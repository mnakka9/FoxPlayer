package mn.blazeapps.foxplayer.data.gemini

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mn.blazeapps.foxplayer.BuildConfig

class GeminiPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isChatEnabled = MutableStateFlow(readChatEnabled())
    val isChatEnabled: StateFlow<Boolean> = _isChatEnabled.asStateFlow()

    private val _customApiKey = MutableStateFlow(readCustomApiKey())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(readSelectedModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isApiKeySession = MutableStateFlow(readApiKeySession())
    val isApiKeySession: StateFlow<Boolean> = _isApiKeySession.asStateFlow()

    fun setChatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHAT_ENABLED, enabled).apply()
        _isChatEnabled.value = enabled
    }

    fun setCustomApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_API_KEY, clean).apply()
        _customApiKey.value = clean
    }

    fun setSelectedModel(modelId: String) {
        val clean = modelId.trim()
        if (clean.isNotBlank()) {
            prefs.edit().putString(KEY_SELECTED_MODEL, clean).apply()
            _selectedModel.value = clean
        }
    }

    fun setApiKeySessionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_API_KEY_SESSION, active).apply()
        _isApiKeySession.value = active
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).putBoolean(KEY_API_KEY_SESSION, false).apply()
        _customApiKey.value = ""
        _isApiKeySession.value = false
    }

    fun getEffectiveApiKey(): String {
        val custom = _customApiKey.value.trim()
        if (custom.isNotBlank()) return custom
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun isApiKeyConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    fun getMaskedApiKey(): String {
        val key = getEffectiveApiKey()
        if (key.length <= 10) return "••••••••"
        return "${key.take(6)}...${key.takeLast(4)}"
    }

    private fun readChatEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHAT_ENABLED, false)
    }

    private fun readCustomApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    private fun readSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    private fun readApiKeySession(): Boolean {
        return prefs.getBoolean(KEY_API_KEY_SESSION, false)
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-1.5-flash"
        private const val PREFS_NAME = "foxplayer_prefs"
        private const val KEY_CHAT_ENABLED = "gemini_chat_enabled"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_MODEL = "gemini_selected_model"
        private const val KEY_API_KEY_SESSION = "gemini_api_key_session"
    }
}
