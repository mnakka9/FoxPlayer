package mn.blazeapps.foxplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DarkColors = darkColorScheme(
    primary = NativeVioletLight,
    onPrimary = Color.White,
    primaryContainer = PillBgDark,
    onPrimaryContainer = NativeVioletLight,
    secondary = ColorOrangeLight,
    onSecondary = Color.White,
    secondaryContainer = ColorOrangeDim,
    onSecondaryContainer = ColorOrangeLight,
    tertiary = ColorPurple,
    onTertiary = Color.White,
    tertiaryContainer = ColorPurpleDim,
    onTertiaryContainer = ColorBlueVioletSubtle,
    background = BgDeep,
    onBackground = TextLightPrimary,
    surface = CardDark,
    onSurface = TextLightPrimary,
    surfaceVariant = PillBgDark,
    onSurfaceVariant = TextLightSecondary,
    surfaceContainerLowest = BgDeep,
    surfaceContainerLow = BgMid,
    surfaceContainer = Color(0xFF101736),
    surfaceContainerHigh = Color(0xFF161F45),
    surfaceContainerHighest = Color(0xFF1E2958),
    outline = BorderDark,
    outlineVariant = GlassBorderSubtle,
    error = ColorRed,
    onError = Color.White,
    errorContainer = ColorRedDim,
    onErrorContainer = ColorRedLight,
)

private val LightColors = lightColorScheme(
    primary = NativeViolet,
    onPrimary = Color.White,
    primaryContainer = PillBgLight,
    onPrimaryContainer = NativeViolet,
    secondary = ColorOrange,
    onSecondary = Color.White,
    secondaryContainer = CatAmberBg,
    onSecondaryContainer = CatAmberIcon,
    tertiary = CatRoseIcon,
    onTertiary = Color.White,
    tertiaryContainer = CatRoseBg,
    onTertiaryContainer = CatRoseIcon,
    background = PastelBgBottom,
    onBackground = TextDarkPrimary,
    surface = CardWhite,
    onSurface = TextDarkPrimary,
    surfaceVariant = PillBgLight,
    onSurfaceVariant = TextDarkSecondary,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = PastelBgBottom,
    surfaceContainer = PillBgLight,
    surfaceContainerHigh = BorderLight,
    surfaceContainerHighest = BorderLightSubtle,
    outline = BorderLight,
    outlineVariant = BorderLightSubtle,
    error = ColorRed,
    onError = Color.White,
    errorContainer = CatRoseBg,
    onErrorContainer = ColorRed,
)

@Composable
fun FoxPlayerTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT, // Defaults to Light mode matching NativeOffice
    content: @Composable () -> Unit,
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }
    val colorScheme = if (isDark) DarkColors else LightColors

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FoxTypography,
            shapes = FoxShapes,
            content = content,
        )
    }
}
