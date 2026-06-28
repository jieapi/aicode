package com.aicodeeditor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.core.util.LogLevel
import com.aicodeeditor.feature.agent.domain.mcp.McpConfigRepository
import com.aicodeeditor.feature.agent.domain.mcp.McpManager
import com.aicodeeditor.feature.agent.domain.mcp.McpServerConfig
import com.aicodeeditor.feature.agent.domain.mcp.McpServerStatus
import com.aicodeeditor.feature.agent.domain.permission.PermissionRule
import com.aicodeeditor.feature.agent.domain.permission.PermissionRulesRepository
import com.aicodeeditor.feature.settings.data.remote.ModelApiService
import com.aicodeeditor.feature.settings.data.remote.ModelTestResult
import com.aicodeeditor.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicodeeditor.feature.settings.data.repository.LogSettingsRepository
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 模型列表拉取状态，按 providerId 区分。 */
sealed class FetchState {
    object Idle : FetchState()
    object Loading : FetchState()
    data class Success(val models: List<String>) : FetchState()
    data class Error(val message: String) : FetchState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AIProviderRepository,
    private val modelApiService: ModelApiService,
    private val logSettingsRepository: LogSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository
) : ViewModel() {

    private val _providers = MutableStateFlow<List<AIProviderConfig>>(emptyList())
    val providers: StateFlow<List<AIProviderConfig>> = _providers.asStateFlow()

    private val _activeProvider = MutableStateFlow<AIProviderConfig?>(null)
    val activeProvider: StateFlow<AIProviderConfig?> = _activeProvider.asStateFlow()

    /** 当前日志最低记录等级（供设置页选择）。 */
    private val _logLevel = MutableStateFlow(LogLevel.VERBOSE)
    val logLevel: StateFlow<LogLevel> = _logLevel.asStateFlow()

    /** 后台保活常驻通知开关（供设置页切换；需用户授权通知权限后才生效）。 */
    private val _keepaliveEnabled = MutableStateFlow(false)
    val keepaliveEnabled: StateFlow<Boolean> = _keepaliveEnabled.asStateFlow()

    /** 结构化的 MCP server 列表（供设置页可视化编辑）。 */
    private val _mcpServers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers.asStateFlow()

    /** 各 MCP server 连接状态。 */
    val mcpStatuses: StateFlow<List<McpServerStatus>> = mcpManager.statuses

    /** MCP 重连进行中标志（供 UI 显示加载态）。 */
    private val _mcpReloading = MutableStateFlow(false)
    val mcpReloading: StateFlow<Boolean> = _mcpReloading.asStateFlow()

    /** 拉取到的可勾选模型列表状态。 */
    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    /** 每个模型的测试结果，键为模型名。 */
    private val _testResults = MutableStateFlow<Map<String, ModelTestResult>>(emptyMap())
    val testResults: StateFlow<Map<String, ModelTestResult>> = _testResults.asStateFlow()

    /** 正在测试中的模型集合。 */
    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    /** 已保存的工具授权规则——全局 + 当前项目。供「工具授权」管理页查看/撤销。 */
    private val _globalRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val globalRules: StateFlow<List<PermissionRule>> = _globalRules.asStateFlow()

    private val _projectRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val projectRules: StateFlow<List<PermissionRule>> = _projectRules.asStateFlow()

    /** 当前项目名（进入设置时确定）；为 null 时项目级规则不可用。 */
    val currentProjectName: String? = permissionRulesRepository.currentProjectName()

    init {
        viewModelScope.launch {
            // 启动即保证有激活服务商（若库中存在却无激活项），避免主页模型胶囊因 activeProvider=null 消失。
            repository.ensureActiveProvider()

            launch {
                repository.getAllProviders().collectLatest {
                    _providers.value = it
                    // 运行期兜底：服务商列表变化后若仍无激活项且有服务商，自动激活首个。
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

    /** 新增或更新一个 MCP server；编辑改名时按原名移除旧项。保存后落盘并重连。 */
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

    /** 删除指定 MCP server。 */
    fun deleteMcpServer(name: String) {
        viewModelScope.launch {
            persistMcpServers(_mcpServers.value.filterNot { it.name == name })
        }
    }

    /** 切换某个 MCP server 的启用状态。 */
    fun setMcpServerEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            persistMcpServers(_mcpServers.value.map { if (it.name == name) it.copy(enabled = enabled) else it })
        }
    }

    /** 仅重新连接，不改动配置。 */
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

    /** 序列化落盘后重连所有 server。 */
    private suspend fun persistMcpServers(servers: List<McpServerConfig>) {
        mcpConfigRepository.setServers(servers)
        _mcpReloading.value = true
        try {
            mcpManager.reload()
        } finally {
            _mcpReloading.value = false
        }
    }

    /** 切换日志最低记录等级（持久化后由 AIEditorApp 同步到 FileLogger）。 */
    fun setLogLevel(level: LogLevel) {
        viewModelScope.launch {
            logSettingsRepository.setLevel(level)
        }
    }

    /**
     * 切换后台保活常驻通知开关。仅持久化标志位——真正的启停 Service 由 AIEditorApp 监听
     * [KeepaliveSettingsRepository.enabledFlow] 统一完成（同一反应器同时负责冷启动恢复）。
     * 调用方（设置页）需先确保已授予通知权限，否则开关虽为 true 但通知不会展示。
     */
    fun setKeepaliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            keepaliveSettingsRepository.setEnabled(enabled)
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

    /** 从服务商接口拉取可用模型列表，结果写入 fetchState 供 UI 勾选。 */
    fun fetchModels(provider: AIProviderConfig) {
        viewModelScope.launch {
            _fetchState.value = FetchState.Loading
            modelApiService.fetchModels(provider.baseUrl, provider.apiKey, provider.type)
                .onSuccess { _fetchState.value = FetchState.Success(it) }
                .onFailure { _fetchState.value = FetchState.Error(it.message ?: "拉取失败") }
        }
    }

    fun resetFetchState() {
        _fetchState.value = FetchState.Idle
    }

    /** 测试单个模型连通性。 */
    fun testModel(provider: AIProviderConfig, model: String) {
        viewModelScope.launch {
            _testing.update { it + model }
            val result = modelApiService.testModel(provider.baseUrl, provider.apiKey, provider.type, provider.apiPath, provider.useResponseApi, model)
            _testResults.update { it + (model to result) }
            _testing.update { it - model }
        }
    }

    fun clearTestResults() {
        _testResults.value = emptyMap()
        _testing.value = emptySet()
    }

    /** 切换当前选中模型（聊天页生效）。 */
    fun selectModel(providerId: String, model: String) {
        viewModelScope.launch {
            repository.setSelectedModel(providerId, model)
        }
    }

    /** 删除一条全局授权规则。 */
    fun deleteGlobalRule(rule: PermissionRule) {
        viewModelScope.launch { permissionRulesRepository.removeGlobalRule(rule) }
    }

    /** 删除当前项目的一条授权规则。 */
    fun deleteProjectRule(rule: PermissionRule) {
        val name = currentProjectName ?: return
        viewModelScope.launch { permissionRulesRepository.removeProjectRule(name, rule) }
    }

    /** 把当前项目的一条规则提升为全局。 */
    fun promoteRuleToGlobal(rule: PermissionRule) {
        val name = currentProjectName ?: return
        viewModelScope.launch { permissionRulesRepository.promoteToGlobal(name, rule) }
    }
}
