package com.aicodeeditor.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.util.LogLevel
import com.aicodeeditor.feature.agent.domain.mcp.McpServerConfig
import com.aicodeeditor.feature.agent.domain.mcp.McpServerStatus
import com.aicodeeditor.feature.agent.domain.permission.PermissionDecision
import com.aicodeeditor.feature.agent.domain.permission.PermissionRule
import com.aicodeeditor.feature.settings.data.remote.ModelTestResult
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.presentation.FetchState
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel

/** 设置页内部二级菜单分区。Menu 为首页菜单，其余为各自的二级页。 */
private enum class SettingsSection(val title: String) {
    Menu("设置"),
    Providers("AI 服务商"),
    Mcp("MCP 服务器"),
    Log("日志等级"),
    Permissions("工具授权")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()
    val mcpStatuses by viewModel.mcpStatuses.collectAsState()
    val mcpReloading by viewModel.mcpReloading.collectAsState()
    val globalRules by viewModel.globalRules.collectAsState()
    val projectRules by viewModel.projectRules.collectAsState()

    var section by remember { mutableStateOf(SettingsSection.Menu) }

    var showProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }

    // 处于二级页时，系统返回键先回到菜单首页；首页时才交还给上层导航。
    BackHandler(enabled = section != SettingsSection.Menu) {
        section = SettingsSection.Menu
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (section == SettingsSection.Menu) onNavigateBack() else section = SettingsSection.Menu
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    when (section) {
                        SettingsSection.Providers -> IconButton(onClick = {
                            editingProvider = null
                            showProviderDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加服务商")
                        }
                        SettingsSection.Mcp -> IconButton(onClick = {
                            editingMcp = null
                            showMcpDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加 MCP 服务器")
                        }
                        else -> {}
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (section) {
                SettingsSection.Menu -> SettingsMenu(
                    providerCount = providers.size,
                    activeProviderName = activeProvider?.name,
                    mcpCount = mcpServers.size,
                    mcpConnected = mcpStatuses.count { it.state == McpServerStatus.State.CONNECTED },
                    logLevel = logLevel,
                    permissionRuleCount = projectRules.size + globalRules.size,
                    onOpen = { section = it }
                )
                SettingsSection.Providers -> ProvidersSection(
                    providers = providers,
                    activeProvider = activeProvider,
                    onActivate = { viewModel.setActiveProvider(it) },
                    onEdit = {
                        editingProvider = it
                        showProviderDialog = true
                    }
                )
                SettingsSection.Mcp -> McpSection(
                    servers = mcpServers,
                    statuses = mcpStatuses,
                    reloading = mcpReloading,
                    onReload = { viewModel.reloadMcp() },
                    onToggle = { name, enabled -> viewModel.setMcpServerEnabled(name, enabled) },
                    onEdit = {
                        editingMcp = it
                        showMcpDialog = true
                    },
                    onDelete = { viewModel.deleteMcpServer(it) }
                )
                SettingsSection.Log -> LogSection(
                    current = logLevel,
                    onSelect = { viewModel.setLogLevel(it) }
                )
                SettingsSection.Permissions -> PermissionsSection(
                    projectName = viewModel.currentProjectName,
                    projectRules = projectRules,
                    globalRules = globalRules,
                    onDeleteProject = { viewModel.deleteProjectRule(it) },
                    onPromote = { viewModel.promoteRuleToGlobal(it) },
                    onDeleteGlobal = { viewModel.deleteGlobalRule(it) }
                )
            }
        }
    }

    if (showProviderDialog) {
        ProviderEditDialog(
            viewModel = viewModel,
            initialProvider = editingProvider,
            onDismiss = { showProviderDialog = false },
            onSave = { provider ->
                viewModel.saveProvider(provider)
                showProviderDialog = false
            },
            onDelete = { id ->
                viewModel.deleteProvider(id)
                showProviderDialog = false
            }
        )
    }

    if (showMcpDialog) {
        McpServerEditDialog(
            initial = editingMcp,
            onDismiss = { showMcpDialog = false },
            onSave = { config ->
                viewModel.upsertMcpServer(editingMcp?.name, config)
                showMcpDialog = false
            },
            onDelete = editingMcp?.let { existing ->
                {
                    viewModel.deleteMcpServer(existing.name)
                    showMcpDialog = false
                }
            }
        )
    }
}

/** 设置首页：每个分区一个可点击的二级菜单入口。 */
@Composable
private fun SettingsMenu(
    providerCount: Int,
    activeProviderName: String?,
    mcpCount: Int,
    mcpConnected: Int,
    logLevel: LogLevel,
    permissionRuleCount: Int,
    onOpen: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MenuRow(
            icon = Icons.Default.Cloud,
            title = SettingsSection.Providers.title,
            subtitle = if (providerCount == 0) {
                "未添加服务商"
            } else {
                "$providerCount 个" + (activeProviderName?.let { " · 启用：$it" } ?: "")
            },
            onClick = { onOpen(SettingsSection.Providers) }
        )
        MenuRow(
            icon = Icons.Default.Extension,
            title = SettingsSection.Mcp.title,
            subtitle = if (mcpCount == 0) "未配置 MCP 服务器" else "$mcpCount 个 · 已连接 $mcpConnected",
            onClick = { onOpen(SettingsSection.Mcp) }
        )
        MenuRow(
            icon = Icons.Default.Article,
            title = SettingsSection.Log.title,
            subtitle = "当前：${logLevel.name}",
            onClick = { onOpen(SettingsSection.Log) }
        )
        MenuRow(
            icon = Icons.Default.Lock,
            title = SettingsSection.Permissions.title,
            subtitle = if (permissionRuleCount == 0) "未保存授权规则" else "已保存 $permissionRuleCount 条",
            onClick = { onOpen(SettingsSection.Permissions) }
        )
    }
}

