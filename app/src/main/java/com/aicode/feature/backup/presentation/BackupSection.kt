package com.aicode.feature.backup.presentation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份页：自包含导出/导入全流程。
 *
 * 导出：输入口令 → ViewModel 生成加密字节 → SAF 选保存位置 → 写入。
 * 导入：SAF 选备份文件 → 读取字节 → 输入口令 → ViewModel 解密合并。
 *
 * SAF launcher 用 [rememberLauncherForActivityResult] 在 Composable 内注册（等价 Activity 级），
 * 无需改动 MainActivity。
 */
@Composable
internal fun BackupSection(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var pendingImportData by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = (state as? BackupState.ExportSuccess)?.bytes
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }
                }.onSuccess {
                    Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "写入失败: ${it.message}", Toast.LENGTH_LONG).show()
                }
                viewModel.reset()
            }
        } else {
            viewModel.reset()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            pendingAction = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val data = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }.getOrNull()
            if (data == null) {
                Toast.makeText(context, "读取备份文件失败", Toast.LENGTH_LONG).show()
                pendingAction = null
            } else {
                pendingImportData = data
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        BackupInfoCard()
        ActionCard(
            icon = FeatherIcons.Download,
            title = "导出备份",
            subtitle = "将配置、聊天历史与凭据加密导出为单个文件",
            enabled = state !is BackupState.Working,
            onClick = { pendingAction = PendingAction.Export }
        )
        ActionCard(
            icon = FeatherIcons.Upload,
            title = "导入备份",
            subtitle = "从备份文件还原数据（合并，不清除现有数据）",
            enabled = state !is BackupState.Working,
            onClick = {
                pendingAction = PendingAction.Import
                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
        )
    }

    // 导出口令弹窗
    if (pendingAction == PendingAction.Export) {
        PasswordDialog(
            title = "设置导出口令",
            confirmText = "导出",
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                val pw = password
                password = ""
                pendingAction = null
                viewModel.export(pw)
            },
            onDismiss = {
                password = ""
                pendingAction = null
            }
        )
    }

    // 导入口令弹窗（SAF 选完文件、字节就绪后弹出）
    if (pendingAction == PendingAction.Import && pendingImportData != null) {
        PasswordDialog(
            title = "输入备份口令",
            confirmText = "导入",
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                val pw = password
                val data = pendingImportData
                password = ""
                pendingAction = null
                pendingImportData = null
                if (data != null) viewModel.import(data, pw)
            },
            onDismiss = {
                password = ""
                pendingAction = null
                pendingImportData = null
            }
        )
    }

    // 导出成功 → 启动 SAF 选保存位置
    LaunchedEffect(state) {
        if (state is BackupState.ExportSuccess) {
            exportLauncher.launch("aicode-backup-${System.currentTimeMillis()}.bak")
        }
    }

    if (state is BackupState.Working) {
        ProgressDialog()
    }

    when (state) {
        is BackupState.Error -> ResultDialog(
            title = "操作失败",
            message = (state as BackupState.Error).message,
            onDismiss = { viewModel.reset() }
        )
        is BackupState.ImportSuccess -> ResultDialog(
            title = "导入完成",
            message = buildImportSummary((state as BackupState.ImportSuccess).stats),
            onDismiss = { viewModel.reset() }
        )
        else -> {}
    }
}

private enum class PendingAction { Export, Import }

@Composable
private fun BackupInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "加密备份",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "备份文件用你的口令加密（AES-GCM），含 API Key 等凭据。请妥善保管口令——口令丢失则无法恢复。导入后应用内仍按现状明文存储。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmText: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("口令") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("处理中") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                Text("正在加密 / 解密数据…")
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

private fun buildImportSummary(stats: com.aicode.feature.backup.domain.RestoreStats): String = buildString {
    appendLine("已还原数据：")
    if (stats.providers > 0) appendLine("· AI 提供商 ${stats.providers} 个")
    if (stats.gitCredentials > 0) appendLine("· Git 凭据 ${stats.gitCredentials} 条")
    if (stats.remoteConnections > 0) appendLine("· 远程连接 ${stats.remoteConnections} 个")
    if (stats.remoteMounts > 0) appendLine("· 远程挂载 ${stats.remoteMounts} 个")
    if (stats.chatSessions > 0) appendLine("· 聊天会话 ${stats.chatSessions} 个")
    if (stats.agentMessages > 0) appendLine("· 聊天消息 ${stats.agentMessages} 条")
    if (stats.todoItems > 0) appendLine("· 待办项 ${stats.todoItems} 条")
    if (stats.mcpServers > 0) appendLine("· MCP 服务器 ${stats.mcpServers} 个")
    if (stats.globalPermissionRules > 0) appendLine("· 全局授权规则 ${stats.globalPermissionRules} 条")
    append("主题、保活、日志、识图、同步偏好已覆盖")
}
