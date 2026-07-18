package com.aicode.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.util.LogLevel
import com.aicode.feature.agent.domain.mcp.McpServerConfig
import com.aicode.feature.agent.domain.mcp.McpServerStatus
import com.aicode.feature.settings.data.repository.AppThemeMode
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.presentation.SettingsViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

/** 设置页内部二级菜单分区。Menu 为首页菜单，其余为各自的二级页。 */
internal enum class SettingsSection(val title: String) {
    Menu("设置"),
    Providers("AI 提供商"),
    ProviderEditor("提供商"),
    VisionModel("识图模型"),
    Mcp("MCP 服务器"),
    Container("容器镜像"),
    Log("日志等级"),
    LogViewer("日志查看"),
    Permissions("工具授权"),
    RemoteServers("远程工作区"),
    About("关于")
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
    val logViewerState by viewModel.logViewerState.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val mcpStatuses by viewModel.mcpStatuses.collectAsStateWithLifecycle()
    val mcpReloading by viewModel.mcpReloading.collectAsStateWithLifecycle()
    val globalRules by viewModel.globalRules.collectAsStateWithLifecycle()
    val projectRules by viewModel.projectRules.collectAsStateWithLifecycle()
    val keepaliveEnabled by viewModel.keepaliveEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val visionProviderId by viewModel.visionProviderId.collectAsStateWithLifecycle()
    val visionModel by viewModel.visionModel.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val containerProfiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(SettingsSection.Menu) }
    var logReturnSection by remember { mutableStateOf(SettingsSection.Menu) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }

    // 处于二级页时，系统返回键先回到上一层；首页时交还给上层导航。
    BackHandler(enabled = section != SettingsSection.Menu) {
        when (section) {
            SettingsSection.ProviderEditor -> section = SettingsSection.Providers
            SettingsSection.LogViewer -> section = logReturnSection
            else -> section = SettingsSection.Menu
        }
    }

    // 提供商编辑为独立全屏页，直接渲染（不嵌套 Scaffold）
    if (section == SettingsSection.ProviderEditor) {
        ProviderEditorScreen(
            viewModel = viewModel,
            initialProvider = editingProvider,
            onNavigateBack = { section = SettingsSection.Providers },
            onSave = { provider ->
                viewModel.saveProvider(provider)
            },
            onDelete = { id ->
                viewModel.deleteProvider(id)
                section = SettingsSection.Providers
            }
        )
        return
    }

    if (section == SettingsSection.RemoteServers) {
        com.aicode.feature.workspace.presentation.remote.RemoteServerScreen(
            onNavigateBack = { section = SettingsSection.Menu }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (section == SettingsSection.Menu) {
                            onNavigateBack()
                        } else if (section == SettingsSection.LogViewer) {
                            section = logReturnSection
                        } else {
                            section = SettingsSection.Menu
                        }
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
                            Icon(FeatherIcons.Plus, contentDescription = "添加提供商")
                        }
                        SettingsSection.Mcp -> {
                            IconButton(onClick = { viewModel.reloadMcp() }) {
                                if (mcpReloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(FeatherIcons.RefreshCw, contentDescription = "重新连接")
                                }
                            }
                            IconButton(onClick = {
                                editingMcp = null
                                showMcpDialog = true
                            }) {
                                Icon(FeatherIcons.Plus, contentDescription = "添加 MCP 服务器")
                            }
                        }
                        SettingsSection.LogViewer -> {
                            IconButton(onClick = { viewModel.refreshLogs() }) {
                                Icon(FeatherIcons.RefreshCw, contentDescription = "刷新日志")
                            }
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
                    activeContainerProfileName = containerProfiles.firstOrNull { it.id == activeProfileId }?.name,
                    visionProviderName = providers.firstOrNull { it.id == visionProviderId }?.name,
                    visionModel = visionModel,
                    mcpCount = mcpServers.size,
                    mcpConnected = mcpStatuses.count { it.state == McpServerStatus.State.CONNECTED },
                    logLevel = logLevel,
                    permissionRuleCount = projectRules.size + globalRules.size,
                    themeMode = themeMode,
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    keepaliveEnabled = keepaliveEnabled,
                    onToggleKeepalive = { viewModel.setKeepaliveEnabled(it) },
                    onOpen = {
                        if (it == SettingsSection.LogViewer) {
                            logReturnSection = SettingsSection.Menu
                            viewModel.refreshLogs(filterServerName = null)
                        }
                        section = it
                    }
                )
                SettingsSection.Providers -> ProvidersSection(
                    providers = providers,
                    onToggle = { id, enabled -> viewModel.setProviderEnabled(id, enabled) },
                    onEdit = {
                        editingProvider = it
                        section = SettingsSection.ProviderEditor
                    }
                )
                SettingsSection.VisionModel -> VisionModelSection(
                    providers = providers,
                    visionProviderId = visionProviderId,
                    visionModel = visionModel,
                    modelMetadata = modelMetadata,
                    onLoadMetadata = { viewModel.loadAllModelMetadata() },
                    onSelect = { pid, m -> viewModel.setVisionModel(pid, m) },
                    onClear = { viewModel.clearVisionModel() }
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
                SettingsSection.Container -> ContainerSection(
                    profiles = containerProfiles,
                    activeProfileId = activeProfileId,
                    onSelect = { viewModel.setActiveContainerProfile(it) },
                    onSaveCustom = { viewModel.saveCustomContainerProfile(it) },
                    onEditCustom = { viewModel.editCustomContainerProfile(it) },
                    onDeleteCustom = { viewModel.deleteCustomContainerProfile(it) }
                )
                SettingsSection.Log -> LogSection(
                    current = logLevel,
                    onSelect = { viewModel.setLogLevel(it) }
                )
                SettingsSection.LogViewer -> LogViewerSection(
                    state = logViewerState,
                    onSelectFile = { viewModel.selectLogFile(it) }
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
                SettingsSection.About -> AboutSection()
            }
        }
    }

    if (showMcpDialog) {
        McpServerEditDialog(
            initial = editingMcp,
            tools = viewModel.getMcpServerTools(editingMcp?.name),
            onRefreshTools = { viewModel.reloadMcp() },
            onOpenLogs = editingMcp?.let { existing ->
                {
                    showMcpDialog = false
                    logReturnSection = SettingsSection.Mcp
                    viewModel.refreshLogs(filterServerName = existing.name)
                    section = SettingsSection.LogViewer
                }
            },
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
internal fun SettingsMenu(
    providerCount: Int,
    activeProviderName: String?,
    activeContainerProfileName: String?,
    visionProviderName: String?,
    visionModel: String,
    mcpCount: Int,
    mcpConnected: Int,
    logLevel: LogLevel,
    permissionRuleCount: Int,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    keepaliveEnabled: Boolean,
    onToggleKeepalive: (Boolean) -> Unit,
    onOpen: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MenuRow(
            icon = FeatherIcons.Cloud,
            title = SettingsSection.Providers.title,
            subtitle = if (providerCount == 0) {
                "未添加提供商"
            } else {
                "$providerCount 个" + (activeProviderName?.let { " · 启用：$it" } ?: "")
            },
            onClick = { onOpen(SettingsSection.Providers) }
        )
        MenuRow(
            icon = FeatherIcons.Image,
            title = SettingsSection.VisionModel.title,
            subtitle = if (visionProviderName.isNullOrBlank() || visionModel.isBlank()) {
                "跟随当前聊天模型"
            } else {
                "专用：$visionProviderName · $visionModel"
            },
            onClick = { onOpen(SettingsSection.VisionModel) }
        )
        MenuRow(
            icon = FeatherIcons.Box,
            title = SettingsSection.Mcp.title,
            subtitle = if (mcpCount == 0) "未配置 MCP 服务器" else "$mcpCount 个 · 已连接 $mcpConnected",
            onClick = { onOpen(SettingsSection.Mcp) }
        )
        MenuRow(
            icon = FeatherIcons.HardDrive,
            title = SettingsSection.Container.title,
            subtitle = "当前：${activeContainerProfileName ?: "内置 Alpine"}",
            onClick = { onOpen(SettingsSection.Container) }
        )
        MenuRow(
            icon = FeatherIcons.FileText,
            title = SettingsSection.Log.title,
            subtitle = "当前：${logLevel.name}",
            onClick = { onOpen(SettingsSection.Log) }
        )
        MenuRow(
            icon = FeatherIcons.FileText,
            title = SettingsSection.LogViewer.title,
            subtitle = "查看最近日志，支持 MCP 名称过滤",
            onClick = { onOpen(SettingsSection.LogViewer) }
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

        ThemeModeRow(
            icon = FeatherIcons.Moon,
            title = "外观主题",
            subtitle = "选择跟随系统或固定明暗模式",
            selected = themeMode,
            onSelected = onThemeModeChange
        )
        SwitchRow(
            icon = FeatherIcons.RefreshCw,
            title = "后台运行保活",
            subtitle = "显示前台通知，避免退到后台时系统杀进程",
            checked = keepaliveEnabled,
            onCheckedChange = onToggleKeepalive
        )
        MenuRow(
            icon = FeatherIcons.Info,
            title = SettingsSection.About.title,
            subtitle = "版本检查 · GitHub · 许可证",
            onClick = { onOpen(SettingsSection.About) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selected.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .width(124.dp)
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                onSelected(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/** 二级菜单入口行：图标 + 标题 + 摘要 + 右箭头。 */
@Composable
internal fun MenuRow(
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
