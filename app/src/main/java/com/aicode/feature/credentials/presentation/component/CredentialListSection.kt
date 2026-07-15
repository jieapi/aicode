package com.aicode.feature.credentials.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.credentials.domain.model.GitCredential
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2

/**
 * 凭据二级页：顶部提交署名卡 + 凭据列表 + 空态。新增/编辑由顶栏「+」与点击触发 [CredentialEditorScreen]。
 * Switch 表示该条是否为其 host 的默认凭据（host 内唯一，切换由仓储清同 host 其它条）。
 */
@Composable
internal fun CredentialListSection(
    credentials: List<GitCredential>,
    userName: String,
    userEmail: String,
    globalUserName: String,
    onEdit: (GitCredential?) -> Unit,
    onToggleDefault: (String, Boolean) -> Unit,
    onSaveIdentity: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            GitUserIdentityCard(
                initialName = userName,
                initialEmail = userEmail,
                globalHint = globalUserName,
                onSave = onSaveIdentity
            )
        }
        if (credentials.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未添加凭据，点右上角 + 添加\n(host / 用户名 / Token)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(credentials, key = { it.id }) { cred ->
                CredentialItem(
                    credential = cred,
                    onToggleDefault = { onToggleDefault(cred.id, it) },
                    onEdit = { onEdit(cred) }
                )
            }
        }
    }
}

@Composable
private fun CredentialItem(
    credential: GitCredential,
    onToggleDefault: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credential.label.ifBlank { "${credential.host} · ${credential.username}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${credential.host} · ${credential.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = credential.isDefault,
                onCheckedChange = onToggleDefault,
                modifier = Modifier.padding(end = Spacing.md)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    FeatherIcons.Edit2,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
