package com.aicodeeditor.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌：统一的间距与圆角规范，供各界面复用，避免散落的魔法数字。
 */
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
    val md = 14.dp
    val lg = 20.dp
    val pill = 999.dp
}

/**
 * 品牌强调色，跨明暗主题保持一致的渐变端点。
 */
object Brand {
    val Indigo = Color(0xFF6366F1)
    val Violet = Color(0xFF8B5CF6)
}

val LocalSpacing = staticCompositionLocalOf { Spacing }

// 深色：近黑中性底色 + 靛蓝强调，营造现代代码工具的氛围。
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2E1065),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF23262D),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

// 浅色：柔白底色 + 同源靛蓝，保持品牌一致性同时干净明亮。
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF059669),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F2F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE7E7EA),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// 收紧字重与字距，让标题更利落、正文更易读。
private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
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
