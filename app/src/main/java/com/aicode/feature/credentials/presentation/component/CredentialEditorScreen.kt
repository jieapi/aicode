package com.aicode.feature.credentials.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TextButton
import com.aicode.core.theme.Spacing
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.model.newCredentialId
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import compose.icons.feathericons.Trash2

/**
 * 凭据编辑页（全屏）。host / 用户名 / Token / 别名 / 设为默认。
 * host 为空或 token 为空时不允许保存（落库前拦截，避免无效凭据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CredentialEditorScreen(
    initial: GitCredential?,
    onBack: () -> Unit,
    onSave: (GitCredential) -> Unit,
    onDelete: (String) -> Unit
) {
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var isDefault by remember { mutableStateOf(initial?.isDefault ?: false) }
    var tokenVisible by remember { mutableStateOf(false) }

    val canSave = host.trim().isNotBlank() && username.trim().isNotBlank() && token.isNotBlank()

    fun current(): GitCredential? {
        if (!canSave) return null
        return GitCredential(
            id = initial?.id ?: newCredentialId(),
            host = host.trim(),
            username = username.trim(),
            token = token,
            label = label.trim(),
            isDefault = isDefault,
            createdAt = initial?.createdAt ?: 0L,
            updatedAt = initial?.updatedAt ?: 0L
        )
    }

    fun saveAndBack() {
        current()?.let {
            onSave(it)
            onBack()
        }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "添加凭据" else "编辑凭据") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { onDelete(initial.id); onBack() }) {
                            Icon(FeatherIcons.Trash2, contentDescription = "删除凭据", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "凭据用于推送/拉取 https 远程仓库时认证。同 host 多账号时，开启「默认」的优先注入；均未开启默认则取该 host 首条。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("远程主机 host") },
                placeholder = { Text("github.com") },
                singleLine = true,
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
                        Icon(if (tokenVisible) FeatherIcons.EyeOff else FeatherIcons.Eye, contentDescription = if (tokenVisible) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("别名（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("设为该 host 的默认凭据", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "同 host 多账号时，默认那条优先注入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isDefault, onCheckedChange = { isDefault = it })
            }
            Button(
                onClick = { saveAndBack() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (initial == null) "添加" else "保存") }
            if (!canSave) {
                Text(
                    text = "host、用户名、Token 均不能为空",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
