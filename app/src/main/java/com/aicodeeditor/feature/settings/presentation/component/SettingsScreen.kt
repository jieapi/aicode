package com.aicodeeditor.feature.settings.presentation.component

import compose.icons.FeatherIcons
import compose.icons.feathericons.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel

/** 设置页内部二级菜单分区。Menu 为首页菜单，其余为各自的二级页。 */
private enum class SettingsSection(val title: String) {
    Menu("设置"),
    Providers("AI 服务商"),
    ProviderEditor("服务商"),
    Mcp("MCP 服务器"),
    Log("日志等级"),
    Permissions("工具授权"),
    RemoteServers("远程工作区")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val mcpStatuses by viewModel.mcpStatuses.collectAsStateWithLifecycle()
    val mcpReloading by viewModel.mcpReloading.collectAsStateWithLifecycle()
    val globalRules by viewModel.globalRules.collectAsStateWithLifecycle()
    val projectRules by viewModel.projectRules.collectAsStateWithLifecycle()
    val keepaliveEnabled by viewModel.keepaliveEnabled.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(SettingsSection.Menu) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }

    // 处于二级页时，系统返回键先回到上一层；首页时交还给上层导航。
    BackHandler(enabled = section != SettingsSection.Menu) {
        when (section) {
            SettingsSection.ProviderEditor -> section = SettingsSection.Providers
            else -> section = SettingsSection.Menu
        }
    }

    // 服务商编辑为独立全屏页，直接渲染（不嵌套 Scaffold）
    if (section == SettingsSection.ProviderEditor) {
        ProviderEditorScreen(
            viewModel = viewModel,
            initialProvider = editingProvider,
            onNavigateBack = { section = SettingsSection.Providers },
            onSave = { provider ->
                viewModel.saveProvider(provider)
                section = SettingsSection.Providers
            },
            onDelete = { id ->
                viewModel.deleteProvider(id)
                section = SettingsSection.Providers
            }
        )
        return
    }

    if (section == SettingsSection.RemoteServers) {
        com.aicodeeditor.feature.workspace.presentation.remote.RemoteServerScreen(
            onNavigateBack = { section = SettingsSection.Menu }
        )
        return
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (section == SettingsSection.Menu) onNavigateBack() else section = SettingsSection.Menu
                    }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    when (section) {
                        SettingsSection.Providers -> IconButton(onClick = {
                            editingProvider = null
                            section = SettingsSection.ProviderEditor
                        }) {
                            Icon(FeatherIcons.Plus, contentDescription = "添加服务商")
                        }
                        SettingsSection.Mcp -> IconButton(onClick = {
                            editingMcp = null
                            showMcpDialog = true
                        }) {
                            Icon(FeatherIcons.Plus, contentDescription = "添加 MCP 服务器")
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
                    keepaliveEnabled = keepaliveEnabled,
                    onToggleKeepalive = { viewModel.setKeepaliveEnabled(it) },
                    onOpen = { section = it }
                )
                SettingsSection.Providers -> ProvidersSection(
                    providers = providers,
                    onToggle = { id, enabled -> viewModel.setProviderEnabled(id, enabled) },
                    onEdit = {
                        editingProvider = it
                        section = SettingsSection.ProviderEditor
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
                SettingsSection.ProviderEditor -> {} // 已在上方 early return 处理
                SettingsSection.RemoteServers -> {} // 已在上方 early return 处理
            }
        }
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
    keepaliveEnabled: Boolean,
    onToggleKeepalive: (Boolean) -> Unit,
    onOpen: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MenuRow(
            icon = FeatherIcons.Cpu,
            title = SettingsSection.Providers.title,
            subtitle = if (providerCount == 0) {
                "未添加服务商"
            } else {
                "$providerCount 个" + (activeProviderName?.let { " · 启用：$it" } ?: "")
            },
            onClick = { onOpen(SettingsSection.Providers) }
        )
        MenuRow(
            icon = FeatherIcons.Box,
            title = SettingsSection.Mcp.title,
            subtitle = if (mcpCount == 0) "未配置 MCP 服务器" else "$mcpCount 个 · 已连接 $mcpConnected",
            onClick = { onOpen(SettingsSection.Mcp) }
        )
        MenuRow(
            icon = FeatherIcons.FileText,
            title = SettingsSection.Log.title,
            subtitle = "当前：${logLevel.name}",
            onClick = { onOpen(SettingsSection.Log) }
        )
        MenuRow(
            icon = FeatherIcons.Lock,
            title = SettingsSection.Permissions.title,
            subtitle = if (permissionRuleCount == 0) "未保存授权规则" else "已保存 $permissionRuleCount 条",
            onClick = { onOpen(SettingsSection.Permissions) }
        )
        MenuRow(
            icon = FeatherIcons.Server,
            title = SettingsSection.RemoteServers.title,
            subtitle = "管理 SFTP / FTP 工作区同步",
            onClick = { onOpen(SettingsSection.RemoteServers) }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEAEAEA))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FeatherIcons.RefreshCw,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF424242),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "后台运行保活",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "显示前台通知，避免退到后台时系统杀进程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = keepaliveEnabled,
                    onCheckedChange = onToggleKeepalive
                )
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEAEAEA))
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
                tint = androidx.compose.ui.graphics.Color(0xFF424242),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF424242))
        }
    }
}

/** 服务商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditorScreen]。 */
@Composable
private fun ProvidersSection(
    providers: List<AIProviderConfig>,
    onToggle: (String, Boolean) -> Unit,
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
                onToggle = { onToggle(provider.id, it) },
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
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEAEAEA))
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
                Icon(FeatherIcons.Trash2, contentDescription = "删除", tint = androidx.compose.ui.graphics.Color(0xFF424242))
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
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEAEAEA))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "日志等级",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
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
                    Icon(FeatherIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(18.dp))
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
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEAEAEA))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
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
                        FeatherIcons.Trash2,
                        contentDescription = "删除",
                        tint = androidx.compose.ui.graphics.Color(0xFF424242))
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

@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.White
        ),
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Color(0xFFEAEAEA)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
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
            Switch(
                checked = provider.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(end = Spacing.md)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    FeatherIcons.Edit2,
                    contentDescription = "编辑",
                    tint = androidx.compose.ui.graphics.Color(0xFF424242))
            }
        }
    }
}