/** 二级菜单入口行：图标 + 标题 + 摘要 + 右箭头。 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 服务商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditDialog]。 */
@Composable
private fun ProvidersSection(
    providers: List<AIProviderConfig>,
    activeProvider: AIProviderConfig?,
    onActivate: (String) -> Unit,
    onEdit: (AIProviderConfig) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyHint("还没有服务商，点右上角 + 添加")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        items(providers) { provider ->
            ProviderItem(
                provider = provider,
                isActive = activeProvider?.id == provider.id,
                onActivate = { onActivate(provider.id) },
                onEdit = { onEdit(provider) }
            )
        }
    }
}

/** 日志等级二级页。 */
@Composable
private fun LogSection(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
    ) {
        LogLevelCard(current = current, onSelect = onSelect)
    }
}

/**
 * 「工具授权」二级页：列出当前项目与全局已保存的授权规则，可逐条删除；项目规则可「提升为全局」。
 * 规则由授权弹窗的「始终允许」写入（默认进当前项目）。
 */
@Composable
private fun PermissionsSection(
    projectName: String?,
    projectRules: List<PermissionRule>,
    globalRules: List<PermissionRule>,
    onDeleteProject: (PermissionRule) -> Unit,
    onPromote: (PermissionRule) -> Unit,
    onDeleteGlobal: (PermissionRule) -> Unit
) {
    if (projectRules.isEmpty() && globalRules.isEmpty()) {
        EmptyHint("还没有已保存的授权规则。\n在 AI 请求执行命令时选「始终允许」即可记住。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                text = "「始终允许」记住的规则。命令按程序名前缀匹配（如 git 命中 git status / git push）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { RuleGroupHeader(if (projectName != null) "当前项目：$projectName" else "当前项目（未选择）") }
        if (projectRules.isEmpty()) {
            item { RuleEmptyHint("本项目暂无规则") }
        } else {
            items(projectRules) { rule ->
                RuleRow(rule = rule, onDelete = { onDeleteProject(rule) }, onPromote = { onPromote(rule) })
            }
        }

        item { RuleGroupHeader("全局") }
        if (globalRules.isEmpty()) {
            item { RuleEmptyHint("暂无全局规则") }
        } else {
            items(globalRules) { rule ->
                RuleRow(rule = rule, onDelete = { onDeleteGlobal(rule) }, onPromote = null)
            }
        }
    }
}

@Composable
private fun RuleGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

@Composable
private fun RuleEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 单条规则行：匹配模式（主）+ 工具名/判定（次），右侧「提升为全局」(仅项目规则) 与删除。 */
@Composable
private fun RuleRow(
    rule: PermissionRule,
    onDelete: () -> Unit,
    onPromote: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rule.pattern == PermissionRule.WHOLE_TOOL) "整个工具" else rule.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.toolName + " · " + if (rule.decision == PermissionDecision.ALLOW) "允许" else "禁止",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onPromote != null) {
                TextButton(onClick = onPromote) { Text("提升为全局") }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 居中空态提示。 */
@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 日志等级选择卡片：6 个等级单选，选中即持久化并实时生效。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogLevelCard(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "日志等级",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "记录低于该等级的日志将被忽略。开发期建议 VERBOSE（全部记录）；NONE 关闭日志。" +
                    "日志文件位于 Android/data/<包名>/files/logs/。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                LogLevel.values().forEach { level ->
                    FilterChip(
                        selected = level == current,
                        onClick = { onSelect(level) },
                        label = { Text(level.name) }
                    )
                }
            }
        }
    }
}

