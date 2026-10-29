package com.vibez.terminal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.vibez.terminal.data.UiThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EE787),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF1B4D2E),
    onPrimaryContainer = Color(0xFFB4F7C0),
    secondary = Color(0xFF79C0FF),
    onSecondary = Color(0xFF002F55),
    secondaryContainer = Color(0xFF1C3B57),
    onSecondaryContainer = Color(0xFFCDE5FF),
    tertiary = Color(0xFFD2A8FF),
    onTertiary = Color(0xFF3B1366),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF5C0A07),
    errorContainer = Color(0xFF7A1F1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFAAB4BF),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF141A20),
    surfaceContainer = Color(0xFF181F26),
    surfaceContainerHigh = Color(0xFF1F272F),
    surfaceContainerHighest = Color(0xFF283039),
    outline = Color(0xFF59636E),
    outlineVariant = Color(0xFF30363D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A7F37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1C2),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF0969DA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E4FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFF8250DF),
    error = Color(0xFFCF222E),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE6EAEF),
    onSurfaceVariant = Color(0xFF59636E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F6),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE3E8ED),
    surfaceContainerHighest = Color(0xFFDCE2E8),
    outline = Color(0xFF8C959F),
    outlineVariant = Color(0xFFD0D7DE),
)

/** Monospace text, used for commands, addresses and keys. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/** Colors for [com.vibez.terminal.data.HostColors]. */
fun hostColor(name: String): Color? = when (name) {
    "red" -> Color(0xFFF85149)
    "orange" -> Color(0xFFF0883E)
    "yellow" -> Color(0xFFE3B341)
    "green" -> Color(0xFF3FB950)
    "teal" -> Color(0xFF39C5CF)
    "blue" -> Color(0xFF58A6FF)
    "purple" -> Color(0xFFBC8CFF)
    "pink" -> Color(0xFFFF7EB6)
    else -> null
}

@Composable
fun VibezTheme(mode: UiThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        UiThemeMode.SYSTEM -> isSystemInDarkTheme()
        UiThemeMode.DARK -> true
        UiThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
