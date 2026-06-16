package com.aicodeeditor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.feature.settings.data.remote.ModelApiService
import com.aicodeeditor.feature.settings.data.remote.ModelTestResult
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
    private val modelApiService: ModelApiService
) : ViewModel() {

    private val _providers = MutableStateFlow<List<AIProviderConfig>>(emptyList())
    val providers: StateFlow<List<AIProviderConfig>> = _providers.asStateFlow()

    private val _activeProvider = MutableStateFlow<AIProviderConfig?>(null)
    val activeProvider: StateFlow<AIProviderConfig?> = _activeProvider.asStateFlow()

    /** 拉取到的可勾选模型列表状态。 */
    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    /** 每个模型的测试结果，键为模型名。 */
    private val _testResults = MutableStateFlow<Map<String, ModelTestResult>>(emptyMap())
    val testResults: StateFlow<Map<String, ModelTestResult>> = _testResults.asStateFlow()

    /** 正在测试中的模型集合。 */
    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDefaultProvidersIfEmpty()

            launch {
                repository.getAllProviders().collectLatest {
                    _providers.value = it
                }
            }

            launch {
                repository.getActiveProvider().collectLatest {
                    _activeProvider.value = it
                }
            }
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            repository.setActiveProvider(id)
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
            val result = modelApiService.testModel(provider.baseUrl, provider.apiKey, provider.type, model)
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
}
