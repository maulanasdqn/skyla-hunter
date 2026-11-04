package dev.reforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Minimalist dark-first palette — matches the device theme (near-black + soft blue accent).
val Accent = Color(0xFF5B8CFF)
val AccentDim = Color(0xFF3A5FB0)
val BgDark = Color(0xFF0B0E13)
val SurfaceDark = Color(0xFF11151C)
val SurfaceDark2 = Color(0xFF171C25)
val OnDark = Color(0xFFE6EAF2)
val MutedDark = Color(0xFF8A93A6)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B0E13),
    secondary = AccentDim,
    background = BgDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = MutedDark,
    outline = Color(0xFF2A313D),
)

private val LightColors = lightColorScheme(
    primary = AccentDim,
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun ReforgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ReforgeTypography,
        content = content,
    )
}
