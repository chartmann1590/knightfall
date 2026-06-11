package com.chartmann.knightfall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Charcoal,
    primaryContainer = GoldDeep,
    onPrimaryContainer = Cream,
    secondary = Cream,
    onSecondary = Charcoal,
    background = Charcoal,
    onBackground = Cream,
    surface = CharcoalSurface,
    onSurface = Cream,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = Smoke,
    error = LossRed,
    outline = Smoke,
)

private val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Cream,
    primaryContainer = Gold,
    onPrimaryContainer = Charcoal,
    secondary = Charcoal,
    onSecondary = Cream,
    background = Cream,
    onBackground = Charcoal,
    surface = Color(0xFFFBF5E9),
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFEDE2CC),
    onSurfaceVariant = Color(0xFF6B6155),
    error = Color(0xFFB3261E),
    outline = Color(0xFF85796A),
)

@Composable
fun KnightfallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KnightfallTypography,
        content = content,
    )
}
