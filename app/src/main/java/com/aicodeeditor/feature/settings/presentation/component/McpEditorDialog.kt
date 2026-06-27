package com.aicodeeditor.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.mcp.McpServerConfig

/** MCP server 可视化编辑对话框：类型切换（HTTP / stdio）+ 各自字段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerEditDialog(
    initial: McpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var isStdio by remember { mutableStateOf(initial?.isStdio ?: false) }

    // HTTP 形态字段
    var url by remember { mutableStateOf(initial?.url ?: "") }
    val headers = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(initial?.headers?.toList() ?: emptyList())
        }
    }

    // stdio 形态字段
    var command by remember { mutableStateOf(initial?.command ?: "") }
    val argsList = remember {
        mutableStateListOf<String>().apply { addAll(initial?.args ?: emptyList()) }
    }
    val envList = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(initial?.env?.toList() ?: emptyList())
        }
    }

    val canSave = name.isNotBlank() && if (isStdio) command.isNotBlank() else url.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 MCP 服务器" else "编辑 MCP 服务器") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── 类型切换 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = !isStdio,
                        onClick = { isStdio = false },
                        label = { Text("远程 HTTP") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isStdio,
                        onClick = { isStdio = true },
                        label = { Text("本地 stdio") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth())

                if (isStdio) {
                    McpStdioFields(
                        command = command,
                        onCommandChange = { command = it },
                        args = argsList,
                        env = envList
                    )
                } else {
                    McpHttpFields(
                        url = url,
                        onUrlChange = { url = it },
                        headers = headers
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val config = if (isStdio) {
                        McpServerConfig(
                            name = name.trim(),
                            command = command.trim(),
                            args = argsList.map { it.trim() }.filter { it.isNotEmpty() },
                            env = envList
                                .map { it.first.trim() to it.second }
                                .filter { it.first.isNotEmpty() }
                                .toMap(),
                            enabled = enabled
                        )
                    } else {
                        McpServerConfig(
                            name = name.trim(),
                            url = url.trim(),
                            headers = headers
                                .map { it.first.trim() to it.second }
                                .filter { it.first.isNotEmpty() }
                                .toMap(),
                            enabled = enabled
                        )
                    }
                    onSave(config)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/** HTTP 形态字段：URL + 请求头键值对。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpHttpFields(
    url: String,
    onUrlChange: (String) -> Unit,
    headers: SnapshotStateList<Pair<String, String>>
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("URL") },
        placeholder = { Text("https://example.com/mcp") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    HorizontalDivider(modifier = Modifier.fillMaxWidth())

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "请求头 Headers（${headers.size}）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { headers.add("" to "") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("添加")
        }
    }

    headers.forEachIndexed { index, (k, v) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            OutlinedTextField(
                value = k,
                onValueChange = { headers[index] = it to v },
                label = { Text("键") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = v,
                onValueChange = { headers[index] = k to it },
                label = { Text("值") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { headers.removeAt(index) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** stdio 形态字段：command + 参数列表 + 环境变量键值对。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpStdioFields(
    command: String,
    onCommandChange: (String) -> Unit,
    args: SnapshotStateList<String>,
    env: SnapshotStateList<Pair<String, String>>
) {
    OutlinedTextField(
        value = command,
        onValueChange = onCommandChange,
        label = { Text("命令 command") },
        placeholder = { Text("npx") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // ── 参数 args ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "参数 args（${args.size}）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { args.add("") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("添加")
        }
    }

    args.forEachIndexed { index, value ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { args[index] = it },
                label = { Text("参数 ${index + 1}") },
                placeholder = { Text("-y / @scope/server / --flag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { args.removeAt(index) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    HorizontalDivider(modifier = Modifier.fillMaxWidth())

    // ── 环境变量 env ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "环境变量 env（${env.size}）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { env.add("" to "") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("添加")
        }
    }

    env.forEachIndexed { index, (k, v) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            OutlinedTextField(
                value = k,
                onValueChange = { env[index] = it to v },
                label = { Text("键") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = v,
                onValueChange = { env[index] = k to it },
                label = { Text("值") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { env.removeAt(index) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
