package mn.blazeapps.foxplayer.data.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.microsoft.foundrylocal.api.ChatCompletionRequest
import com.microsoft.foundrylocal.api.ChatMessage
import com.microsoft.foundrylocal.api.Configuration
import com.microsoft.foundrylocal.api.FoundryLocalException
import com.microsoft.foundrylocal.api.FoundryLocalManager
import com.microsoft.foundrylocal.api.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages on-device inference via Microsoft Foundry Local Android service app
 * (package: com.microsoft.foundrylocal.app) using Android IPC and the official
 * Foundry Local IPC SDK (com.microsoft.foundrylocal.api.*).
 *
 * Runs models (Qwen, Phi, etc.) 100% locally on device NPU/GPU/CPU in a separate,
 * sandboxed process so inference never bloats FoxPlayer APK or consumes its process memory.
 */
object FoundryIpcManager {
    private const val TAG = "FoxPlayer-FoundryIPC"
    const val FOUNDRY_APP_PACKAGE = "com.microsoft.foundrylocal.app"
    const val PLAY_STORE_MARKET_URI = "market://details?id=com.microsoft.foundrylocal.app"

    private val mutex = Mutex()
    private var managerInstance: FoundryLocalManager? = null
    private var isConnectedState: Boolean = false

    data class IpcStatus(
        val isSupportedOs: Boolean,
        val isAppInstalled: Boolean,
        val isConnected: Boolean,
        val models: List<String> = emptyList(),
        val loadedModels: List<String> = emptyList(),
        val latencyMs: Long = 0,
        val message: String = "",
    )

    data class ChatResult(
        val text: String,
        val modelUsed: String,
        val isSuccess: Boolean,
        val errorMessage: String? = null,
    )

