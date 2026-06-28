package com.aicodeeditor.feature.workspace.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.workspace.domain.model.Workspace
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel

/**
 * 顶栏的工作区选择胶囊：显示当前工作区名，点击弹出选择/新建/删除面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceChip(
    viewModel: WorkspaceViewModel,
    hasRunningSessions: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()

    var showSheet by remember { mutableStateOf(false) }
    var pendingWorkspaceSelect by remember { mutableStateOf<Workspace?>(null) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showSheet = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = current?.name ?: "选择工作区",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.UnfoldMore,
            contentDescription = "切换工作区",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showSheet) {
        WorkspaceSheet(
            workspaces = workspaces,
            current = current,
            onSelect = {
                if (hasRunningSessions()) {
                    pendingWorkspaceSelect = it
                } else {
                    viewModel.selectWorkspace(it.name)
                    showSheet = false
                }
            },
            onCreate = { viewModel.createWorkspace(it) },
            onDelete = { viewModel.deleteWorkspace(it.name) },
            onDismiss = { showSheet = false }
        )
    }

    pendingWorkspaceSelect?.let { ws ->
        AlertDialog(
            onDismissRequest = { pendingWorkspaceSelect = null },
            title = { Text("切换工作区") },
            text = { Text("当前工作区有正在运行的会话，切换工作区后它们将在后台继续运行。确定要切换吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.selectWorkspace(ws.name)
                    pendingWorkspaceSelect = null
                    showSheet = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingWorkspaceSelect = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceSheet(
    workspaces: List<Workspace>,
    current: Workspace?,
    onSelect: (Workspace) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Workspace) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Workspace?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "工作区",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("新建")
                }
            }

            if (workspaces.isEmpty()) {
                Text(
                    "暂无工作区，点击「新建」创建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(workspaces, key = { it.name }) { ws ->
                        WorkspaceRow(
                            workspace = ws,
                            selected = ws.name == current?.name,
                            canDelete = workspaces.size > 1,
                            onClick = { onSelect(ws) },
                            onDelete = { pendingDelete = ws }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWorkspaceDialog(
            existingNames = workspaces.map { it.name },
            onConfirm = {
                onCreate(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    pendingDelete?.let { ws ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除工作区") },
            text = { Text("确定删除「${ws.name}」？该目录下的所有文件都将被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(ws)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WorkspaceRow(
    workspace: Workspace,
    selected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = workspace.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "当前",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else if (canDelete) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateWorkspaceDialog(
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val canConfirm = trimmed.isNotEmpty() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建工作区") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名称") },
                    placeholder = { Text("例如 my-project") },
                    isError = duplicate,
                    leadingIcon = {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    }
                )
                if (duplicate) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "已存在同名工作区",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(trimmed) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
