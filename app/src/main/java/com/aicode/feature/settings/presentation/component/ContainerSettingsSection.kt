package com.aicode.feature.settings.presentation.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.container.ContainerProfile
import com.aicode.feature.agent.domain.container.RootfsSource
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Edit3
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Trash2

/**
 * 容器镜像二级页：列出内置与自定义 profile，单选切换；新建自定义（导入 tar.gz + 填 shell 路径与额外参数）；
 * 删除自定义（连带清理其 rootfs 目录）。
 *
 * 自定义镜像只保证能起 shell 跑命令，不 provision、不接管镜像源——所需工具由用户自行在容器内安装。
 */
@Composable
internal fun ContainerSection(
    profiles: List<ContainerProfile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    onSaveCustom: (ContainerProfile) -> Unit,
    onEditCustom: (ContainerProfile) -> Unit,
    onDeleteCustom: (ContainerProfile) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<ContainerProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                text = "选择一个容器镜像。自定义镜像只保证能起 shell 执行命令，不自动安装工具——所需软件请自行在容器内配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }

        items(profiles, key = { it.id }) { profile ->
            val active = profile.id == activeProfileId
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(profile.id) }
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val sourceDesc = when (profile.rootfsSource) {
                            is RootfsSource.Asset -> "内置镜像"
                            is RootfsSource.LocalFile -> "导入的 tar.gz"
                        }
                        val shellDesc = if (profile.isBuiltin) "自动 (bash/sh)"
                        else profile.shellPath?.ifBlank { null } ?: "/bin/sh"
                        Text(
                            text = "$sourceDesc · shell: $shellDesc",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                        if (profile.extraBindings.isNotEmpty()) {
                            Text(
                                text = "绑定: ${profile.extraBindings.joinToString(" ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (active) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (!profile.isBuiltin) {
                        IconButton(onClick = { editingProfile = profile }) {
                            Icon(
                                imageVector = FeatherIcons.Edit3,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deletingProfile = profile }) {
                            Icon(
                                imageVector = FeatherIcons.Trash2,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAddDialog = true },
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "导入自定义镜像 (tar.gz)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ProfileEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, shellPath, bindings, args, uri ->
                val id = "custom-${System.currentTimeMillis()}"
                onSaveCustom(
                    ContainerProfile(
                        id = id,
                        name = name.ifBlank { "自定义镜像" },
                        rootfsSource = RootfsSource.LocalFile(uri),
                        shellPath = shellPath.ifBlank { null },
                        extraBindings = bindings,
                        extraArgs = args,
                        isBuiltin = false
                    )
                )
                showAddDialog = false
            }
        )
    }

    editingProfile?.let { editing ->
        ProfileEditDialog(
            initial = editing,
            onDismiss = { editingProfile = null },
            onConfirm = { name, shellPath, bindings, args, uri ->
                val newSource = if (uri != editing.rootfsSource.let {
                        (it as? RootfsSource.LocalFile)?.uri
                    }) RootfsSource.LocalFile(uri) else editing.rootfsSource
                onEditCustom(
                    editing.copy(
                        name = name.ifBlank { editing.name },
                        shellPath = shellPath.ifBlank { null },
                        extraBindings = bindings,
                        extraArgs = args,
                        rootfsSource = newSource
                    )
                )
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { deleting ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("删除镜像配置") },
            text = { Text("确定删除「${deleting.name}」？其 rootfs 目录会被一并清除，内置 Alpine 不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCustom(deleting)
                    deletingProfile = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingProfile = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ProfileEditDialog(
    initial: ContainerProfile?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, shellPath: String, bindings: List<String>, args: List<String>, uri: String) -> Unit
) {
    val initialUri = (initial?.rootfsSource as? RootfsSource.LocalFile)?.uri
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var shellPath by remember { mutableStateOf(initial?.shellPath ?: "/bin/sh") }
    var bindingsText by remember { mutableStateOf(initial?.extraBindings?.joinToString(" ") ?: "") }
    var argsText by remember { mutableStateOf(initial?.extraArgs?.joinToString(" ") ?: "") }
    var pickedUri by remember { mutableStateOf(initialUri) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pickedUri = uri.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "导入自定义镜像" else "编辑容器配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shellPath,
                    onValueChange = { shellPath = it },
                    label = { Text("shell 路径（如 /bin/sh、/bin/bash）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bindingsText,
                    onValueChange = { bindingsText = it },
                    label = { Text("额外绑定（空格分隔，如 /sdcard:/mnt）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text("额外 proot 参数（空格分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                TextButton(
                    onClick = { pickLauncher.launch(arrayOf("*/*")) }
                ) {
                    Text(
                        pickedUri?.let {
                            if (it == initialUri) "已导入（点此重新选择）" else "已选择文件"
                        } ?: "选择 tar.gz / tgz / tar.xz / txz 镜像文件"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val uri = pickedUri ?: return@TextButton
                    val bindings = bindingsText.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
                    val args = argsText.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(name, shellPath, bindings, args, uri)
                },
                enabled = pickedUri != null
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
