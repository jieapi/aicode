package com.aicode.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.core.util.LogLevel
import com.aicode.feature.agent.domain.container.ContainerInstaller
import com.aicode.feature.agent.domain.container.ContainerProfile
import com.aicode.feature.agent.domain.container.RootfsSource
import com.aicode.feature.agent.domain.mcp.McpConfigRepository
import com.aicode.feature.agent.domain.mcp.McpManager
import com.aicode.feature.agent.domain.mcp.McpServerConfig
import com.aicode.feature.agent.domain.mcp.McpServerStatus
import com.aicode.feature.agent.domain.mcp.McpToolDescriptor
import com.aicode.feature.agent.domain.permission.PermissionRule
import com.aicode.feature.agent.domain.permission.PermissionRulesRepository
import com.aicode.feature.settings.data.remote.ModelApiService
import com.aicode.feature.settings.data.remote.ModelMetadataService
import com.aicode.feature.settings.data.remote.ModelTestResult
import com.aicode.feature.settings.data.repository.AppThemeMode
import com.aicode.feature.settings.data.repository.ContainerSettingsRepository
import com.aicode.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicode.feature.settings.data.repository.LogSettingsRepository
import com.aicode.feature.settings.data.repository.ThemeSettingsRepository
import com.aicode.feature.settings.data.repository.VisionModelSettingsRepository
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class FetchState {
    object Idle : FetchState()
    object Loading : FetchState()
    data class Success(val models: List<String>) : FetchState()
    data class Error(val message: String) : FetchState()
}

