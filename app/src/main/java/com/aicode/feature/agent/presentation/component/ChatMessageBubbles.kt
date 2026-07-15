package com.aicode.feature.agent.presentation.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image as ComposeImage
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Brand
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.presentation.AgentAttachment
import com.aicode.feature.agent.presentation.AgentUIMessage
import com.aicode.feature.agent.presentation.hasVisibleContent
import com.aicode.feature.agent.presentation.MessageRole
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.State as MarkdownParseState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Copy
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.Star
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import kotlinx.coroutines.delay

internal class MarkdownRenderCache(
    private val maxEntries: Int = 80
) {
    private val parsedStates = object : LinkedHashMap<String, MarkdownParseState.Success>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MarkdownParseState.Success>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(text: String): MarkdownParseState.Success? = parsedStates[text]

    fun put(state: MarkdownParseState.Success) {
        parsedStates[state.content] = state
    }
}

@Composable
internal fun AgentMessageItem(
    message: AgentUIMessage,
    liveOutput: String? = null,
    markdownCache: MarkdownRenderCache? = null
) {
    if (message.isCompactionMarker) {
        CompactionDivider()
        return
    }

    val hasReasoning = message.role == MessageRole.ASSISTANT && !message.reasoning.isNullOrEmpty()
    val hasContent = message.content.hasVisibleContent()
    val hasAttachments = message.attachments.isNotEmpty()
    if (message.role == MessageRole.ASSISTANT && !hasContent && !hasReasoning) return

    val isUser = message.role == MessageRole.USER
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (hasReasoning) {
            ReasoningBubble(text = message.reasoning!!, initiallyExpanded = false)
        }
        if (hasContent || hasAttachments || message.role != MessageRole.ASSISTANT) {
            Column(
                // 助手消息左对齐，用户消息右对齐
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                if (hasContent || message.role == MessageRole.TOOL) {
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
                                val selectionColors = if (isUser) {
                                    TextSelectionColors(
                                        handleColor = MaterialTheme.colorScheme.onPrimary,
                                        backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f),
                                    )
                                } else {
                                    TextSelectionColors(
                                        handleColor = MaterialTheme.colorScheme.primary,
                                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    )
                                }
                                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                                    MarkdownContent(
                                        text = message.content,
                                        color = textColor,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                                        cache = markdownCache
                                    )
                                }
                            }
                        }
                    }
                }
                if (isUser && hasAttachments) {
                    MessageAttachmentPreviewRow(attachments = message.attachments)
                }
                // 气泡下方复制按钮（工具消息不显示）
                if (message.content.hasVisibleContent() && message.role != MessageRole.TOOL) {
                    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = iconTint),
                    ) {
                        Icon(
                            if (copied) FeatherIcons.Check else FeatherIcons.Copy,
                            contentDescription = if (copied) "已复制" else "复制",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    // 复制成功 1.5s 后恢复图标
                    if (copied) {
                        LaunchedEffect(Unit) {
                            delay(1500)
                            copied = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAttachmentPreviewRow(attachments: List<AgentAttachment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .horizontalScroll(rememberScrollState())
            .padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        attachments.forEach { attachment ->
            MessageAttachmentPreviewItem(attachment = attachment)
        }
    }
}

@Composable
private fun MessageAttachmentPreviewItem(attachment: AgentAttachment) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(76.dp)
    ) {
        if (attachment.isImage) {
            MessageImagePreview(attachment = attachment)
        } else {
            MessageFilePreview(attachment = attachment)
        }
    }
}

@Composable
private fun MessageImagePreview(attachment: AgentAttachment) {
    val bitmap = remember(attachment.localPath) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(attachment.localPath, bounds)
            val sampleSize = calculateMessageAttachmentSampleSize(bounds.outWidth, bounds.outHeight, 180, 180)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(attachment.localPath, options)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap,
            contentDescription = attachment.fileName.ifBlank { "图片预览" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                FeatherIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MessageFilePreview(attachment: AgentAttachment) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            FeatherIcons.FileText,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatBytes(attachment.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun calculateMessageAttachmentSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var sampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}

@Composable
private fun CompactionDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "上下文已压缩",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
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
    cache: MarkdownRenderCache? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val mdColors = markdownColor(
        text = color,
        codeBackground = if (isDark) Color(0xFF152030) else Color(0xFFE8EDF3),
        inlineCodeBackground = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f),
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
        val cachedState = cache?.get(text)
        val parsedState = if (cachedState != null) {
            cachedState
        } else {
            val mdState = rememberMarkdownState(content = text, retainState = true)
            val state by mdState.state.collectAsState()
            state
        }

        if (parsedState is MarkdownParseState.Success) {
            LaunchedEffect(cache, parsedState) {
                cache?.put(parsedState)
            }
        }

        when (parsedState) {
            is MarkdownParseState.Success -> Markdown(
                state = parsedState,
                modifier = modifier,
                colors = mdColors,
                typography = mdTypography,
                padding = mdPadding,
                dimens = mdDimens,
                // 关闭段落文本的 animateContentSize：快速流式更新下它会持续追赶目标高度，反而弹性抖动。
                animations = markdownAnimations(animateTextSize = { this }),
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

            is MarkdownParseState.Loading -> PlainMarkdownText(
                text = text,
                color = color,
                modifier = modifier
            )

            is MarkdownParseState.Error -> PlainMarkdownText(
                text = text,
                color = color,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PlainMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = color, lineHeight = 20.sp)
    )
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

/** 上下文压缩期间的临时状态气泡，不落库。 */
@Composable
internal fun CompactionProgressBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "正在压缩上下文",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 模型流式吐字时的实时气泡：左对齐、与助手气泡同款。
 * 尾部带三个跳动的点表示仍在生成。本轮结束后由落库的助手气泡接管。
 *
 * 流式阶段也渲染 Markdown，但使用采样文本降低解析频率；最终落库消息再走常规缓存渲染。
 */
@Composable
internal fun StreamingBubble(text: String) {
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
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.sm))
                TypingDots(color = MaterialTheme.colorScheme.primary, dotSize = 5.dp)
            }
        }
    }
}