    fun isSupportedOs(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun isFoundryAppInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(FOUNDRY_APP_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(FOUNDRY_APP_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createPlayStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_MARKET_URI)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createLaunchAppIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(FOUNDRY_APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun getManager(context: Context): FoundryLocalManager? = mutex.withLock {
        if (!isSupportedOs() || !isFoundryAppInstalled(context)) {
            return null
        }

        val existing = managerInstance
        if (existing != null && existing.isConnected) {
            return existing
        }

        try {
            val config = Configuration(
                appName = "FoxPlayer",
                disableTelemetry = true,
            )
            val mgr = FoundryLocalManager.create(
                context = context.applicationContext,
                config = config,
                onDisconnected = {
                    Log.w(TAG, "Foundry Local service disconnected")
                    isConnectedState = false
                },
            )
            managerInstance = mgr
            isConnectedState = mgr.isConnected
            mgr
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FoundryLocalManager", e)
            null
        }
    }

    suspend fun checkStatus(context: Context): IpcStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!isSupportedOs()) {
            return@withContext IpcStatus(
                isSupportedOs = false,
                isAppInstalled = false,
                isConnected = false,
                message = "Requires Android 13+ (API 33).",
            )
        }

        val appInstalled = isFoundryAppInstalled(context)
        if (!appInstalled) {
            return@withContext IpcStatus(
                isSupportedOs = true,
                isAppInstalled = false,
                isConnected = false,
                message = "Foundry Local app is not installed.",
            )
        }

        try {
            val mgr = getManager(context)
            if (mgr == null) {
                return@withContext IpcStatus(
                    isSupportedOs = true,
                    isAppInstalled = true,
                    isConnected = false,
                    latencyMs = System.currentTimeMillis() - start,
                    message = "Service not running. Open Foundry Local app.",
                )
            }

            val catalog = mgr.getCatalog()
            val allModels = try { catalog.listModels().map { it.alias } } catch (_: Exception) { emptyList() }
            val loaded = try { catalog.getLoadedModels().map { it.alias } } catch (_: Exception) { emptyList() }
            val latency = System.currentTimeMillis() - start

            IpcStatus(
                isSupportedOs = true,
                isAppInstalled = true,
                isConnected = mgr.isConnected,
                models = allModels,
                loadedModels = loaded,
                latencyMs = latency,
                message = if (mgr.isConnected) "Connected via IPC (${latency}ms)" else "Connected (Service Idle)",
            )
        } catch (e: Exception) {
            IpcStatus(
                isSupportedOs = true,
                isAppInstalled = true,
                isConnected = false,
                latencyMs = System.currentTimeMillis() - start,
                message = "IPC error: ${e.message?.take(50) ?: "Offline"}",
            )
        }
    }

    suspend fun chat(
        context: Context,
        query: String,
        bookTitle: String? = null,
        bookAuthor: String? = null,
        preferredModel: String? = null,
        liveKnowledgeContext: String? = null,
    ): ChatResult = withContext(Dispatchers.IO) {
        if (!isSupportedOs()) {
            return@withContext ChatResult(
                text = "",
                modelUsed = "",
                isSuccess = false,
                errorMessage = "Requires Android 13+ (API 33).",
            )
        }

        if (!isFoundryAppInstalled(context)) {
            return@withContext ChatResult(
                text = "",
                modelUsed = "",
                isSuccess = false,
                errorMessage = "Foundry Local service app ($FOUNDRY_APP_PACKAGE) is not installed.",
            )
        }

        val mgr = getManager(context)
            ?: return@withContext ChatResult(
                text = "",
                modelUsed = "",
                isSuccess = false,
                errorMessage = "Failed to connect to Foundry Local IPC service.",
            )

        try {
            val catalog = mgr.getCatalog()
            val loadedModels = try { catalog.getLoadedModels() } catch (_: Exception) { emptyList() }
            val cachedModels = try { catalog.getCachedModels() } catch (_: Exception) { emptyList() }
            val allModels = try { catalog.listModels() } catch (_: Exception) { emptyList() }

            // Determine best model to use
            val targetAlias = when {
                !preferredModel.isNullOrBlank() && (allModels.any { it.alias.equals(preferredModel, ignoreCase = true) } || loadedModels.any { it.alias.equals(preferredModel, ignoreCase = true) }) -> {
                    allModels.firstOrNull { it.alias.equals(preferredModel, ignoreCase = true) }?.alias
                        ?: preferredModel
                }
                loadedModels.isNotEmpty() -> loadedModels.first().alias
                cachedModels.isNotEmpty() -> cachedModels.first().alias
                allModels.isNotEmpty() -> allModels.first().alias
                else -> preferredModel ?: "qwen2.5-0.5b-instruct"
            }

            val model: Model = catalog.getModel(targetAlias)
            if (!model.isLoaded()) {
                if (!model.isCached()) {
                    return@withContext ChatResult(
                        text = "",
                        modelUsed = targetAlias,
                        isSuccess = false,
                        errorMessage = "Model '$targetAlias' is not downloaded yet. Please download it inside the Foundry Local app.",
                    )
                }
                model.load()
            }

            val chatClient = model.createChatClient()

            val systemPrompt = buildString {
                append("You are FoxPlayer's intelligent on-device AI assistant powered by Microsoft Foundry Local.")
                if (!bookTitle.isNullOrBlank()) {
                    append(" The user is listening to '$bookTitle'")
                    if (!bookAuthor.isNullOrBlank()) append(" by $bookAuthor")
                    append(".")
                }
                if (!liveKnowledgeContext.isNullOrBlank()) {
                    append("\n\nLive Search & Reference Information:\n")
                    append(liveKnowledgeContext)
                    append("\n\nSynthesize the above facts into a clear, engaging, and well-structured answer.")
                } else {
                    append(" Provide a direct, helpful, and concise response.")
                }
            }

            val request = ChatCompletionRequest(
                messages = listOf(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user(query),
                ),
                temperature = 0.7f,
                maxTokens = 1024,
            )

            val completion = chatClient.completeChat(request)
            val answer = completion.message?.content.orEmpty()

            ChatResult(
                text = answer,
                modelUsed = targetAlias,
                isSuccess = answer.isNotBlank(),
                errorMessage = if (answer.isBlank()) "Empty response returned by Foundry Local model." else null,
            )
        } catch (e: FoundryLocalException) {
            Log.e(TAG, "FoundryLocalException during chat inference", e)
            ChatResult(
                text = "",
                modelUsed = preferredModel.orEmpty(),
                isSuccess = false,
                errorMessage = "Foundry Local error (${e.errorCode}): ${e.message}",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Foundry Local inference", e)
            ChatResult(
                text = "",
                modelUsed = preferredModel.orEmpty(),
                isSuccess = false,
                errorMessage = e.message ?: "Inference failed",
            )
        }
    }
}
