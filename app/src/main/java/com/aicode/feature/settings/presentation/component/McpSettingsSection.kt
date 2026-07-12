package com.aicode.feature.settings.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Brand
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.mcp.McpServerConfig
import com.aicode.feature.agent.domain.mcp.McpServerStatus
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Server
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Trash2
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * MCP 二级页（可视化）：全新现代化设计的 Server 列表页面。
 */
@Composable
internal fun McpSection(
    servers: List<McpServerConfig>,
    statuses: List<McpServerStatus>,
    reloading: Boolean,
    onReload: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (McpServerConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (servers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.lg)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                FeatherIcons.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "还没有配置 MCP 服务器",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "点击右上角 + 可以添加远程 HTTP 或本地 stdio 服务器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(servers, key = { it.name }) { server ->
                McpServerRow(
                    server = server,
                    status = statuses.firstOrNull { it.name == server.name },
                    onClick = { onEdit(server) },
                    onDelete = { onDelete(server.name) }
                )
            }
        }
    }
}

/** 单个 MCP server 行：现代化卡片样式（图标状态标签 + 药丸标签 + 右侧箭头，支持左滑删除）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpServerRow(
    server: McpServerConfig,
    status: McpServerStatus?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isConnected = server.enabled && status?.state == McpServerStatus.State.CONNECTED

    val statusText = when {
        !server.enabled -> "已禁用"
        status == null -> "未连接"
        else -> when (status.state) {
            McpServerStatus.State.CONNECTED -> "已连接"
            McpServerStatus.State.CONNECTING -> "连接中…"
            McpServerStatus.State.FAILED -> "连接失败"
            McpServerStatus.State.DISABLED -> "已禁用"
        }
    }

    val statusColor = when {
        !server.enabled || status == null || status.state == McpServerStatus.State.DISABLED ->
            MaterialTheme.colorScheme.outline
        status.state == McpServerStatus.State.CONNECTED ->
            MaterialTheme.colorScheme.tertiary
        status.state == McpServerStatus.State.CONNECTING ->
            MaterialTheme.colorScheme.primary
        else ->
            MaterialTheme.colorScheme.error
    }

    val statusBgColor = statusColor.copy(alpha = 0.12f)

    val density = LocalDensity.current
    val revealPx = remember(density) { with(density) { -112.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val revealedWidthDp = with(density) { (-offsetX.value).toDp().coerceAtLeast(0.dp) }
    val maxButtonWidth = 104.dp
    val buttonWidth = if (revealedWidthDp > 8.dp) (revealedWidthDp - 8.dp).coerceAtMost(maxButtonWidth) else 0.dp
    val progress = (buttonWidth / maxButtonWidth).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. 底层删除按钮（固定在右端，向右滑动滑动时会受到挤压、缩放与透明度渐变，直到消失）
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (buttonWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(buttonWidth)
                        .graphicsLayer {
                            alpha = (progress * 1.2f).coerceIn(0f, 1f)
                            scaleX = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
                            scaleY = (0.7f + 0.3f * progress).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEF4444))
                        .border(1.dp, Color(0xFFF87171), RoundedCornerShape(16.dp))
                        .clickable {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                                onDelete()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.requiredWidth(104.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Trash2,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. 表层卡片（支持手势回弹与滑动展开）
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            coroutineScope.launch { offsetX.stop() }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < revealPx / 2) {
                                    offsetX.animateTo(
                                        targetValue = revealPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(revealPx * 1.15f, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetX.value < -10f) {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    } else {
                        onClick()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧容器图标 + 状态圆点
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (server.isStdio) FeatherIcons.Terminal else FeatherIcons.Server,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                    // 右下角带描边的状态圆点
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(10.dp)
                            .background(color = statusColor, shape = RoundedCornerShape(Radius.pill))
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.pill))
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 中间标题和 Pill 标签
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 状态 Pill
                        Box(
                            modifier = Modifier
                                .background(statusBgColor, RoundedCornerShape(Radius.pill))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = statusColor
                            )
                        }

                        // 2. 类型 Pill
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(Radius.pill)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (server.isStdio) "内置" else "HTTP",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 3. 工具数量/信息 Pill
                        val infoText = when {
                            isConnected -> "工具: ${status?.toolCount ?: 0}/${status?.toolCount ?: 0}"
                            server.isStdio -> server.command.orEmpty().ifEmpty { "stdio" }
                            else -> server.url.orEmpty().ifEmpty { "HTTP" }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(Radius.pill)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = infoText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧箭头
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = "详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
