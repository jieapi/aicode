package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.model.ChatSession
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

/**
 * 侧边栏内容：顶部「新建会话」，中部历史记录列表，底部「设置」入口。
 * 由 AIChatPanel 的 ModalNavigationDrawer 承载，支持左上角按钮点击或右滑打开。
 */
@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    agentStates: Map<String, AgentUIState>,
    onSelect: (ChatSession) -> Unit,
    onCreate: () -> Unit,
    onDelete: (ChatSession) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.lg)
    ) {
        // 顶部：新建会话
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onCreate() }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                FeatherIcons.Plus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "新建会话",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )

        // 中部：历史记录列表（占据剩余空间）
        Box(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty()) {
                Text(
                    "暂无会话，点击「新建会话」开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val state = agentStates[session.id]
                        val isExecuting = state is AgentUIState.Loading || state is AgentUIState.Streaming
                        ChatSessionRow(
                            session = session,
                            selected = session.id == currentSessionId,
                            isExecuting = isExecuting,
                            onClick = { onSelect(session) },
                            onDelete = { pendingDelete = session }
                        )
                    }
                }
            }
        }

        // 底部：设置入口
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onNavigateToSettings() }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                FeatherIcons.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "设置",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${session.title}」？该会话的所有消息都将被清除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
