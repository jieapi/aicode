package com.aicode.feature.workspace.presentation.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteMount
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerScreen(
    viewModel: RemoteServerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var showAddMountDialog by remember { mutableStateOf(false) }
    var connectionToEdit by remember { mutableStateOf<RemoteConnection?>(null) }
    var mountToEdit by remember { mutableStateOf<RemoteMount?>(null) }

    val syncIgnoredPatterns by viewModel.syncIgnoredPatterns.collectAsStateWithLifecycle()
    val syncUseGitIgnore by viewModel.syncUseGitIgnore.collectAsStateWithLifecycle()
    val maxSyncBatchSize by viewModel.maxSyncBatchSize.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text("远程工作区") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("连接配置") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("工作区") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("内置FTP") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("同步设置") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(onClick = {
                    if (selectedTab == 0) {
                        connectionToEdit = null
                        showAddConnectionDialog = true
                    } else {
                        mountToEdit = null
                        showAddMountDialog = true
                    }
                }) {
                    Icon(FeatherIcons.Plus, contentDescription = "添加")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (selectedTab == 0) {
                if (uiState.connections.isEmpty()) {
                    Text(
                        text = "暂无连接配置，请点击添加。",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.connections) { conn ->
                            RemoteConnectionCard(
                                conn = conn,
                                onEdit = {
                                    connectionToEdit = it
                                    showAddConnectionDialog = true
                                },
                                onDelete = { viewModel.deleteConnection(it.id) }
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                if (uiState.mounts.isEmpty()) {
                    Text(
                        text = "暂无工作区，请点击添加。",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.mounts) { mount ->
                            RemoteMountCard(
                                mount = mount,
                                onEdit = {
                                    mountToEdit = it
                                    showAddMountDialog = true
                                },
                                onDelete = { viewModel.deleteMount(it.id) },
                                onUpload = { viewModel.forceUploadMount(it.id) },
                                onDownload = { viewModel.forceDownloadMount(it.id) },
                                onConnect = { viewModel.connectMount(it.id) },
                                onDisconnect = { viewModel.disconnectMount(it.id) }
                            )
                        }
                    }
                }
            }

            if (selectedTab == 2) {
                WiFiFtpServerSection(viewModel)
            } else if (selectedTab == 3) {
                SyncSettingsSection(
                    ignoredPatterns = syncIgnoredPatterns,
                    useGitIgnore = syncUseGitIgnore,
                    maxSyncBatchSize = maxSyncBatchSize,
                    onPatternsChange = { viewModel.setSyncIgnoredPatterns(it) },
                    onUseGitIgnoreChange = { viewModel.setSyncUseGitIgnore(it) },
                    onMaxSyncBatchSizeChange = { viewModel.setMaxSyncBatchSize(it) }
                )
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

    if (showAddConnectionDialog) {
        AddRemoteConnectionDialog(
            initialConnection = connectionToEdit,
            onDismiss = { showAddConnectionDialog = false },
            onAdd = { name, host, port, username, password, protocol ->
                if (connectionToEdit != null) {
                    viewModel.updateConnection(connectionToEdit!!.id, name, host, port, username, password, protocol)
                } else {
                    viewModel.addConnection(name, host, port, username, password, protocol)
                }
                showAddConnectionDialog = false
            },
            onTestConnection = { host, port, username, password, protocol, onResult ->
                viewModel.testConnection(host, port, username, password, protocol, onResult)
            }
        )
    }

    if (showAddMountDialog) {
        if (uiState.connections.isEmpty()) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                onDismissRequest = { showAddMountDialog = false },
                title = { Text("提示") },
                text = { Text("请先在「连接配置」中添加一个远程通道。") },
                confirmButton = {
                    TextButton(onClick = { showAddMountDialog = false; selectedTab = 0 }) {
                        Text("去添加")
                    }
                }
            )
        } else {
            AddRemoteMountDialog(
                initialMount = mountToEdit,
                connections = uiState.connections,
                workspaces = uiState.workspaces,
                onDismiss = { showAddMountDialog = false },
                onAdd = { connectionId, remotePath, localWorkspacePath, autoConnect ->
                    if (mountToEdit != null) {
                        viewModel.updateMount(mountToEdit!!.id, connectionId, remotePath, localWorkspacePath, autoConnect)
                    } else {
                        viewModel.addMount(connectionId, remotePath, localWorkspacePath, autoConnect)
                    }
                    showAddMountDialog = false
                },
                onListDirectories = { connectionId, path, onResult ->
                    viewModel.listRemoteDirectories(connectionId, path, onResult)
                }
            )
        }
    }

}
