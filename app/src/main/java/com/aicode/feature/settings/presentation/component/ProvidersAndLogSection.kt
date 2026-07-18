package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.util.LogLevel
import com.aicode.feature.settings.presentation.LogViewerUiState
import com.aicode.feature.settings.domain.model.AIProviderConfig
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2

/** 提供商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditorScreen]。 */
@Composable
internal fun ProvidersSection(
    providers: List<AIProviderConfig>,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (AIProviderConfig) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyHint("还没有提供商，点右上角 + 添加")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        items(providers) { provider ->
            ProviderItem(
                provider = provider,
                onToggle = { onToggle(provider.id, it) },
                onEdit = { onEdit(provider) }
            )
        }
    }
}

/** 系统日志二级页：合并日志等级与日志查看。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemLogsSection(
    currentLogLevel: LogLevel,
    onSelectLogLevel: (LogLevel) -> Unit,
    logViewerState: LogViewerUiState,
    onSelectFile: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 顶部：日志等级调整卡片
        LogLevelCard(current = currentLogLevel, onSelect = onSelectLogLevel)

        // 中间：日志筛选与文件列表
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = logViewerState.filterServerName?.let { "MCP 日志：$it" } ?: "全部日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = logViewerSummary(logViewerState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )

                if (logViewerState.files.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        logViewerState.files.forEach { fileName ->
                            FilterChip(
                                selected = fileName == logViewerState.selectedFileName,
                                onClick = { onSelectFile(fileName) },
                                label = { Text(fileName.removePrefix("log-").removeSuffix(".txt")) }
                            )
                        }
                    }
                }
            }
        }

        // 底部：日志内容区
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            val text = when {
                logViewerState.loading -> "正在读取日志..."
                logViewerState.error != null -> logViewerState.error
                logViewerState.content.isBlank() -> "当前筛选条件下没有日志"
                else -> logViewerState.content
            }

            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .verticalScroll(verticalScroll)
                        .padding(Spacing.md),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (logViewerState.error == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

private fun logViewerSummary(state: LogViewerUiState): String {
    val file = state.selectedFileName ?: "未选择文件"
    val scope = state.filterServerName?.let { "筛选：$it" } ?: "未筛选"
    val count = if (state.totalLines > state.shownLines) {
        "显示最后 ${state.shownLines}/${state.totalLines} 行"
    } else {
        "显示 ${state.shownLines} 行"
    }
    return "$file · $scope · $count"
}

/** 居中空态提示。 */
@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 日志等级选择卡片：6 个等级单选，选中即持久化并实时生效。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogLevelCard(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "日志等级",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "记录低于该等级的日志将被忽略。开发期建议 VERBOSE（全部记录）；NONE 关闭日志。" +
                    "日志文件位于 Android/data/<包名>/files/logs/。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
            )
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                LogLevel.values().forEach { level ->
                    FilterChip(
                        selected = level == current,
                        onClick = { onSelect(level) },
                        label = { Text(level.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (provider.models.isEmpty()) {
                        "未配置模型，点击编辑添加"
                    } else {
                        "模型：${provider.effectiveModel} · 共 ${provider.models.size} 个"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = provider.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(end = Spacing.md)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    FeatherIcons.Edit2,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
