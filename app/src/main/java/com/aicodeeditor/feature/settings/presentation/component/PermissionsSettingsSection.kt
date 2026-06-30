package com.aicodeeditor.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.permission.PermissionDecision
import com.aicodeeditor.feature.agent.domain.permission.PermissionRule
import compose.icons.FeatherIcons
import compose.icons.feathericons.Trash2

/**
 * 「工具授权」二级页：列出当前项目与全局已保存的授权规则，可逐条删除；项目规则可「提升为全局」。
 */
@Composable
internal fun PermissionsSection(
    projectName: String?,
    projectRules: List<PermissionRule>,
    globalRules: List<PermissionRule>,
    onDeleteProject: (PermissionRule) -> Unit,
    onPromote: (PermissionRule) -> Unit,
    onDeleteGlobal: (PermissionRule) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = "🔒 内置安全白名单",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "系统已预设白名单，对少量完全无害、只读且不派生子进程的命令（如 ls、pwd、cat、grep、ps、top、git status 等）免弹窗自动放行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "在此可管理用户在对话中选「始终允许」生成的记忆规则。精细化授权机制会按命令或路径精准匹配（如 rm temp.log、git pull）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { RuleGroupHeader(if (projectName != null) "当前项目：$projectName" else "当前项目（未选择）") }
        if (projectRules.isEmpty()) {
            item { RuleEmptyHint("本项目暂无规则") }
        } else {
            items(projectRules) { rule ->
                RuleRow(rule = rule, onDelete = { onDeleteProject(rule) }, onPromote = { onPromote(rule) })
            }
        }

        item { RuleGroupHeader("全局") }
        if (globalRules.isEmpty()) {
            item { RuleEmptyHint("暂无全局规则") }
        } else {
            items(globalRules) { rule ->
                RuleRow(rule = rule, onDelete = { onDeleteGlobal(rule) }, onPromote = null)
            }
        }
    }
}

@Composable
internal fun RuleGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

@Composable
internal fun RuleEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 单条规则行：匹配模式（主）+ 工具名/判定（次），右侧「提升为全局」(仅项目规则) 与删除。 */
@Composable
internal fun RuleRow(
    rule: PermissionRule,
    onDelete: () -> Unit,
    onPromote: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rule.pattern == PermissionRule.WHOLE_TOOL) "整个工具" else rule.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.toolName + " · " + if (rule.decision == PermissionDecision.ALLOW) "允许" else "禁止",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onPromote != null) {
                TextButton(onClick = onPromote) { Text("提升为全局") }
            }
            IconButton(onClick = onDelete) {
                Icon(FeatherIcons.Trash2, contentDescription = "删除", tint = Brand.IconGray)
            }
        }
    }
}
