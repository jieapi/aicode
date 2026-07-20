package com.aicode.feature.git.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Spacing
import com.aicode.core.util.LineDiff
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.File
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * diff 视图所需的数据。由 [com.aicode.feature.git.presentation.GitViewModel] 在后台线程
 * 用 [LineDiff] 计算 + Highlights 着色后填充，UI 直接渲染。
 */
data class DiffData(
    val filePath: String,
    val oldRef: String,
    val newRef: String,
    val lines: List<DiffRow>,
    val addedCount: Int,
    val removedCount: Int,
    val isBinary: Boolean = false,
    val isLarge: Boolean = false
)

/**
 * 一行 diff 的完整渲染数据：类型 + 文本 + 高亮 + 旧/新行号。
 * 行号在 ViewModel 计算 diff 时一并算好，避免 LazyColumn 懒加载下 UI 层维护可变计数。
 */
data class DiffRow(
    val type: LineDiff.LineType,
    val text: String,
    val highlighted: AnnotatedString?,
    val oldLineNum: Int?,
    val newLineNum: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diffData: DiffData,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = diffData.filePath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${diffData.oldRef} → ${diffData.newRef}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            diffData.isBinary -> BinaryHint(padding)
            diffData.isLarge -> LargeFileHint(diffData, padding)
            diffData.lines.isEmpty() -> EmptyDiff(padding)
            else -> DiffContent(diffData, padding)
        }
    }
}

@Composable
private fun DiffContent(diffData: DiffData, padding: androidx.compose.foundation.layout.PaddingValues) {
    val maxLineNum = diffData.lines.maxOfOrNull {
        maxOf(it.oldLineNum ?: 0, it.newLineNum ?: 0)
    } ?: 0
    val gutterWidth = remember(maxLineNum) {
        val chars = maxOf(3, maxLineNum.toString().length)
        (chars * 8 + 8).dp
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            DiffStatsBar(diffData.addedCount, diffData.removedCount)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                diffData.lines.forEach { row ->
                    DiffLineRow(row, gutterWidth)
                }
            }
        }
    }
}

@Composable
private fun DiffStatsBar(added: Int, removed: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+$added",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "-$removed",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DiffLineRow(row: DiffRow, gutterWidth: androidx.compose.ui.unit.Dp) {
    val (bgColor, prefix, prefixColor) = when (row.type) {
        LineDiff.LineType.ADD -> Triple(Color(0x3322C55E), "+", Color(0xFF22C55E))
        LineDiff.LineType.REMOVE -> Triple(Color(0x33EF4444), "-", Color(0xFFEF4444))
        LineDiff.LineType.CONTEXT -> Triple(Color.Transparent, " ", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val gutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val gutterTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bgColor),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = row.oldLineNum?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = gutterTextColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            softWrap = false,
            modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
                .background(gutterBg)
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Text(
            text = row.newLineNum?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = gutterTextColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            softWrap = false,
            modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
                .background(gutterBg)
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
            maxLines = 1
        )
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = prefixColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = Spacing.xs, top = 2.dp)
                .width(14.dp)
        )
        if (row.highlighted != null) {
            Text(
                text = row.highlighted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier
                    .padding(end = Spacing.lg, top = 2.dp, bottom = 2.dp)
            )
        } else {
            Text(
                text = row.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier
                    .padding(end = Spacing.lg, top = 2.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyDiff(padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text("无差异", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BinaryHint(padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                FeatherIcons.File,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(Spacing.sm))
            Text("二进制文件，不支持文本差异对比", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LargeFileHint(diffData: DiffData, padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "文件过大，已跳过差异计算",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "超过 2000 行的文件不做行级 diff 以避免内存压力。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 对一段文本做语法高亮，返回带 SpanStyle 的 AnnotatedString。
 * language 为 null 时返回 null（调用方降级为纯文本）。
 */
fun highlightCode(code: String, language: SyntaxLanguage?): AnnotatedString? {
    if (language == null || code.isBlank()) return null
    return try {
        val highlights = Highlights.Builder()
            .code(code)
            .language(language)
            .theme(SyntaxThemes.default(darkMode = false))
            .build()
        val codeHighlights = highlights.getHighlights()
        buildAnnotatedString {
            append(code)
            codeHighlights.forEach { h ->
                val style = when (h) {
                    is ColorHighlight -> SpanStyle(color = Color(h.rgb).copy(alpha = 1f))
                    is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
                }
                addStyle(style, h.location.start, h.location.end)
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 按文件扩展名推断语法语言。未识别返回 null（降级纯文本）。
 */
fun inferSyntaxLanguage(filePath: String): SyntaxLanguage? {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "py" -> SyntaxLanguage.PYTHON
        "js", "mjs" -> SyntaxLanguage.JAVASCRIPT
        "ts" -> SyntaxLanguage.TYPESCRIPT
        "go" -> SyntaxLanguage.GO
        "rb" -> SyntaxLanguage.RUBY
        "php" -> SyntaxLanguage.PHP
        "swift" -> SyntaxLanguage.SWIFT
        "rs" -> SyntaxLanguage.RUST
        "c", "h", "cpp", "cc", "cxx", "hpp" -> SyntaxLanguage.CPP
        "cs" -> SyntaxLanguage.CSHARP
        "sh", "bash" -> SyntaxLanguage.SHELL
        "dart" -> SyntaxLanguage.DART
        "pl", "pm" -> SyntaxLanguage.PERL
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        else -> null
    }
}
