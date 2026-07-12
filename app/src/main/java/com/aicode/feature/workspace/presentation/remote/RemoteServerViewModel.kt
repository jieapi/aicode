package com.aicode.feature.workspace.presentation.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteMount
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import com.aicode.feature.workspace.domain.remote.RemoteAuth
import com.aicode.feature.workspace.domain.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.aicode.feature.workspace.domain.model.Workspace
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.settings.data.repository.SyncSettingsRepository
import com.aicode.feature.workspace.domain.remote.ftp.FtpServerManager

@HiltViewModel
class RemoteServerViewModel @Inject constructor(
    private val repository: RemoteRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    val ftpServerManager: FtpServerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteServerUiState())
    val uiState: StateFlow<RemoteServerUiState> = _uiState.asStateFlow()

    val syncIgnoredPatterns = syncSettingsRepository.ignoredPatterns
    val syncUseGitIgnore = syncSettingsRepository.useGitIgnore
    val maxSyncBatchSize = syncSettingsRepository.maxSyncBatchSize

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            launch {
                repository.getConnections()
                    .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                    .collect { connections ->
                        _uiState.value = _uiState.value.copy(connections = connections)
                    }
            }
            launch {
                repository.getMounts()
                    .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                    .collect { mounts ->
                        val wasEmpty = _uiState.value.mounts.isEmpty()
                        _uiState.value = _uiState.value.copy(mounts = mounts)
                        
                        // Auto-connect mounts on load
                        if (wasEmpty) {
                            mounts.filter { it.autoConnect && !it.isActive }.forEach {
                                connectMount(it.id)
                            }
                        }
                    }
            }
            launch {
                workspaceRepository.workspaces.collect { workspaces ->
                    _uiState.value = _uiState.value.copy(workspaces = workspaces)
                }
            }
        }
    }

    fun connectMount(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.connectMount(id)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Connection failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun disconnectMount(id: String) {
        viewModelScope.launch {
            repository.disconnectMount(id)
        }
    }

    fun forceUploadMount(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.forceUploadMount(id)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "全量上传失败: ${result.exceptionOrNull()?.message}")
            } else {
                _uiState.value = _uiState.value.copy(error = "全量上传成功！") // 暂时复用 error 展示成功消息，或稍后单独做 toast
            }
        }
    }

    fun forceDownloadMount(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.forceDownloadMount(id)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "全量下载失败: ${result.exceptionOrNull()?.message}")
            } else {
                _uiState.value = _uiState.value.copy(error = "全量下载成功！")
            }
        }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            repository.deleteConnection(id)
        }
    }
    
    fun deleteMount(id: String) {
        viewModelScope.launch {
            repository.deleteMount(id)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun addConnection(
        name: String,
        host: String,
        port: String,
        username: String,
        password: String, 
        protocol: RemoteProtocol
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val conn = RemoteConnection(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = protocol,
                host = host,
                port = p,
                username = username.ifBlank { "local" }
            )
            repository.addConnection(conn, RemoteAuth.Password(password))
        }
    }

    fun updateConnection(
        id: String,
        name: String,
        host: String,
        port: String,
        username: String,
        password: String,
        protocol: RemoteProtocol
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val conn = RemoteConnection(
                id = id,
                name = name,
                protocol = protocol,
                host = host,
                port = p,
                username = username.ifBlank { "local" }
            )
            repository.updateConnection(conn, RemoteAuth.Password(password))
        }
    }

    fun addMount(connectionId: String, remotePath: String, localWorkspacePath: String, autoConnect: Boolean) {
        viewModelScope.launch {
            val mount = RemoteMount(
                id = UUID.randomUUID().toString(),
                connectionId = connectionId,
                remotePath = remotePath,
                localMountPath = localWorkspacePath,
                autoConnect = autoConnect
            )
            repository.addMount(mount)
            if (autoConnect) {
                connectMount(mount.id)
            }
        }
    }

    fun updateMount(id: String, connectionId: String, remotePath: String, localWorkspacePath: String, autoConnect: Boolean) {
        viewModelScope.launch {
            val mount = RemoteMount(
                id = id,
                connectionId = connectionId,
                remotePath = remotePath,
                localMountPath = localWorkspacePath,
                autoConnect = autoConnect
            )
            repository.updateMount(mount)
            if (autoConnect) {
                connectMount(mount.id)
            } else {
                disconnectMount(mount.id)
            }
        }
    }

    fun testConnection(
        host: String,
        port: String,
        username: String,
        password: String,
        protocol: RemoteProtocol,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val result = repository.testConnection(host, p, username, RemoteAuth.Password(password), protocol)
            if (result.isSuccess) {
                onResult(true, "连接成功！")
            } else {
                onResult(false, "连接失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun listRemoteDirectories(
        connectionId: String,
        path: String,
        onResult: (Boolean, List<String>, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.listRemoteDirectories(connectionId, path)
            if (result.isSuccess) {
                onResult(true, result.getOrNull() ?: emptyList(), "")
            } else {
                onResult(false, emptyList(), result.exceptionOrNull()?.message ?: "未知错误")
            }
        }
    }

    fun setSyncIgnoredPatterns(patterns: String) {
        syncSettingsRepository.setIgnoredPatterns(patterns)
    }

    fun setSyncUseGitIgnore(use: Boolean) {
        syncSettingsRepository.setUseGitIgnore(use)
    }

    fun setMaxSyncBatchSize(size: Int) {
        syncSettingsRepository.setMaxSyncBatchSize(size)
    }

    fun toggleFtpServer() {
        viewModelScope.launch {
            ftpServerManager.toggleServer()
        }
    }

    fun saveFtpServerConfig(port: Int, username: String, password: String, isAnonymous: Boolean, autoStart: Boolean) {
        viewModelScope.launch {
            ftpServerManager.saveConfig(port, username, password, isAnonymous, autoStart)
        }
    }

    private fun defaultPort(protocol: RemoteProtocol): Int = when (protocol) {
        RemoteProtocol.SFTP -> 22
        RemoteProtocol.FTP -> 21
        RemoteProtocol.LOCAL -> 0
    }
}

data class RemoteServerUiState(
    val connections: List<RemoteConnection> = emptyList(),
    val mounts: List<RemoteMount> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
