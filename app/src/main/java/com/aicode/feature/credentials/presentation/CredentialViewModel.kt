package com.aicode.feature.credentials.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.feature.credentials.data.GitCredentialsFileSync
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import com.aicode.feature.git.domain.GitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 凭据页 UI 编排：凭据 CRUD + 提交署名(user.name/email) 配置。
 *
 * 提交署名读写走 `git config`（[GitRepository.setUserIdentity] / [getUserName] / [getUserEmail]）：
 * **优先项目级**（当前工作区 /workspace/.git/config），无则退全局（持久挂载 /root/.aicode/.gitconfig）。
 * UI 与终端敲 `git config user.name` 读到的是同一份署名——优先项目级、无则退全局，无两套源头竞争。
 */
@HiltViewModel
class CredentialViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val gitRepository: GitRepository,
    private val fileSync: GitCredentialsFileSync
) : ViewModel() {

    private companion object { const val TAG = "CredentialViewModel" }

    data class UiState(
        val credentials: List<GitCredential> = emptyList(),
        /** 容器 git config 当前 user.name，作编辑框初值。 */
        val userName: String = "",
        /** 容器 git config 当前 user.email，作编辑框初值。 */
        val userEmail: String = "",
        /** 容器 `git config --global` 实际 user.name，回显「git 实际值」（与 userName 同源）。 */
        val globalUserName: String = "",
        /** 容器 git config 当前 remote.origin.url，作仓库地址编辑框初值。 */
        val repoUrl: String = "",
        val toast: String? = null
    )

    // 非反应式来源的状态：git config 读出的署名、仓库地址、toast。credentials 走 Room Flow。
    private val _extra = MutableStateFlow(Extra())
    private data class Extra(
        val userName: String = "",
        val userEmail: String = "",
        val globalUserName: String = "",
        val repoUrl: String = "",
        val toast: String? = null
    )

    val state: StateFlow<UiState> = combine(credentialRepository.getAll(), _extra) { creds, extra ->
        UiState(
            credentials = creds,
            userName = extra.userName,
            userEmail = extra.userEmail,
            globalUserName = extra.globalUserName,
            repoUrl = extra.repoUrl,
            toast = extra.toast
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        refreshIdentity()
        // 兜底：把 Room 凭据落盘到容器持久挂载，保证升级后或文件被删时仍就位（详见 GitCredentialsFileSync）。
        viewModelScope.launch { fileSync.syncAll() }
    }

    /** 从容器 git config 读取当前署名与仓库地址刷新 UI（编辑框初值 + 实际值回显）。可重入，进凭据页时调一次兜住终端改动。 */
    fun refreshIdentity() {
        viewModelScope.launch {
            runCatching {
                _extra.update {
                    it.copy(
                        userName = gitRepository.getUserName(),
                        userEmail = gitRepository.getUserEmail(),
                        globalUserName = gitRepository.getUserName(),
                        repoUrl = gitRepository.getRepoUrl()
                    )
                }
            }.onFailure { FileLogger.w(TAG, "读取 git 全局署名失败: ${it.message}") }
        }
    }

    fun saveCredential(credential: GitCredential) {
        viewModelScope.launch {
            try {
                credentialRepository.save(credential)
                fileSync.syncAll() // 落盘到容器持久挂载，UI/终端/AI 三端共用
                toast("凭据已保存")
            } catch (e: Exception) {
                FileLogger.e(TAG, "保存凭据失败", e)
                toast("保存失败: ${e.message}")
            }
        }
    }

    fun deleteCredential(id: String) {
        viewModelScope.launch {
            credentialRepository.delete(id)
            fileSync.syncAll() // 删凭据后从落盘文件移除，否则终端/AI 仍能用旧凭据
            toast("凭据已删除")
        }
    }

    fun setDefault(id: String, isDefault: Boolean) {
        viewModelScope.launch {
            credentialRepository.setDefault(id, isDefault)
            fileSync.syncAll() // 切默认后落盘文件随之更新，三端按新默认条带凭据
        }
    }

    /** 保存提交署名与仓库地址：跑 `git config` 写入对应 key 真源 + 刷新回显。UI 与命令行同一文件。 */
    fun saveUserIdentity(name: String, email: String, repoUrl: String) {
        viewModelScope.launch {
            try {
                gitRepository.setUserIdentity(name, email)
                gitRepository.setRepoUrl(repoUrl)
                refreshIdentity()
                toast("署名与仓库地址已保存")
            } catch (e: Exception) {
                FileLogger.e(TAG, "保存署名失败", e)
                toast("保存失败: ${e.message}")
            }
        }
    }

    fun consumeToast() = _extra.update { it.copy(toast = null) }

    private fun toast(msg: String) = _extra.update { it.copy(toast = msg) }
}
