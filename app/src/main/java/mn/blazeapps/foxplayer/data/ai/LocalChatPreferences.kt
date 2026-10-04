package mn.blazeapps.foxplayer.data.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalChatEngineMode(val displayName: String, val subtitle: String) {
    FAST_LOCAL(
        displayName = "Fast Local (Web & On-Device Extractor)",
        subtitle = "Instant Wikipedia, DuckDuckGo & Brave Search with on-device ONNX extraction. Zero server setup.",
    ),
    FOUNDRY_LOCAL(
        displayName = "Microsoft Foundry Local (On-Device LLM)",
        subtitle = "On-device LLM (Qwen/Phi) via Microsoft Foundry Local server with agentic search. 100% private.",
    );

    companion object {
        fun fromString(value: String?): LocalChatEngineMode {
            return when (value?.uppercase()) {
                "FOUNDRY_LOCAL" -> FOUNDRY_LOCAL
                else -> FAST_LOCAL
            }
        }
    }
}

class LocalChatPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _engineMode = MutableStateFlow(readEngineMode())
    val engineMode: StateFlow<LocalChatEngineMode> = _engineMode.asStateFlow()

    private val _foundryEndpoint = MutableStateFlow(readFoundryEndpoint())
    val foundryEndpoint: StateFlow<String> = _foundryEndpoint.asStateFlow()

    private val _foundryModel = MutableStateFlow(readFoundryModel())
    val foundryModel: StateFlow<String> = _foundryModel.asStateFlow()

    fun setEngineMode(mode: LocalChatEngineMode) {
        prefs.edit().putString(KEY_ENGINE_MODE, mode.name).apply()
        _engineMode.value = mode
    }

    fun setFoundryEndpoint(endpoint: String) {
        val clean = endpoint.trim().ifBlank { DEFAULT_ENDPOINT }
        prefs.edit().putString(KEY_FOUNDRY_ENDPOINT, clean).apply()
        _foundryEndpoint.value = clean
    }

    fun setFoundryModel(model: String) {
        val clean = model.trim().ifBlank { DEFAULT_MODEL }
        prefs.edit().putString(KEY_FOUNDRY_MODEL, clean).apply()
        _foundryModel.value = clean
    }

    private fun readEngineMode(): LocalChatEngineMode {
        val str = prefs.getString(KEY_ENGINE_MODE, LocalChatEngineMode.FAST_LOCAL.name)
        return LocalChatEngineMode.fromString(str)
    }

    private fun readFoundryEndpoint(): String {
        return prefs.getString(KEY_FOUNDRY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    }

    private fun readFoundryModel(): String {
        return prefs.getString(KEY_FOUNDRY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    companion object {
        private const val PREFS_NAME = "foxplayer_prefs"
        private const val KEY_ENGINE_MODE = "local_chat_engine_mode"
        private const val KEY_FOUNDRY_ENDPOINT = "foundry_local_endpoint"
        private const val KEY_FOUNDRY_MODEL = "foundry_local_model"

        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8080/v1"
        const val DEFAULT_MODEL = "qwen2.5-0.5b-instruct"
    }
}
