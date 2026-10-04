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

    fun setChatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHAT_ENABLED, enabled).apply()
        _isChatEnabled.value = enabled
    }

    fun setCustomApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_API_KEY, clean).apply()
        _customApiKey.value = clean
    }

    fun getEffectiveApiKey(): String {
        val custom = _customApiKey.value.trim()
        if (custom.isNotBlank()) return custom
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun isApiKeyConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    private fun readChatEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHAT_ENABLED, false)
    }

    private fun readCustomApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    companion object {
        private const val PREFS_NAME = "foxplayer_prefs"
        private const val KEY_CHAT_ENABLED = "gemini_chat_enabled"
        private const val KEY_API_KEY = "gemini_api_key"
    }
}
