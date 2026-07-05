package com.aicodeeditor.feature.workspace.presentation.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicodeeditor.feature.workspace.domain.model.RemoteConnection
import com.aicodeeditor.feature.workspace.domain.model.RemoteMount
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteConnectionDialog(
    initialConnection: RemoteConnection? = null,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, RemoteProtocol) -> Unit,
    onTestConnection: (String, String, String, String, RemoteProtocol, (Boolean, String) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember(initialConnection) { mutableStateOf(initialConnection?.name ?: "") }
    var host by remember(initialConnection) { mutableStateOf(initialConnection?.host ?: "") }
    var port by remember(initialConnection) { mutableStateOf(initialConnection?.port?.toString() ?: "22") }
    var username by remember(initialConnection) { mutableStateOf(initialConnection?.username ?: "") }
    var password by remember(initialConnection) { mutableStateOf(initialConnection?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSftp by remember(initialConnection) { mutableStateOf(initialConnection?.protocol != RemoteProtocol.FTP) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(if (initialConnection != null) "编辑连接通道" else "添加连接通道", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("协议类型:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = isSftp,
                        onClick = { isSftp = true; port = "22" },
                        label = { Text("SFTP") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !isSftp,
                        onClick = { isSftp = false; port = "21" },
                        label = { Text("FTP") }
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("连接名称 (如: 腾讯云云服务器)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机地址 (IP 或域名)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) FeatherIcons.Eye else FeatherIcons.EyeOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(image, "切换密码可见性", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val protocol = if (isSftp) RemoteProtocol.SFTP else RemoteProtocol.FTP
                        isTesting = true
                        onTestConnection(host, port, username, password, protocol) { success, msg ->
                            isTesting = false
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting && host.isNotBlank() && username.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连通性")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val protocol = if (isSftp) RemoteProtocol.SFTP else RemoteProtocol.FTP
                onAdd(name, host, port, username, password, protocol)
            }) {
                Text(if (initialConnection != null) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteMountDialog(
    initialMount: RemoteMount? = null,
    connections: List<RemoteConnection>,
    workspaces: List<com.aicodeeditor.feature.workspace.domain.model.Workspace>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Boolean) -> Unit,
    onListDirectories: (String, String, (Boolean, List<String>, String) -> Unit) -> Unit
) {
    var selectedConnectionId by remember(initialMount) { mutableStateOf(initialMount?.connectionId ?: connections.firstOrNull()?.id ?: "") }
    var remotePath by remember(initialMount) { mutableStateOf(initialMount?.remotePath ?: "/") }

    var selectedWorkspacePath by remember(initialMount) { mutableStateOf(initialMount?.localMountPath ?: workspaces.firstOrNull()?.path ?: "") }
    var autoConnect by remember(initialMount) { mutableStateOf(initialMount?.autoConnect ?: true) }

    var connExpanded by remember { mutableStateOf(false) }
    var wsExpanded by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(if (initialMount != null) "编辑工作区" else "添加工作区", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = connExpanded,
                    onExpandedChange = { connExpanded = !connExpanded }
                ) {
                    val selectedName = connections.find { it.id == selectedConnectionId }?.name ?: "选择远程通道"
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("关联远程连接通道") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)                    )
                    ExposedDropdownMenu(
                        expanded = connExpanded,
                        onDismissRequest = { connExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        connections.forEach { conn ->
                            DropdownMenuItem(
                                text = { Text(conn.name) },
                                onClick = {
                                    selectedConnectionId = conn.id
                                    connExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text("远程目标目录 (绝对路径)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showBrowser = true },
                        enabled = selectedConnectionId.isNotEmpty()
                    ) {
                        Icon(FeatherIcons.Folder, contentDescription = "浏览目录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = wsExpanded,
                    onExpandedChange = { wsExpanded = !wsExpanded }
                ) {
                    val selectedWsName = workspaces.find { it.path == selectedWorkspacePath }?.name ?: "选择本地工作区"
                    OutlinedTextField(
                        value = selectedWsName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("映射到本地工作区") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)                    )
                    ExposedDropdownMenu(
                        expanded = wsExpanded,
                        onDismissRequest = { wsExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        workspaces.forEach { ws ->
                            DropdownMenuItem(
                                text = { Text(ws.name) },
                                onClick = {
                                    selectedWorkspacePath = ws.path
                                    wsExpanded = false
                                }
                            )
                        }
                    }
                }

                if (workspaces.isEmpty()) {
                    Text("暂无本地工作区，请先在侧边栏创建一个。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("应用启动时自动连接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("开机自动连接并同步该工作区", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(selectedConnectionId, remotePath, selectedWorkspacePath, autoConnect)
                },
                enabled = selectedWorkspacePath.isNotEmpty() && selectedConnectionId.isNotEmpty()
            ) {
                Text(if (initialMount != null) "保存" else "添加工作区")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )

    if (showBrowser) {
        RemoteDirectoryBrowserDialog(
            connectionId = selectedConnectionId,
            initialPath = remotePath.ifBlank { "/" },
            onPathSelected = {
                remotePath = it
                showBrowser = false
            },
            onDismiss = { showBrowser = false },
            listDirectories = onListDirectories
        )
    }
}

@Composable
fun RemoteDirectoryBrowserDialog(
    connectionId: String,
    initialPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    listDirectories: (String, String, (Boolean, List<String>, String) -> Unit) -> Unit
) {
    var currentPath by remember { mutableStateOf(if (initialPath.endsWith("/")) initialPath else "$initialPath/") }
    var directories by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        isLoading = true
        error = null
        listDirectories(connectionId, currentPath) { success, dirs, msg ->
            isLoading = false
            if (success) {
                directories = dirs.sorted()
            } else {
                error = msg
            }
        }
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text("选择远程目录") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)) {
                Text("当前路径: $currentPath", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (error != null) {
                    Text("加载失败: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn {
                        if (currentPath != "/") {
                            item {
                                TextButton(onClick = {
                                    val parent = currentPath.trimEnd('/').substringBeforeLast('/')
                                    currentPath = if (parent.isEmpty()) "/" else "$parent/"
                                }) {
                                    Icon(FeatherIcons.Folder, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(".. (上一级)")
                                }
                            }
                        }
                        items(directories) { dir ->
                            TextButton(onClick = {
                                currentPath = if (currentPath == "/") "/$dir/" else "$currentPath$dir/"
                            }) {
                                Icon(FeatherIcons.Folder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(dir)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPathSelected(currentPath) }) {
                Text("确认选择")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
