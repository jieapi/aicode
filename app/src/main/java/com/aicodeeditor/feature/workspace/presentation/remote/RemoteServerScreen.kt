package com.aicodeeditor.feature.workspace.presentation.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import com.aicodeeditor.feature.workspace.domain.model.RemoteServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerScreen(
    viewModel: RemoteServerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程工作区") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加工作区")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.servers.isEmpty()) {
                Text(
                    text = "暂无远程工作区，请点击添加。",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.servers) { server ->
                        RemoteServerCard(
                            server = server,
                            onConnect = { viewModel.connectServer(it.id) },
                            onDisconnect = { viewModel.disconnectServer(it.id) },
                            onDelete = { viewModel.deleteServer(it.id) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    if (showAddDialog) {
        AddRemoteServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, host, port, username, password, remotePath, protocol ->
                viewModel.addServer(name, host, port, username, password, remotePath, protocol)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun RemoteServerCard(
    server: RemoteServer,
    onConnect: (RemoteServer) -> Unit,
    onDisconnect: (RemoteServer) -> Unit,
    onDelete: (RemoteServer) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (server.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = server.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(text = "${server.protocol}://${server.username}@${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onDelete(server) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "远程路径: ${server.remotePath}", style = MaterialTheme.typography.bodySmall)
            Text(text = "本地路径: ${server.localMountPath}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(12.dp))

            if (server.isActive) {
                Button(
                    onClick = { onDisconnect(server) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("断开连接并停止同步")
                }
            } else {
                Button(
                    onClick = { onConnect(server) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("连接并同步")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String, RemoteProtocol) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("/home/") }
    var isSftp by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加远程服务器") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("协议:")
                    Spacer(modifier = Modifier.width(8.dp))
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("连接名称 (如: 测试服)") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机地址 (IP或域名)") }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true)
                OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text("远程目标目录 (绝对路径)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val protocol = if (isSftp) RemoteProtocol.SFTP else RemoteProtocol.FTP
                onAdd(name, host, port, username, password, remotePath, protocol)
            }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
