package mn.blazeapps.foxplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ColorBlueVioletLight,
    onPrimary = Color.White,
    primaryContainer = ColorBlueVioletDim,
    onPrimaryContainer = Color.White,
    secondary = ColorOrange,
    onSecondary = Color.White,
    secondaryContainer = ColorOrangeDim,
    onSecondaryContainer = ColorOrangeLight,
    tertiary = ColorPurple,
    onTertiary = Color.White,
    tertiaryContainer = ColorPurpleDim,
    onTertiaryContainer = ColorBlueVioletSubtle,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgMid,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF161F45),
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = BgDeep,
    surfaceContainerLow = BgMid,
    surfaceContainer = Color(0xFF101736),
    surfaceContainerHigh = Color(0xFF161F45),
    surfaceContainerHighest = Color(0xFF1E2958),
    outline = GlassBorder,
    outlineVariant = GlassBorderSubtle,
    error = ColorRed,
    onError = Color.White,
    errorContainer = ColorRedDim,
    onErrorContainer = ColorRedLight,
)

private val LightColors = lightColorScheme(
    primary = ColorBlueViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF3730A3),
    secondary = ColorOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF9A3412),
    tertiary = ColorPurple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF5B21B6),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = ColorRed,
    onError = Color.White,
)

@Composable
fun FoxPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }
    val colorScheme = if (isDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FoxTypography,
        shapes = FoxShapes,
        content = content,
    )
}
