package com.aicodeeditor.feature.settings.presentation.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.util.LogLevel
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2

/** 服务商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditorScreen]。 */
@Composable
internal fun ProvidersSection(
    providers: List<AIProviderConfig>,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (AIProviderConfig) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyHint("还没有服务商，点右上角 + 添加")
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

/** 日志等级二级页。 */
@Composable
internal fun LogSection(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
    ) {
        LogLevelCard(current = current, onSelect = onSelect)
    }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
            containerColor = Color.White
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
                    tint = Brand.IconGray)
            }
        }
    }
}
