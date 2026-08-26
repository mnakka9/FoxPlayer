package mn.blazeapps.foxplayer.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("foxplayer_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _mode.value = mode
    }

    private fun readMode(): ThemeMode {
        return when (prefs.getString(KEY_THEME, ThemeMode.LIGHT.name)) {
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
    }
}
