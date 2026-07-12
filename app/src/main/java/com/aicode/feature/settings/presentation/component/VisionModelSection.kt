package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/**
 * 识图模型二级页：选择一个用于识图（viewImage）兜底的专用模型，或选择「跟随当前聊天模型」。
 *
 * 识图优先用当前聊天模型的原生视觉能力；当当前聊天模型不支持 Vision 时，临时切到此专用模型发送。
 * 每个模型旁标注其 ModelMetadata.supportsVision（Image），便于挑选--但不强制过滤，
 * 即使选了不支持 Vision 的模型也会持久化，实际发送时由工作流校验报错。
 */
@Composable
internal fun VisionModelSection(
    providers: List<AIProviderConfig>,
    visionProviderId: String,
    visionModel: String,
    modelMetadata: Map<String, ModelMetadata>,
    onLoadMetadata: () -> Unit,
    onSelect: (providerId: String, model: String) -> Unit,
    onClear: () -> Unit
) {
    // 进入本页时主动加载所有已启用 provider 的模型元数据--否则 map 为空，
    // 所有模型都会被误判为不支持图片输入。
    LaunchedEffect(Unit) { onLoadMetadata() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (visionProviderId.isBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClear() }
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "跟随当前聊天模型",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "识图始终用当前选中的聊天模型；当其不支持 Vision 时识图不可用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    if (visionProviderId.isBlank()) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (providers.none { it.isEnabled && it.models.isNotEmpty() }) {
            item {
                Text(
                    text = "暂无可用模型。请在「AI 提供商」中添加并配置模型后再选识图专用模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        } else {
            providers.forEach { provider ->
                if (provider.isEnabled && provider.models.isNotEmpty()) {
                    item(key = "header_${provider.id}") {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xs)
                        )
                    }
                    items(provider.models, key = { "${provider.id}_$it" }) { model ->
                        VisionModelRow(
                            provider = provider,
                            model = model,
                            selected = provider.id == visionProviderId && model == visionModel,
                            supportsVision = modelMetadata[model]?.supportsVision == true,
                            onClick = { onSelect(provider.id, model) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionModelRow(
    provider: AIProviderConfig,
    model: String,
    selected: Boolean,
    supportsVision: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
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
                    text = model,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (supportsVision) "支持图片输入" else "不支持图片输入（不推荐用于识图）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (supportsVision) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            if (selected) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = FeatherIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
