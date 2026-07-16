package com.aicode.feature.credentials.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

/**
 * 拉取/推送缺 https 凭据时的登录弹窗。host 只读预填，用户填 username/token；填完确认回调回填，
 * 取消回调让上层放弃。这是三端（UI / AI Bash / 交互终端）git 缺凭据的统一弹窗——
 * UI 路径与 [com.aicode.feature.credentials.data.CredentialRequestBridge] 监听到的命令行请求都复用本组件。
 *
 * @param host 远程主机（只读预填，来自 git credential 协议的 host 字段）
 * @param onConfirm 回传用户填的 username/token（已 trim）；上层据此落盘 + 回喂 git helper。文本框双非空才可点。
 * @param onDismiss 取消：上层写 cancel 响应，helper 退出非零让 git 报认证失败（与无凭据行为一致）。
 * @param usernameHint git 可能在请求里已带预填 username（如 URL 里 u），非空时作初值；通常为空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialPromptDialog(
    host: String,
    onConfirm: (username: String, token: String) -> Unit,
    onDismiss: () -> Unit,
    usernameHint: String? = null
) {
    var username by remember(host, usernameHint) { mutableStateOf(usernameHint ?: "") }
    var token by remember(host) { mutableStateOf("") }
    var tokenVisible by remember(host) { mutableStateOf(false) }
    val canSave = username.trim().isNotBlank() && token.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要 $host 的登录凭据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { /* host 来自 remote，只读 */ },
                    label = { Text("远程主机") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("访问令牌 Token / PAT") },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (tokenVisible) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "填完即保存并自动重试。该凭据同时会保存到容器内 git，终端与 AI 执行裸 git 命令时也可直接使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSave) onConfirm(username.trim(), token) },
                enabled = canSave
            ) { Text("保存并重试") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