/**
 * MCP 二级页（可视化）：顶部说明 + 重新连接按钮 + server 列表。
 * 新增/编辑由顶栏「+」与点击行触发 [McpServerEditDialog]，保存即落盘并自动重连。
 */
@Composable
private fun McpSection(
    servers: List<McpServerConfig>,
    statuses: List<McpServerStatus>,
    reloading: Boolean,
    onReload: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (McpServerConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                text = "添加 MCP 服务器：远程 HTTP（含 URL，可选 headers 鉴权）或本地 stdio" +
                    "（在容器内用 command 启动，如 npx；需先在容器装好运行时如 apk add nodejs npm）。" +
                    "保存后会自动连接并把其工具加入 AI 可用工具集。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedButton(
                onClick = onReload,
                enabled = !reloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (reloading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("连接中…")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("重新连接")
                }
            }
        }
        if (servers.isEmpty()) {
            item {
                Text(
                    text = "还没有 MCP 服务器，点右上角 + 添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            }
        } else {
            items(servers) { server ->
                McpServerRow(
                    server = server,
                    status = statuses.firstOrNull { it.name == server.name },
                    onToggle = { onToggle(server.name, it) },
                    onEdit = { onEdit(server) },
                    onDelete = { onDelete(server.name) }
                )
            }
        }
    }
}

/** 单个 MCP server 行：名称/URL + 状态圆点 + 启用开关 + 删除；点击整行进入编辑。 */
@Composable
private fun McpServerRow(
    server: McpServerConfig,
    status: McpServerStatus?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (server.isStdio) {
                            "stdio · " + (listOf(server.command.orEmpty()) + server.args).joinToString(" ").trim()
                        } else {
                            server.url.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = server.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            McpStatusLabel(server = server, status = status)
        }
    }
}

