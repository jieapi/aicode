package com.aicode.feature.credentials.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/**
 * git 提交署名(user.name / user.email)配置卡片。
 *
 * 文本框以 DataStore 持久值初始化；点「保存」时回调 onSave，由 ViewModel 写 DataStore 并同步
 * `git config --global`。容器 HOME=/root 全局共享，写入即对所有工作区生效。
 * [globalHint] 显示容器内 git 实际配置的 user.name，供用户确认同步是否生效。
 */
@Composable
internal fun GitUserIdentityCard(
    initialName: String,
    initialEmail: String,
    initialRepoUrl: String,
    globalHint: String,
    onSave: (name: String, email: String, repoUrl: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var repoUrl by remember(initialRepoUrl) { mutableStateOf(initialRepoUrl) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "提交署名（git config --global）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "用于每次提交的 author。容器全局生效，所有工作区共用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("user.name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("user.email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                label = { Text("仓库地址 remote.origin.url") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (globalHint.isBlank()) "git 实际署名：(未配置)" else "git 实际署名：$globalHint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { onSave(name.trim(), email.trim(), repoUrl.trim()) }) { Text("保存") }
            }
        }
    }
}
