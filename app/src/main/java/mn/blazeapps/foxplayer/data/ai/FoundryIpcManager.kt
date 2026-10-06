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
import com.microsoft.foundrylocal.api.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    const val DEFAULT_MODEL_ALIAS = "qwen2.5-0.5b"

    fun normalizeModelAlias(alias: String): String {
        val clean = alias.trim()
        return when {
            clean.equals("qwen2.5-0.5b-instruct", ignoreCase = true) -> "qwen2.5-0.5b"
            clean.equals("qwen2.5-1.5b-instruct", ignoreCase = true) -> "qwen2.5-1.5b"
            else -> clean
        }
    }

    fun isSameModel(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val cleanA = a.trim().lowercase()
        val cleanB = b.trim().lowercase()
        if (cleanA == cleanB) return true

        val baseA = cleanA.removeSuffix("-instruct")
        val baseB = cleanB.removeSuffix("-instruct")
        if (baseA == baseB) return true

        val hasQwenA = baseA.contains("qwen")
        val hasQwenB = baseB.contains("qwen")
        val hasPhiA = baseA.contains("phi")
        val hasPhiB = baseB.contains("phi")

        if (hasQwenA && hasQwenB) {
            if (baseA.contains("0.5b") && baseB.contains("0.5b")) return true
            if (baseA.contains("1.5b") && baseB.contains("1.5b")) return true
            if (baseA.contains("coder") && baseB.contains("coder")) return true
        }
        if (hasPhiA && hasPhiB) {
            if (baseA.contains("mini") && baseB.contains("mini")) return true
        }

        return false
    }

    private val mutex = Mutex()
    private var managerInstance: FoundryLocalManager? = null
    private var isConnectedState: Boolean = false

    sealed interface FoundryDownloadState {
        object Idle : FoundryDownloadState
        data class Downloading(val modelAlias: String, val progress: Float) : FoundryDownloadState
        data class Ready(val modelAlias: String) : FoundryDownloadState
        data class Error(val modelAlias: String, val message: String) : FoundryDownloadState
    }

    private val _downloadState = MutableStateFlow<FoundryDownloadState>(FoundryDownloadState.Idle)
    val downloadState: StateFlow<FoundryDownloadState> = _downloadState.asStateFlow()
    private var activeDownloadJob: Job? = null

    data class IpcStatus(
        val isSupportedOs: Boolean,
        val isAppInstalled: Boolean,
        val isConnected: Boolean,
        val models: List<String> = emptyList(),
        val cachedModels: List<String> = emptyList(),
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
            val cached = try { catalog.getCachedModels().map { it.alias } } catch (_: Exception) { emptyList() }
            val latency = System.currentTimeMillis() - start

            val msg = if (mgr.isConnected) {
                if (cached.isNotEmpty()) {
                    "Connected via IPC (${latency}ms) · ${cached.size} model(s) downloaded"
                } else {
                    "Connected via IPC (${latency}ms) · No models downloaded"
                }
            } else {
                "Connected (Service Idle)"
            }

            IpcStatus(
                isSupportedOs = true,
                isAppInstalled = true,
                isConnected = mgr.isConnected,
                models = allModels,
                cachedModels = cached,
                loadedModels = loaded,
                latencyMs = latency,
                message = msg,
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

    suspend fun resolveCatalogModel(
        catalog: com.microsoft.foundrylocal.api.Catalog,
        requestedAlias: String,
    ): Pair<String, Model>? {
        val clean = requestedAlias.trim()
        if (clean.isBlank()) return null

        // 1. Direct getModel attempt
        try {
            val direct = catalog.getModel(clean)
            return Pair(clean, direct)
        } catch (_: Exception) {}

        // 2. Fetch allModels and cachedModels from catalog
        val all = try { catalog.listModels() } catch (_: Exception) { emptyList() }
        val cached = try { catalog.getCachedModels() } catch (_: Exception) { emptyList() }
        val candidates = (cached.map { it.alias } + all.map { it.alias }).distinct()

        // 3. Match candidates using isSameModel
        val matched = candidates.firstOrNull { isSameModel(it, clean) }
        if (matched != null) {
            try {
                val m = catalog.getModel(matched)
                return Pair(matched, m)
            } catch (_: Exception) {}
        }

        // 4. Try normalized alias or with/without -instruct variants
        val variants = listOf(
            normalizeModelAlias(clean),
            if (clean.endsWith("-instruct", ignoreCase = true)) clean.removeSuffix("-instruct") else "$clean-instruct",
            DEFAULT_MODEL_ALIAS,
            "$DEFAULT_MODEL_ALIAS-instruct",
        ).distinct()

        for (v in variants) {
            try {
                val m = catalog.getModel(v)
                return Pair(v, m)
            } catch (_: Exception) {}
        }

        // 5. Fallback: If catalog has any model, use first available
        for (cand in candidates) {
            try {
                val m = catalog.getModel(cand)
                return Pair(cand, m)
            } catch (_: Exception) {}
        }

        return null
    }

    suspend fun isModelCached(context: Context, modelAlias: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSupportedOs() || !isFoundryAppInstalled(context)) return@withContext false
        try {
            val mgr = getManager(context) ?: return@withContext false
            val catalog = mgr.getCatalog()
            val cached = try { catalog.getCachedModels() } catch (_: Exception) { emptyList() }
            if (cached.any { isSameModel(it.alias, modelAlias) }) {
                return@withContext true
            }
            val resolved = resolveCatalogModel(catalog, modelAlias)
            resolved?.second?.isCached() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isModelCached check failed for $modelAlias: ${e.message}")
            false
        }
    }

    suspend fun getCachedModels(context: Context): List<String> = withContext(Dispatchers.IO) {
        if (!isSupportedOs() || !isFoundryAppInstalled(context)) return@withContext emptyList()
        try {
            val mgr = getManager(context) ?: return@withContext emptyList()
            val catalog = mgr.getCatalog()
            catalog.getCachedModels().map { it.alias }
        } catch (e: Exception) {
            Log.w(TAG, "getCachedModels failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getModelSizeBytes(context: Context, modelAlias: String): Long = withContext(Dispatchers.IO) {
        if (!isSupportedOs() || !isFoundryAppInstalled(context)) return@withContext 0L
        try {
            val mgr = getManager(context) ?: return@withContext 0L
            val catalog = mgr.getCatalog()
            val resolved = resolveCatalogModel(catalog, modelAlias)
            val info: ModelInfo? = try {
                resolved?.second?.info
                    ?: resolved?.first?.let { catalog.getModelInfo(it) }
                    ?: catalog.getModelInfo(modelAlias)
            } catch (_: Exception) { null }

            if (info != null && info.fileSizeMb > 0) {
                info.fileSizeMb * 1024 * 1024
            } else {
                when {
                    modelAlias.contains("0.5b", ignoreCase = true) -> 822L * 1024 * 1024
                    modelAlias.contains("1.5b", ignoreCase = true) -> 900L * 1024 * 1024
                    modelAlias.contains("phi", ignoreCase = true) -> 1800L * 1024 * 1024
                    else -> 500L * 1024 * 1024
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun startDownload(context: Context, modelAlias: String, forceRedownload: Boolean = false): Job {
        cancelDownload()
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            downloadModel(context, modelAlias, forceRedownload = forceRedownload)
        }
        activeDownloadJob = job
        return job
    }

    suspend fun downloadModel(
        context: Context,
        modelAlias: String,
        forceRedownload: Boolean = false,
        progress: ((Float) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isSupportedOs()) {
            val err = "Requires Android 13+ (API 33)."
            _downloadState.value = FoundryDownloadState.Error(modelAlias, err)
            return@withContext Result.failure(IllegalStateException(err))
        }
        if (!isFoundryAppInstalled(context)) {
            val err = "Foundry Local app is not installed."
            _downloadState.value = FoundryDownloadState.Error(modelAlias, err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val mgr = getManager(context)
            ?: run {
                val err = "Failed to connect to Foundry Local service. Open Foundry Local app."
                _downloadState.value = FoundryDownloadState.Error(modelAlias, err)
                return@withContext Result.failure(IllegalStateException(err))
            }

        try {
            val catalog = mgr.getCatalog()
            val allModels = try { catalog.listModels() } catch (_: Exception) { emptyList() }
            val cachedModels = try { catalog.getCachedModels() } catch (_: Exception) { emptyList() }

            val resolved = resolveCatalogModel(catalog, modelAlias)
                ?: run {
                    val availableList = allModels.map { it.alias }.ifEmpty { cachedModels.map { it.alias } }
                    val availStr = if (availableList.isNotEmpty()) " Available: ${availableList.joinToString(", ")}" else ""
                    val err = "Model '$modelAlias' not found in Foundry Local catalog.$availStr"
                    _downloadState.value = FoundryDownloadState.Error(modelAlias, err)
                    return@withContext Result.failure(IllegalStateException(err))
                }

            val targetAlias = resolved.first
            val model = resolved.second

            val isCached = try { model.isCached() } catch (_: Exception) { false } ||
                cachedModels.any { isSameModel(it.alias, targetAlias) }

            if (isCached && !forceRedownload) {
                Log.i(TAG, "Model '$targetAlias' is already cached on device.")
                try {
                    if (!model.isLoaded()) {
                        Log.i(TAG, "Preloading cached model '$targetAlias'...")
                        model.load()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Preload notice: ${e.message}")
                }
                _downloadState.value = FoundryDownloadState.Ready(modelAlias)
                progress?.invoke(1f)
                return@withContext Result.success(Unit)
            }

            if (isCached && forceRedownload) {
                Log.i(TAG, "Force re-download requested. Removing model '$targetAlias' from cache...")
                try {
                    model.removeFromCache()
                } catch (e: Exception) {
                    Log.w(TAG, "removeFromCache note: ${e.message}")
                }
            }

            _downloadState.value = FoundryDownloadState.Downloading(modelAlias, 0f)
            progress?.invoke(0f)
            Log.i(TAG, "Starting download for model '$targetAlias' (requested: '$modelAlias') via Foundry Local service...")

            model.download(progress = { p ->
                val normalized = when {
                    p > 1f -> (p / 100f).coerceIn(0f, 1f)
                    p < 0f -> 0f
                    else -> p.coerceIn(0f, 1f)
                }
                _downloadState.value = FoundryDownloadState.Downloading(modelAlias, normalized)
                progress?.invoke(normalized)
            })

            try {
                if (!model.isLoaded()) {
                    Log.i(TAG, "Preloading downloaded model '$targetAlias'...")
                    model.load()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preload notice: ${e.message}")
            }

            Log.i(TAG, "Model '$targetAlias' download completed successfully.")
            _downloadState.value = FoundryDownloadState.Ready(modelAlias)
            Result.success(Unit)
        } catch (e: CancellationException) {
            Log.i(TAG, "Download of model '$modelAlias' cancelled.")
            _downloadState.value = FoundryDownloadState.Idle
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model '$modelAlias'", e)
            val msg = e.message ?: "Download failed"
            _downloadState.value = FoundryDownloadState.Error(modelAlias, msg)
            Result.failure(e)
        }
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _downloadState.value = FoundryDownloadState.Idle
    }

    suspend fun deleteModel(context: Context, modelAlias: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSupportedOs() || !isFoundryAppInstalled(context)) return@withContext false
        try {
            val mgr = getManager(context) ?: return@withContext false
            val catalog = mgr.getCatalog()
            val resolved = resolveCatalogModel(catalog, modelAlias)
            val model = resolved?.second ?: try { catalog.getModel(modelAlias) } catch (_: Exception) { null }
            if (model != null) {
                model.removeFromCache()
                if (_downloadState.value is FoundryDownloadState.Ready &&
                    isSameModel((_downloadState.value as FoundryDownloadState.Ready).modelAlias, modelAlias)
                ) {
                    _downloadState.value = FoundryDownloadState.Idle
                }
                Log.i(TAG, "Model '$modelAlias' (${resolved?.first}) removed from Foundry Local cache.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model '$modelAlias' from cache", e)
            false
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

            val resolved = preferredModel?.let { resolveCatalogModel(catalog, it) }
                ?: cachedModels.firstOrNull()?.let { Pair(it.alias, catalog.getModel(it.alias)) }
                ?: loadedModels.firstOrNull()?.let { Pair(it.alias, catalog.getModel(it.alias)) }
                ?: allModels.firstOrNull()?.let { Pair(it.alias, catalog.getModel(it.alias)) }
                ?: resolveCatalogModel(catalog, DEFAULT_MODEL_ALIAS)

            if (resolved == null) {
                return@withContext ChatResult(
                    text = "",
                    modelUsed = preferredModel.orEmpty(),
                    isSuccess = false,
                    errorMessage = "No compatible Foundry Local model found. Please download a model in Settings.",
                )
            }

            val targetAlias = resolved.first
            val model = resolved.second

            val isAlreadyLoaded = loadedModels.any { isSameModel(it.alias, targetAlias) } ||
                try { model.isLoaded() } catch (_: Exception) { false }

            if (!isAlreadyLoaded) {
                val isCached = cachedModels.any { isSameModel(it.alias, targetAlias) } ||
                    try { model.isCached() } catch (_: Exception) { false } ||
                    isModelCached(context, targetAlias)

                if (!isCached) {
                    return@withContext ChatResult(
                        text = "",
                        modelUsed = targetAlias,
                        isSuccess = false,
                        errorMessage = "Model '$targetAlias' is not downloaded yet. Please tap 'Download Model' under Microsoft Foundry Local in Settings.",
                    )
                }

                try {
                    Log.i(TAG, "Loading model '$targetAlias' into Foundry Local...")
                    model.load()
                } catch (e: Exception) {
                    Log.w(TAG, "Model load note: ${e.message}")
                    if (e.message?.contains("download", ignoreCase = true) == true ||
                        e.message?.contains("not found", ignoreCase = true) == true
                    ) {
                        return@withContext ChatResult(
                            text = "",
                            modelUsed = targetAlias,
                            isSuccess = false,
                            errorMessage = "Model '$targetAlias' is not downloaded yet. Please tap 'Download Model' under Microsoft Foundry Local in Settings. (${e.message})",
                        )
                    }
                }
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
