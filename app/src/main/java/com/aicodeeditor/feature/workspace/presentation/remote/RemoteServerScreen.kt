package com.aicodeeditor.feature.workspace.presentation.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicodeeditor.feature.workspace.domain.model.RemoteConnection
import com.aicodeeditor.feature.workspace.domain.model.RemoteMount
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import com.aicodeeditor.core.theme.Brand
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
        containerColor = Brand.PageBg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.PageBg),
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
                    // Update existing
                    viewModel.updateConnection(connectionToEdit!!.id, name, host, port, username, password, protocol)
                } else {
                    // Add new
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
        containerColor = Color.White,
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

@Composable
fun SyncSettingsSection(
    ignoredPatterns: String,
    useGitIgnore: Boolean,
    maxSyncBatchSize: Int,
    onPatternsChange: (String) -> Unit,
    onUseGitIgnoreChange: (Boolean) -> Unit,
    onMaxSyncBatchSizeChange: (Int) -> Unit
) {
    var patternsText by remember(ignoredPatterns) { mutableStateOf(ignoredPatterns) }
    var maxBatchSizeText by remember(maxSyncBatchSize) { mutableStateOf(maxSyncBatchSize.toString()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.FileText,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "目录/文件忽略列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "多个规则请用英文逗号分隔。这些名称的目录及文件将不会被同步到远程服务器（如 node_modules）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = patternsText,
                    onValueChange = { patternsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("忽略规则") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onPatternsChange(patternsText)
                        android.widget.Toast.makeText(context, "忽略规则已保存", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存规则")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        FeatherIcons.CheckSquare,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "遵循 .gitignore 规则",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "如果项目根目录存在 .gitignore，也会自动读取并忽略其中的文件与目录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Switch(
                    checked = useGitIgnore,
                    onCheckedChange = {
                        onUseGitIgnoreChange(it)
                        android.widget.Toast.makeText(context, if (it) "已开启 .gitignore 过滤" else "已关闭 .gitignore 过滤", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Layers,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "单次最大同步队列数量",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "防止突发性大量文件修改同时请求服务端导致断开连接。超过此数量的操作将排队按批次执行（批次间稍作停顿）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = maxBatchSizeText,
                    onValueChange = { maxBatchSizeText = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最大数量") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val size = maxBatchSizeText.toIntOrNull() ?: 50
                        onMaxSyncBatchSizeChange(size)
                        android.widget.Toast.makeText(context, "最大队列数量已保存", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存数量")
                }
            }
        }
    }
}

@Composable
fun RemoteConnectionCard(
    conn: RemoteConnection,
    onEdit: (RemoteConnection) -> Unit,
    onDelete: (RemoteConnection) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Cloud,
                        contentDescription = null,
                        tint = Brand.IconGray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = conn.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "${conn.protocol}://${conn.username}@${conn.host}:${conn.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row {
                    IconButton(onClick = { onEdit(conn) }) {
                        Icon(FeatherIcons.Edit2, contentDescription = "编辑", tint = Brand.IconGray)
                    }
                    IconButton(onClick = { onDelete(conn) }) {
                        Icon(FeatherIcons.Trash2, contentDescription = "删除", tint = Brand.IconGray)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteMountCard(
    mount: RemoteMount,
    onEdit: (RemoteMount) -> Unit,
    onDelete: (RemoteMount) -> Unit,
    onUpload: (RemoteMount) -> Unit,
    onDownload: (RemoteMount) -> Unit,
    onConnect: (RemoteMount) -> Unit,
    onDisconnect: (RemoteMount) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Folder,
                        contentDescription = null,
                        tint = Brand.IconGray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "通过: ${mount.connection?.name ?: "未知连接"}", fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        if (mount.autoConnect) {
                            Text(text = "自动连接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Row {
                    IconButton(onClick = { onEdit(mount) }) {
                        Icon(FeatherIcons.Edit2, contentDescription = "编辑", tint = Brand.IconGray)
                    }
                    IconButton(onClick = { onDelete(mount) }) {
                        Icon(FeatherIcons.Trash2, contentDescription = "删除", tint = Brand.IconGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "远程路径: ${mount.remotePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "本地路径: ${mount.localMountPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (mount.isActive) {
                    TextButton(onClick = { onDisconnect(mount) }) {
                        Text("断开连接")
                    }
                    TextButton(onClick = { onUpload(mount) }) {
                        Text("上传全部")
                    }
                    TextButton(onClick = { onDownload(mount) }) {
                        Text("下载全部")
                    }
                } else {
                    Button(onClick = { onConnect(mount) }) {
                        Text("连接并同步")
                    }
                }
            }
        }
    }
}

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
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(if (initialConnection != null) "编辑连接通道" else "添加连接通道", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
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
                            Icon(image, "切换密码可见性", tint = Brand.IconGray)
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
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(if (initialMount != null) "编辑工作区" else "添加工作区", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
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
                        modifier = Modifier.background(Color.White)
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
                        Icon(FeatherIcons.Folder, contentDescription = "浏览目录", tint = Brand.IconGray)
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
                        modifier = Modifier.background(Color.White)
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
        containerColor = Color.White,
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

@Composable
fun WiFiFtpServerSection(viewModel: RemoteServerViewModel) {
    val isRunning by viewModel.ftpServerManager.isRunning.collectAsStateWithLifecycle()
    val serverUrl by viewModel.ftpServerManager.serverUrl.collectAsStateWithLifecycle()
    val port by viewModel.ftpServerManager.port.collectAsStateWithLifecycle()
    val username by viewModel.ftpServerManager.username.collectAsStateWithLifecycle()
    val password by viewModel.ftpServerManager.password.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.ftpServerManager.isAnonymous.collectAsStateWithLifecycle()
    val autoStart by viewModel.ftpServerManager.autoStart.collectAsStateWithLifecycle()
    val errorMessage by viewModel.ftpServerManager.errorMessage.collectAsStateWithLifecycle()

    var editPort by remember(port) { mutableStateOf(port.toString()) }
    var editUsername by remember(username) { mutableStateOf(username) }
    var editPassword by remember(password) { mutableStateOf(password) }
    var editAnonymous by remember(isAnonymous) { mutableStateOf(isAnonymous) }
    var editAutoStart by remember(autoStart) { mutableStateOf(autoStart) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    FeatherIcons.Info,
                    contentDescription = null,
                    tint = Brand.IconGray,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1. 手机与电脑需连接至同一 WiFi 局域网。\n2. 开启服务后，在电脑的文件资源管理器（或 FileZilla）地址栏访问下方 FTP 地址。\n3. 连接成功后，即可在电脑端直接查看并编辑手机工作区根目录（projects）下的全部代码项目。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            FeatherIcons.Share2,
                            contentDescription = null,
                            tint = if (isRunning) androidx.compose.ui.graphics.Color(0xFF4CAF50) else Brand.IconGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "FTP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRunning) "运行中: $serverUrl" else "服务未开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRunning) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { viewModel.toggleFtpServer() }
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "错误: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Settings,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "服务参数配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = editPort,
                    onValueChange = { editPort = it.filter { char -> char.isDigit() } },
                    label = { Text("监听端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    label = { Text("登录用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous
                )

                OutlinedTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    label = { Text("登录密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "允许匿名访问", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "无需输入用户名和密码即可连接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAnonymous, onCheckedChange = { editAnonymous = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "开机自启", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "应用启动时后台自动运行 FTP 服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAutoStart, onCheckedChange = { editAutoStart = it })
                }

                Button(
                    onClick = {
                        val p = editPort.toIntOrNull() ?: 2121
                        viewModel.saveFtpServerConfig(p, editUsername, editPassword, editAnonymous, editAutoStart)
                        android.widget.Toast.makeText(context, "配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}
