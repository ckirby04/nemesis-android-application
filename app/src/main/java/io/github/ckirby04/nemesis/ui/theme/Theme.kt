package io.github.ckirby04.nemesis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Felt,
    onPrimary = Ink,
    primaryContainer = FeltDeep,
    onPrimaryContainer = FeltLight,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3D3016),
    onSecondaryContainer = Gold,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = InkHigh,
    onSurfaceVariant = ChalkMuted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF29323A),
    outline = InkOutline,
    outlineVariant = Color(0xFF263038),
)

private val LightColors = lightColorScheme(
    primary = FeltDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFDD),
    onPrimaryContainer = Color(0xFF0C3B27),
    secondary = GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E3B4),
    onSecondaryContainer = Color(0xFF3D2C05),
    background = Paper,
    onBackground = Graphite,
    surface = Paper,
    onSurface = Graphite,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = GraphiteMuted,
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = Color(0xFFE4E3DB),
    outline = PaperOutline,
    outlineVariant = Color(0xFFDDDDD6),
)

/** App theme. No dynamic color: the heatmap's red/green semantics need a fixed palette around them. */
@Composable
fun NemesisTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
