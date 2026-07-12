package com.aicode.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 10.dp
    val lg = 14.dp
    val pill = 999.dp
}

object Brand {
    val Blue = Color(0xFF2563EB)
    val Sky = Color(0xFF38BDF8)
    val Ice = Color(0xFFEFF6FF)
    val IconGray = Color(0xFF424242)
    val PageBg = Color(0xFFFAFAFA)
}

val LocalSpacing = staticCompositionLocalOf { Spacing }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0F3A63),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    tertiary = Color(0xFF22C55E),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF0D1B2E),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF13273F),
    onSurfaceVariant = Color(0xFFB8C7DA),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF44617F),
    outlineVariant = Color(0xFF223B57),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0B3B76),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF16A34A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAF4FF),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color.White,
    outline = Color(0xFFBBD7F2),
    outlineVariant = Color(0xFFDCEBFA),
    error = Color(0xFFDC2626),

    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    )
}

@Composable
fun AIEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
