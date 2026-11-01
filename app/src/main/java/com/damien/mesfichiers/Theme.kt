package com.damien.mesfichiers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B7BFF),
    onPrimary = Color.White,
    background = Color(0xFFF3F4F8),
    onBackground = Color(0xFF16171B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16171B),
    surfaceVariant = Color(0xFFE8EAF1),
    onSurfaceVariant = Color(0xFF6B7080),
    secondaryContainer = Color(0xFFE9ECF4),
    onSecondaryContainer = Color(0xFF16171B),
    outlineVariant = Color(0xFFE4E6ED),
    error = Color(0xFFD93025)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C9BFF),
    onPrimary = Color(0xFF00112B),
    background = Color(0xFF0B0C10),
    onBackground = Color(0xFFEDEEF2),
    surface = Color(0xFF17181D),
    onSurface = Color(0xFFEDEEF2),
    surfaceVariant = Color(0xFF262830),
    onSurfaceVariant = Color(0xFF9EA2B0),
    secondaryContainer = Color(0xFF22242B),
    onSecondaryContainer = Color(0xFFEDEEF2),
    outlineVariant = Color(0xFF2B2D35),
    error = Color(0xFFFF6B60)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
