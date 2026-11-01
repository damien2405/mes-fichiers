package com.damien.mesfichiers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3E91FF),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFE6E6EA),
    onSurfaceVariant = Color(0xFF6E6E73),
    secondaryContainer = Color(0xFFEBEBEF),
    onSecondaryContainer = Color(0xFF1B1B1D),
    outlineVariant = Color(0xFFE0E0E4),
    error = Color(0xFFD93025)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BA0FF),
    onPrimary = Color(0xFF00111F),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF2C2C2F),
    onSurfaceVariant = Color(0xFFA0A0A6),
    secondaryContainer = Color(0xFF2A2A2D),
    onSecondaryContainer = Color(0xFFEDEDF0),
    outlineVariant = Color(0xFF38383B),
    error = Color(0xFFFF6B60)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
