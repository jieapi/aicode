package com.aicodeeditor.feature.workspace.presentation.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import com.aicodeeditor.feature.workspace.domain.remote.RemoteAuth
import com.aicodeeditor.feature.workspace.domain.model.RemoteServer
import com.aicodeeditor.feature.workspace.domain.repository.RemoteServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RemoteServerViewModel @Inject constructor(
    private val repository: RemoteServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteServerUiState())
    val uiState: StateFlow<RemoteServerUiState> = _uiState.asStateFlow()

    init {
        loadServers()
    }

    private fun loadServers() {
        viewModelScope.launch {
            repository.getServers()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                .collect { servers ->
                    _uiState.value = _uiState.value.copy(servers = servers)
                }
        }
    }

    fun connectServer(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.connect(id)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Connection failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun disconnectServer(id: String) {
        viewModelScope.launch {
            repository.disconnect(id)
            // 状态通过 Room Flow 自动刷新 (isActive)
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            repository.deleteServer(id)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // 实际项目中可以分离到 AddServerViewModel
    fun addServer(
        name: String,
        host: String,
        port: String,
        username: String,
        password: String, // 简化：目前先只做密码形式的表单
        remotePath: String,
        protocol: RemoteProtocol
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: 22
            val server = RemoteServer(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = protocol,
                host = host,
                port = p,
                username = username,
                remotePath = remotePath,
                localMountPath = "/data/data/com.aicodeeditor/files/workspace/${name.replace(" ", "_")}" // 这里应该通过 Context 获取更严谨
            )
            repository.addServer(server, RemoteAuth.Password(password))
        }
    }
}

data class RemoteServerUiState(
    val servers: List<RemoteServer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
