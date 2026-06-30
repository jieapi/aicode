package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextSelectionColors
import androidx.compose.material3.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.hasVisibleContent
import com.aicodeeditor.feature.agent.presentation.MdBlock
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.Star
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

@Composable
fun AgentMessageItem(message: AgentUIMessage, liveOutput: String? = null) {
    val hasReasoning = message.role == MessageRole.ASSISTANT && !message.reasoning.isNullOrEmpty()
    val hasContent = message.content.hasVisibleContent()
    if (message.role == MessageRole.ASSISTANT && !hasContent && !hasReasoning) return

    val isUser = message.role == MessageRole.USER

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (hasReasoning) {
            ReasoningBubble(text = message.reasoning!!, initiallyExpanded = false)
        }
        if (hasContent || message.role != MessageRole.ASSISTANT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    shape = if (isUser) {
                        RoundedCornerShape(Radius.md, Radius.md, Radius.xs, Radius.md)
                    } else {
                        RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs)
                    },
                    color = when (message.role) {
                        MessageRole.USER -> MaterialTheme.colorScheme.primary
                        MessageRole.ASSISTANT -> MaterialTheme.colorScheme.surface
                        MessageRole.TOOL -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (message.role == MessageRole.ASSISTANT) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    } else null,
                    modifier = Modifier.fillMaxWidth(0.88f)
                ) {
                    if (message.role == MessageRole.TOOL) {
                        ToolMessageBody(message, liveOutput = liveOutput)
                    } else {
                        val textColor = when (message.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        SelectionContainer {
                            MarkdownContent(
                                text = message.content.ifEmpty { "…" },
                                color = textColor,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                                preParsedBlocks = message.parsedBlocks.takeIf { it.isNotEmpty() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 使用 multiplatform-markdown-renderer-m3 库渲染 Markdown。
 */
@Composable
internal fun MarkdownContent(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    preParsedBlocks: List<MdBlock>? = null
) {
    val isDark = isSystemInDarkTheme()

    val mdColors = markdownColor(
        text = color,
        codeBackground = if (isDark) Color(0xFF152030) else Color(0xFFE8EDF3),
        inlineCodeBackground = if (isDark) Color(0xFF1C2E44) else Color(0xFFDBEAFE),
        dividerColor = if (isDark) Color(0xFF2A3F56) else Color(0xFFCBD5E1),
        tableBackground = if (isDark) Color(0xFF152030) else Color(0xFFF1F5F9),
    )

    val typography = MaterialTheme.typography
    val mdTypography = markdownTypography(
        h1 = typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color),
        h2 = typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color),
        h3 = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = color),
        h4 = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = color),
        h5 = typography.bodyLarge.copy(fontWeight = FontWeight.Medium, color = color),
        h6 = typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = color),
        paragraph = typography.bodyMedium.copy(color = color, lineHeight = 20.sp),
        code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)),
        inlineCode = TextStyle(fontFamily = FontFamily.Monospace, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)),
        ordered = typography.bodyMedium.copy(color = color, lineHeight = 20.sp),
        bullet = typography.bodyMedium.copy(color = color, lineHeight = 20.sp),
        table = typography.bodySmall.copy(color = color),
    )

    val mdPadding = markdownPadding(
        block = 4.dp,
        list = 2.dp,
        listItemBottom = 1.dp,
        listIndent = 12.dp,
        codeBlock = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    )

    val mdDimens = markdownDimens(
        codeBackgroundCornerSize = 6.dp,
        tableCellPadding = 6.dp,
        tableCornerSize = 6.dp,
    )

    val highlightsBuilder = remember(isDark) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDark))
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides color
    ) {
        Markdown(
            content = text,
            modifier = modifier,
            colors = mdColors,
            typography = mdTypography,
            padding = mdPadding,
            dimens = mdDimens,
            components = markdownComponents(
                codeFence = {
                    MarkdownHighlightedCodeFence(
                        content = it.content,
                        node = it.node,
                        highlightsBuilder = highlightsBuilder,
                        showHeader = true,
                    )
                },
                codeBlock = {
                    MarkdownHighlightedCodeBlock(
                        content = it.content,
                        node = it.node,
                        highlightsBuilder = highlightsBuilder,
                        showHeader = true,
                    )
                },
            ),
        )
    }
}

/** 等待模型返回时的占位气泡：左对齐、与助手气泡同款，内含三个循环跳动的点。 */
@Composable
internal fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 模型流式吐字时的实时气泡：左对齐、与助手气泡同款。
 * 尾部带三个跳动的点表示仍在生成。本轮结束后由落库的助手气泡接管。
 *
 * 性能优化：节流——流式 delta 频率远高于 Markdown AST 解析速度，
 * 用 debounce(80ms) 将渲染更新降到约 12fps，避免每个 delta 都触发
 * 库内部 Loading→Success 状态循环导致的 0 高度闪烁帧。
 */
@OptIn(FlowPreview::class)
@Composable
internal fun StreamingBubble(text: String) {
    var throttledText by remember { mutableStateOf(text) }
    LaunchedEffect(Unit) {
        snapshotFlow { text }
            .debounce(80)
            .collect { throttledText = it }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
                MarkdownContent(
                    text = throttledText,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.sm))
                TypingDots(color = MaterialTheme.colorScheme.primary, dotSize = 5.dp)
            }
        }
    }
}

/**
 * 思考过程可折叠气泡：左对齐、浅色弱化，与正式回复区分。点击标题栏折叠/展开。
 * [initiallyExpanded]：流式实时展示时默认展开以便边想边看；落库后的历史气泡默认折叠，避免刷屏。
 */
@Composable
internal fun ReasoningBubble(text: String, initiallyExpanded: Boolean = true) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        FeatherIcons.Star,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "思考过程",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = Brand.IconGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 打字机平滑：把会「跳变」的目标文本（流式分片经 StateFlow 合流后常常一次到达一大块）
 * 转成以稳定帧率逐字浮现的显示文本。落后越多步进越大（按比例追赶），既不会越拖越远，
 * 临近追平时又退化为逐字，呈现自然的打字手感。目标被清空/缩短（新一轮）时立即对齐。
 */
@Composable
internal fun rememberTypewriterText(target: String): String {
    var visibleCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        if (target.length < visibleCount) visibleCount = target.length
        while (visibleCount < target.length) {
            val remaining = target.length - visibleCount
            val step = (remaining / 4).coerceIn(1, 60)
            visibleCount += step
            delay(32)
        }
    }
    return target.take(visibleCount.coerceAtMost(target.length))
}

/**
 * 三个循环跳动的点：通用「正在输入/生成」指示器，取代转圈 spinner。
 * 三点以固定相位差依次上下弹跳，形成波浪式律动。
 *
 * 性能优化：用 graphicsLayer { translationY } 替代 offset(y)，动画值变化在 draw 阶段
 * 处理而不触发 compose/recompose，消除无限动画导致父布局每帧重组的开销。
 * 容器高度固定，防止布局波动传递到 LazyColumn。
 */
@Composable
internal fun TypingDots(
    color: Color,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(dotSize + 10.dp)
    ) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0f at 0
                        -5f at 180
                        0f at 360
                        0f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot-$index"
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = offsetY }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
            )
            if (index < 2) Spacer(Modifier.width(4.dp))
        }
    }
}