/** server 连接状态：彩色圆点 + 文案（按启用/连接态推导）。 */
@Composable
private fun McpStatusLabel(server: McpServerConfig, status: McpServerStatus?) {
    val (dotColor, label) = when {
        !server.enabled -> MaterialTheme.colorScheme.outline to "已禁用"
        status == null -> MaterialTheme.colorScheme.outline to "未连接"
        else -> when (status.state) {
            McpServerStatus.State.CONNECTED ->
                MaterialTheme.colorScheme.primary to "已连接 · ${status.toolCount} 个工具"
            McpServerStatus.State.CONNECTING ->
                MaterialTheme.colorScheme.tertiary to "连接中…"
            McpServerStatus.State.FAILED ->
                MaterialTheme.colorScheme.error to (status.error ?: "连接失败")
            McpServerStatus.State.DISABLED ->
                MaterialTheme.colorScheme.outline to "已禁用"
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, RoundedCornerShape(Radius.md))
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** MCP server 可视化编辑对话框：类型切换（HTTP / stdio）+ 各自字段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerEditDialog(
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

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

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

    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

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

    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

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

@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (provider.models.isEmpty()) {
                        "未配置模型，点击编辑添加"
                    } else {
                        "模型：${provider.effectiveModel} · 共 ${provider.models.size} 个"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已启用",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.md)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditDialog(
    viewModel: SettingsViewModel,
    initialProvider: AIProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    var selectedModel by remember { mutableStateOf(initialProvider?.effectiveModel ?: "") }
    var newModel by remember { mutableStateOf("") }

    val fetchState by viewModel.fetchState.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val testing by viewModel.testing.collectAsState()

    // 进入/离开对话框时清理临时状态。
    DisposableEffect(Unit) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
        }
    }

    // 构造当前表单对应的临时配置，供拉取/测试使用。
    fun currentConfig() = AIProviderConfig(
        id = initialProvider?.id ?: "temp",
        name = name,
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultBaseUrl(type) },
        defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "" },
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = selectedModel
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProvider == null) "添加服务商" else "编辑服务商") },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = type == ProviderType.OPENAI,
                        onClick = { type = ProviderType.OPENAI },
                        label = { Text("OpenAI") }
                    )
                    FilterChip(
                        selected = type == ProviderType.ANTHROPIC,
                        onClick = { type = ProviderType.ANTHROPIC },
                        label = { Text("Anthropic") }
                    )
                    FilterChip(
                        selected = type == ProviderType.CUSTOM,
                        onClick = { type = ProviderType.CUSTOM },
                        label = { Text("自定义") }
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text(defaultBaseUrl(type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                // ── 模型管理区 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "模型（${models.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.fetchModels(currentConfig()) },
                        enabled = fetchState !is FetchState.Loading
                    ) {
                        if (fetchState is FetchState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Text("拉取")
                    }
                }

                // 拉取结果 / 错误
                when (val fs = fetchState) {
                    is FetchState.Error -> Text(
                        "拉取失败：${fs.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    is FetchState.Success -> {
                        val newOnes = fs.models.filter { it !in models }
                        if (newOnes.isEmpty()) {
                            Text(
                                "已是最新，无新模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "点击添加（${newOnes.size}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowChips(
                                items = newOnes,
                                onClick = { m ->
                                    if (m !in models) models.add(m)
                                    if (selectedModel.isBlank()) selectedModel = m
                                }
                            )
                        }
                    }
                    else -> {}
                }

                // 已添加模型列表
                models.forEach { model ->
                    ModelRow(
                        model = model,
                        isSelected = model == selectedModel,
                        testing = model in testing,
                        result = testResults[model],
                        onSelect = { selectedModel = model },
                        onTest = { viewModel.testModel(currentConfig(), model) },
                        onRemove = {
                            models.remove(model)
                            if (selectedModel == model) selectedModel = models.firstOrNull() ?: ""
                        }
                    )
                }

                // 手动添加
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = newModel,
                        onValueChange = { newModel = it },
                        label = { Text("手动添加模型") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val m = newModel.trim()
                            if (m.isNotEmpty() && m !in models) {
                                models.add(m)
                                if (selectedModel.isBlank()) selectedModel = m
                            }
                            newModel = ""
                        },
                        enabled = newModel.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AIProviderConfig(
                            id = initialProvider?.id ?: System.currentTimeMillis().toString(),
                            name = name.ifEmpty { "新服务商" },
                            type = type,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { defaultBaseUrl(type) },
                            defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "" },
                            isActive = initialProvider?.isActive ?: false,
                            models = models.toList(),
                            selectedModel = selectedModel.ifBlank { models.firstOrNull() ?: "" }
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (initialProvider != null) {
                    TextButton(
                        onClick = { onDelete(initialProvider.id) },
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

@Composable
private fun ModelRow(
    model: String,
    isSelected: Boolean,
    testing: Boolean,
    result: ModelTestResult?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "当前选中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    model,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 测试按钮
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                        Text("测试", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // 测试结果
            result?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (r.success) Icons.Default.Check else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 可点击添加的模型胶囊流式布局（简易换行）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, onClick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onClick(item) },
                label = { Text(item, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    else -> "https://api.openai.com/"
}