data class LogViewerUiState(
    val files: List<String> = emptyList(),
    val selectedFileName: String? = null,
    val filterServerName: String? = null,
    val content: String = "",
    val totalLines: Int = 0,
    val shownLines: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AIProviderRepository,
    private val modelApiService: ModelApiService,
    private val modelMetadataService: ModelMetadataService,
    private val logSettingsRepository: LogSettingsRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val containerSettingsRepository: ContainerSettingsRepository,
    private val containerInstaller: ContainerInstaller
) : ViewModel() {
    private companion object {
        const val MAX_LOG_LINES = 1200
    }

    private val _providers = MutableStateFlow<List<AIProviderConfig>>(emptyList())
    val providers: StateFlow<List<AIProviderConfig>> = _providers.asStateFlow()

    private val _activeProvider = MutableStateFlow<AIProviderConfig?>(null)
    val activeProvider: StateFlow<AIProviderConfig?> = _activeProvider.asStateFlow()

    /** 识图专用模型选择：providerId 为空即「跟随当前聊天模型」。 */
    private val _visionProviderId = MutableStateFlow("")
    val visionProviderId: StateFlow<String> = _visionProviderId.asStateFlow()

    private val _visionModel = MutableStateFlow("")
    val visionModel: StateFlow<String> = _visionModel.asStateFlow()

    private val _logLevel = MutableStateFlow(LogLevel.VERBOSE)
    val logLevel: StateFlow<LogLevel> = _logLevel.asStateFlow()

    private val _logViewerState = MutableStateFlow(LogViewerUiState())
    val logViewerState: StateFlow<LogViewerUiState> = _logViewerState.asStateFlow()

    private val _keepaliveEnabled = MutableStateFlow(false)
    val keepaliveEnabled: StateFlow<Boolean> = _keepaliveEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.AUTO)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers.asStateFlow()

    val mcpStatuses: StateFlow<List<McpServerStatus>> = mcpManager.statuses

    private val _mcpReloading = MutableStateFlow(false)
    val mcpReloading: StateFlow<Boolean> = _mcpReloading.asStateFlow()

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    private val _testResults = MutableStateFlow<Map<String, ModelTestResult>>(emptyMap())
    val testResults: StateFlow<Map<String, ModelTestResult>> = _testResults.asStateFlow()

    private val _modelMetadata = MutableStateFlow<Map<String, ModelMetadata>>(emptyMap())
    val modelMetadata: StateFlow<Map<String, ModelMetadata>> = _modelMetadata.asStateFlow()

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    private val _globalRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val globalRules: StateFlow<List<PermissionRule>> = _globalRules.asStateFlow()

    private val _projectRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val projectRules: StateFlow<List<PermissionRule>> = _projectRules.asStateFlow()

    val currentProjectName: String? = permissionRulesRepository.currentProjectName()

    private val _activeProfileId = MutableStateFlow(ContainerProfile.BUILTIN_ID)
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _customProfiles = MutableStateFlow<List<ContainerProfile>>(emptyList())
    val customProfiles: StateFlow<List<ContainerProfile>> = _customProfiles.asStateFlow()

    /** 全部 profile（内置 + 自定义），供 UI 列出。 */
    val profiles: StateFlow<List<ContainerProfile>> = customProfiles
        .map { listOf(ContainerProfile.BUILTIN_ALPINE) + it }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, listOf(ContainerProfile.BUILTIN_ALPINE))

    init {
        viewModelScope.launch {
            // 启动即保证有激活提供商（若库中存在却无激活项），避免主页模型胶囊因 activeProvider=null 消失。
            repository.ensureActiveProvider()

            launch {
                repository.getAllProviders().collectLatest {
                    _providers.value = it
                    // 运行期兜底：提供商列表变化后若仍无激活项且有提供商，自动激活首个。
                    if (_activeProvider.value == null && it.isNotEmpty()) {
                        repository.ensureActiveProvider()
                    }
                }
            }

            launch {
                repository.getActiveProvider().collectLatest {
                    _activeProvider.value = it
                }
            }

            launch {
                visionModelSettingsRepository.providerIdFlow.collectLatest {
                    _visionProviderId.value = it
                }
            }

            launch {
                visionModelSettingsRepository.modelFlow.collectLatest {
                    _visionModel.value = it
                }
            }

            launch {
                logSettingsRepository.levelFlow.collectLatest {
                    _logLevel.value = it
                }
            }

            launch {
                keepaliveSettingsRepository.enabledFlow.collectLatest {
                    _keepaliveEnabled.value = it
                }
            }

            launch {
                themeSettingsRepository.themeModeFlow.collectLatest {
                    _themeMode.value = it
                }
            }

            launch {
                containerSettingsRepository.activeProfileIdFlow.collectLatest {
                    _activeProfileId.value = it
                }
            }

            launch {
                containerSettingsRepository.customProfilesFlow.collectLatest {
                    _customProfiles.value = it
                }
            }

            launch {
                mcpConfigRepository.serversFlow.collectLatest {
                    _mcpServers.value = it
                }
            }

            launch {
                permissionRulesRepository.globalRulesFlow.collectLatest {
                    _globalRules.value = it
                }
            }

            currentProjectName?.let { name ->
                launch {
                    permissionRulesRepository.projectRulesFlow(name).collectLatest {
                        _projectRules.value = it
                    }
                }
            }
        }
    }

    fun upsertMcpServer(originalName: String?, config: McpServerConfig) {
        viewModelScope.launch {
            val ordered = LinkedHashMap<String, McpServerConfig>()
            _mcpServers.value.forEach { ordered[it.name] = it }
            if (originalName != null && originalName != config.name) {
                ordered.remove(originalName)
            }
            ordered[config.name] = config
            persistMcpServers(ordered.values.toList())
        }
    }

    fun deleteMcpServer(name: String) {
        viewModelScope.launch {
            persistMcpServers(_mcpServers.value.filterNot { it.name == name })
        }
    }

    fun setMcpServerEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            persistMcpServers(_mcpServers.value.map { if (it.name == name) it.copy(enabled = enabled) else it })
        }
    }

    fun reloadMcp() {
        viewModelScope.launch {
            _mcpReloading.value = true
            try {
                mcpManager.reload()
            } finally {
                _mcpReloading.value = false
            }
        }
    }

    fun getMcpServerTools(serverName: String?): List<McpToolDescriptor> {
        if (serverName.isNullOrBlank()) return emptyList()
        return mcpManager.getServerTools(serverName)
    }

    private suspend fun persistMcpServers(servers: List<McpServerConfig>) {
        mcpConfigRepository.setServers(servers)
        _mcpReloading.value = true
        try {
            mcpManager.reload()
        } finally {
            _mcpReloading.value = false
        }
    }

    fun setLogLevel(level: LogLevel) {
        viewModelScope.launch {
            logSettingsRepository.setLevel(level)
        }
    }

    fun refreshLogs(filterServerName: String? = _logViewerState.value.filterServerName) {
        loadLogs(
            filterServerName = filterServerName?.takeIf { it.isNotBlank() },
            preferredFileName = _logViewerState.value.selectedFileName
        )
    }

    fun selectLogFile(fileName: String) {
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = fileName
        )
    }

    private fun loadLogs(filterServerName: String?, preferredFileName: String?) {
        viewModelScope.launch {
            _logViewerState.update {
                it.copy(
                    loading = true,
                    filterServerName = filterServerName,
                    error = null
                )
            }
            val state = withContext(Dispatchers.IO) {
                runCatching {
                    val files = FileLogger.listLogFiles()
                    val selected = files.firstOrNull { it.name == preferredFileName } ?: files.lastOrNull()
                    if (selected == null) {
                        return@runCatching LogViewerUiState(
                            filterServerName = filterServerName,
                            error = "还没有日志文件"
                        )
                    }

                    val rawLines = selected.readLines(Charsets.UTF_8)
                    val filteredLines = if (filterServerName.isNullOrBlank()) {
                        rawLines
                    } else {
                        rawLines.filter { line ->
                            line.contains("[$filterServerName]") ||
                                line.contains(filterServerName, ignoreCase = true)
                        }
                    }
                    val visibleLines = filteredLines.takeLast(MAX_LOG_LINES)

                    LogViewerUiState(
                        files = files.map { it.name },
                        selectedFileName = selected.name,
                        filterServerName = filterServerName,
                        content = visibleLines.joinToString("\n"),
                        totalLines = filteredLines.size,
                        shownLines = visibleLines.size
                    )
                }.getOrElse { e ->
                    LogViewerUiState(
                        filterServerName = filterServerName,
                        error = "读取日志失败: ${e.message}"
                    )
                }
            }
            _logViewerState.value = state
        }
    }

    // 仅持久化标志位——启停 Service 由 AIEditorApp 监听 enabledFlow 统一完成。
    fun setKeepaliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            keepaliveSettingsRepository.setEnabled(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themeSettingsRepository.setThemeMode(mode)
        }
    }

    /** 切换当前选中的容器 profile。 */
    fun setActiveContainerProfile(id: String) {
        viewModelScope.launch {
            containerSettingsRepository.setActiveProfile(id)
        }
    }

    /** 保存（新增/覆盖）自定义容器 profile。 */
    fun saveCustomContainerProfile(profile: ContainerProfile) {
        viewModelScope.launch {
            containerSettingsRepository.upsertCustomProfile(profile)
        }
    }

    /** 编辑自定义 profile：覆盖配置；若镜像来源变了则删旧 rootfs 触发重新解压。 */
    fun editCustomContainerProfile(profile: ContainerProfile) {
        viewModelScope.launch {
            val old = _customProfiles.value.firstOrNull { it.id == profile.id }
            val oldUri = (old?.rootfsSource as? RootfsSource.LocalFile)?.uri
            val newUri = (profile.rootfsSource as? RootfsSource.LocalFile)?.uri
            if (old != null && oldUri != newUri) {
                containerInstaller.deleteCustomRootfs(profile)
            }
            containerSettingsRepository.upsertCustomProfile(profile)
        }
    }

    /** 删除自定义 profile，连带清理其 rootfs 目录。 */
    fun deleteCustomContainerProfile(profile: ContainerProfile) {
        viewModelScope.launch {
            containerSettingsRepository.deleteCustomProfile(profile.id)
            containerInstaller.deleteCustomRootfs(profile)
            if (_activeProfileId.value == profile.id) {
                containerSettingsRepository.setActiveProfile(ContainerProfile.BUILTIN_ID)
            }
        }
    }

    /** 设置识图专用模型；providerId 留空等同 [clearVisionModel]（跟随聊天模型）。 */
    fun setVisionModel(providerId: String, model: String) {
        viewModelScope.launch {
            visionModelSettingsRepository.setVisionModel(providerId, model)
        }
    }

    /** 清空识图专用模型——回退到跟随当前聊天模型。 */
    fun clearVisionModel() {
        viewModelScope.launch {
            visionModelSettingsRepository.clear()
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            repository.setActiveProvider(id)
        }
    }

    fun setProviderEnabled(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setProviderEnabled(id, isEnabled)
        }
    }

    fun saveProvider(provider: AIProviderConfig) {
        viewModelScope.launch {
            repository.saveProvider(provider)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProvider(id)
        }
    }

    fun fetchModels(provider: AIProviderConfig) {
        viewModelScope.launch {
            _fetchState.value = FetchState.Loading
            modelApiService.fetchModels(provider.baseUrl, provider.apiKey, provider.type)
                .onSuccess {
                    _fetchState.value = FetchState.Success(it)
                    resolveModelMetadata(provider.type, it)
                }
                .onFailure { _fetchState.value = FetchState.Error(it.message ?: "拉取失败") }
        }
    }

    fun resolveModelMetadata(type: ProviderType, modelIds: List<String>) {
        val normalizedIds = modelIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedIds.isEmpty()) return
        viewModelScope.launch {
            val metadata = modelMetadataService.resolveAll(type, normalizedIds)
            _modelMetadata.update { current -> current + metadata }
            modelMetadataService.refreshAll(type, normalizedIds).onSuccess { refreshed ->
                _modelMetadata.update { current -> current + refreshed }
            }
        }
    }

    /**
     * 加载所有已启用 provider 的全部模型元数据，合并进 [modelMetadata]。
     * 供「识图模型」等需要展示跨 provider 模型能力标签的页面在进入时调用--
     * 这些页面不像 ProviderEditor 那样会在编辑单个 provider 时顺带 resolve，
     * 不主动加载则 map 为空、所有模型都被误判为不支持图片。
     *
     * 实现要点（避免设置页卡顿）：
     * - 单协程顺序处理各 provider：首个 resolveAll 触发 catalog 加载（网络/磁盘）并写入内存缓存，
     *   后续 provider 命中缓存，不会并发重复拉取 models.dev。
     * - 只调 resolveAll（用缓存/磁盘/infer），不调 refreshAll--识图页只需能力标签，
     *   最新 catalog 的刷新留给 ProviderEditor 显式触发。
     * - 全部解析完一次性 update，避免多次 emit 导致设置页反复重组。
     */
    fun loadAllModelMetadata() {
        val enabled = _providers.value.filter { it.isEnabled }
        if (enabled.isEmpty()) return
        viewModelScope.launch {
            val resolved = mutableMapOf<String, ModelMetadata>()
            for (provider in enabled) {
                val ids = provider.models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (ids.isEmpty()) continue
                resolved += modelMetadataService.resolveAll(provider.type, ids)
            }
            if (resolved.isNotEmpty()) {
                _modelMetadata.update { it + resolved }
            }
        }
    }

    fun resetFetchState() {
        _fetchState.value = FetchState.Idle
    }

    fun testModel(provider: AIProviderConfig, model: String) {
        viewModelScope.launch {
            _testing.update { it + model }
            val result = modelApiService.testModel(provider.baseUrl, provider.apiKey, provider.type, provider.useFullUrl, provider.useResponseApi, model)
            _testResults.update { it + (model to result) }
            _testing.update { it - model }
        }
    }

    fun clearTestResults() {
        _testResults.value = emptyMap()
        _testing.value = emptySet()
    }

    fun selectModel(providerId: String, model: String) {
        viewModelScope.launch {
            repository.setSelectedModel(providerId, model)
        }
    }

    // 主页模型选择：同步更新全局 active provider 的选中模型，使新建会话回退全局时落到用户最近选的模型。
    fun applyModelGlobally(providerId: String, model: String) {
        viewModelScope.launch {
            val activeId = repository.getActiveProviderSync()?.id
            if (activeId != providerId) {
                repository.setActiveProvider(providerId)
            }
            repository.setSelectedModel(providerId, model)
        }
    }

    fun deleteGlobalRule(rule: PermissionRule) {
        viewModelScope.launch { permissionRulesRepository.removeGlobalRule(rule) }
    }

    fun deleteProjectRule(rule: PermissionRule) {
        val name = currentProjectName ?: return
        viewModelScope.launch { permissionRulesRepository.removeProjectRule(name, rule) }
    }

    fun promoteRuleToGlobal(rule: PermissionRule) {
        val name = currentProjectName ?: return
        viewModelScope.launch { permissionRulesRepository.promoteToGlobal(name, rule) }
    }
}