/** 思维链折叠阈值：超过此行数视为过长，自动折叠为前 N 行 + 「展开剩余 X 行」。 */
internal const val REASONING_COLLAPSE_LINE_LIMIT = 8

/**
 * 思考过程可折叠气泡：左对齐、浅色弱化，与正式回复区分。点击标题栏折叠/展开。
 *
 * 折叠判定按行数阈值：超过 [REASONING_COLLAPSE_LINE_LIMIT] 行视为「过长」，自动折叠为
 * 前 N 行 + 「展开剩余 X 行」。流式实时展示时，短文本边想边看，一旦长度越过阈值即自动
 * 折叠（折叠态下新内容仍持续追加，保持折叠不刷屏，用户可随时点开看最新）；落库后的历史
 * 气泡默认折叠，避免刷屏。用户手动 toggle 后以用户选择为准，不再被自动折叠覆盖。
 */
@Composable
internal fun ReasoningBubble(
    text: String,
    initiallyExpanded: Boolean = true
) {
    var userToggled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val overThreshold = lineCount > REASONING_COLLAPSE_LINE_LIMIT
    // 自动折叠：仅在用户尚未手动 toggle 过时生效；用户手动展开/折叠后以用户选择为准
    val effectiveExpanded = if (userToggled) expanded else (initiallyExpanded && !overThreshold)
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
                        .clickable {
                            userToggled = true
                            expanded = !expanded
                        },
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
                        if (effectiveExpanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                        contentDescription = if (effectiveExpanded) "折叠" else "展开",
                        tint = Brand.IconGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (effectiveExpanded) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (overThreshold) {
                    // 折叠态：前 N 行 + 「展开剩余 X 行」
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = REASONING_COLLAPSE_LINE_LIMIT,
                        overflow = TextOverflow.Ellipsis
                    )
                    val hidden = lineCount - REASONING_COLLAPSE_LINE_LIMIT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable {
                                userToggled = true
                                expanded = true
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            FeatherIcons.ChevronDown,
                            contentDescription = "展开",
                            tint = Brand.IconGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "展开剩余 $hidden 行",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
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
