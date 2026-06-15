package com.aicodeeditor.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF007ACC),
    primaryContainer = Color(0xFF264F78),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFFFF7043),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF252526),
    surfaceVariant = Color(0xFF3C3C3C),
    onBackground = Color(0xFFD4D4D4),
    onSurface = Color(0xFFD4D4D4)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007ACC),
    primaryContainer = Color(0xFFADD6FF),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFFFF7043),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F3F3),
    surfaceVariant = Color(0xFFE0E0E0),
    onBackground = Color(0xFF333333),
    onSurface = Color(0xFF333333)
)

@Composable
fun AIEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
