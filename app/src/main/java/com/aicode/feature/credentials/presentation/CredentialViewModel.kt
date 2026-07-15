package com.aicode.feature.credentials.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.feature.credentials.data.GitUserSettingsRepository
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import com.aicode.feature.git.domain.GitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * 凭据页 UI 编排：凭据 CRUD + 提交署名(user.name/email) 配置。
 *
 * 提交署名以 [GitUserSettingsRepository](DataStore) 为真源；用户保存后同步落 `git config --global`
 * （[GitRepository.setUserGlobal]）。进页 init 时若 DataStore 有值则补一次同步，保证 rootfs 升级丢
 * `.gitconfig` 后能自动恢复——单向「DataStore → git」，DataStore 空时不动容器既有配置。
 */
@HiltViewModel
class CredentialViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val userSettings: GitUserSettingsRepository,
    private val gitRepository: GitRepository
) : ViewModel() {

    private companion object { const val TAG = "CredentialViewModel" }

    data class UiState(
        val credentials: List<GitCredential> = emptyList(),
        val userName: String = "",
        val userEmail: String = "",
        /** 容器内 `git config --global` 实际的 user.name，用于回显「git 实际值」。 */
        val globalUserName: String = "",
        val toast: String? = null
    )

    // combine 持久流外，单独用一个 MutableStateFlow 承载「全局实际值」与 toast 这类非 DataStore 来源的状态。
    private val _extra = MutableStateFlow(Extra())
    private data class Extra(val globalUserName: String = "", val toast: String? = null)

    val state: StateFlow<UiState> = combine(
        credentialRepository.getAll(),
        userSettings.userNameFlow,
        userSettings.userEmailFlow,
        _extra
    ) { creds, name, email, extra ->
        UiState(
            credentials = creds,
            userName = name,
            userEmail = email,
            globalUserName = extra.globalUserName,
            toast = extra.toast
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init { syncGlobalIdentity() }

    /** 把 DataStore 的署名（若非空）同步进容器 git 全局配置，并刷新实际值回显。 */
    private fun syncGlobalIdentity() {
        viewModelScope.launch {
            runCatching {
                val name = userSettings.userNameFlow.first()
                val email = userSettings.userEmailFlow.first()
                if (name.isNotBlank() || email.isNotBlank()) gitRepository.setUserGlobal(name, email)
                _extra.update { it.copy(globalUserName = gitRepository.getGlobalUserName()) }
            }.onFailure { FileLogger.w(TAG, "同步全局署名失败: ${it.message}") }
        }
    }

    fun saveCredential(credential: GitCredential) {
        viewModelScope.launch {
            try {
                credentialRepository.save(credential)
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
            toast("凭据已删除")
        }
    }

    fun setDefault(id: String, isDefault: Boolean) {
        viewModelScope.launch { credentialRepository.setDefault(id, isDefault) }
    }

    /** 保存提交署名：写 DataStore 真源 + 同步落 git 全局配置 + 刷新实际值回显。 */
    fun saveUserIdentity(name: String, email: String) {
        viewModelScope.launch {
            try {
                userSettings.setUserIdentity(name, email)
                gitRepository.setUserGlobal(name, email)
                _extra.update { it.copy(globalUserName = gitRepository.getGlobalUserName()) }
                toast("署名已保存到 git 全局配置")
            } catch (e: Exception) {
                FileLogger.e(TAG, "保存署名失败", e)
                toast("保存失败: ${e.message}")
            }
        }
    }

    fun consumeToast() = _extra.update { it.copy(toast = null) }

    private fun toast(msg: String) = _extra.update { it.copy(toast = msg) }
}

/** 生成新凭据 id：时间戳 + 随机后缀（避免同毫秒冲突），仿现有 entity 主键风格。 */
internal fun newCredentialId(): String = "${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}"
