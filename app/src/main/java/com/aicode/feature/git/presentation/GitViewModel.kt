package com.aicode.feature.git.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.feature.git.domain.GitRepository
import com.aicode.feature.git.domain.model.GitBranch
import com.aicode.feature.git.domain.model.GitCommit
import com.aicode.feature.git.domain.model.GitFileChange
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Git 页的 UI 状态与操作。
 *
 * 进入页面即 [refresh] 并发拉取 status/branches/log；每个写操作（暂存/提交/拉取/推送）执行后
 * 自动刷新并通过 [GitUiState.toast] 反馈。[GitUiState.busy] 守卫防止并发写操作相互踩踏。
 */
@HiltViewModel
class GitViewModel @Inject constructor(
    private val repository: GitRepository
) : ViewModel() {

    private companion object { const val TAG = "GitViewModel" }

    data class GitUiState(
        val loading: Boolean = true,
        val notARepo: Boolean = false,
        val status: GitStatus? = null,
        val branches: List<GitBranch> = emptyList(),
        val commits: List<GitCommit> = emptyList(),
        val tab: GitTab = GitTab.STATUS,
        val busy: Boolean = false,
        val toast: String? = null,
        /** 已展开的提交 hash 集合。 */
        val expandedCommits: Set<String> = emptySet(),
        /** 已懒加载的提交文件清单，按 hash 缓存。 */
        val commitFiles: Map<String, List<GitFileChange>> = emptyMap(),
        /** 正在加载文件清单的提交 hash。 */
        val loadingCommit: String? = null
    )

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    init { refresh() }

    fun setTab(tab: GitTab) = _state.update { it.copy(tab = tab) }

    fun refresh() {
        if (_state.value.busy) return
        _state.update { it.copy(loading = true, toast = null) }
        viewModelScope.launch {
            if (!repository.isRepo()) {
                _state.update { it.copy(loading = false, notARepo = true) }
                return@launch
            }
            try {
                val (status, branches, commits) = coroutineScope {
                    val s = async { repository.status() }
                    val b = async { repository.branches() }
                    val c = async { repository.log() }
                    Triple(s.await(), b.await(), c.await())
                }
                _state.update {
                    it.copy(loading = false, notARepo = false, status = status, branches = branches, commits = commits)
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "刷新失败", e)
                _state.update { it.copy(loading = false, toast = "刷新失败: ${e.message}") }
            }
        }
    }

    /** 执行一个写操作：置 busy → 跑命令 → 刷新 → 反馈。操作间互斥。 */
    private fun runAction(name: String, action: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, toast = null) }
        viewModelScope.launch {
            val msg = try {
                action()
                "${name}成功"
            } catch (e: Exception) {
                FileLogger.e(TAG, "${name}失败", e)
                "${name}失败: ${e.message}"
            }
            // 刷新以反映新状态；失败也刷新，让 UI 与仓库一致。
            try {
                if (repository.isRepo()) {
                    val (status, branches, commits) = coroutineScope {
                        Triple(
                            async { repository.status() }.await(),
                            async { repository.branches() }.await(),
                            async { repository.log() }.await()
                        )
                    }
                    _state.update { it.copy(busy = false, status = status, branches = branches, commits = commits, notARepo = false, toast = msg) }
                } else {
                    _state.update { it.copy(busy = false, notARepo = true, toast = msg) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, toast = "$msg（刷新失败）") }
            }
        }
    }

    fun stage(path: String) = runAction("暂存", { repository.stage(path) })
    fun unstage(path: String) = runAction("取消暂存", { repository.unstage(path) })
    fun stageAll() = runAction("全部暂存", { repository.stageAll() })
    fun unstageAll() = runAction("全部取消暂存", { repository.unstageAll() })
    fun commit(message: String) = runAction("提交", { repository.commit(message) })
    fun pull() = runAction("拉取", { repository.pull() })
    fun push() = runAction("推送", { repository.push() })

    /**
     * 切换某条提交的展开状态。展开时若尚未加载文件清单则懒加载（不置 [GitUiState.busy]，
     * 因为这是只读查看，不应阻塞 status/branches 的写操作）。
     */
    fun toggleCommit(hash: String) {
        val current = _state.value
        if (hash in current.expandedCommits) {
            _state.update { it.copy(expandedCommits = it.expandedCommits - hash) }
            return
        }
        _state.update { it.copy(expandedCommits = it.expandedCommits + hash) }
        if (hash in current.commitFiles) return
        _state.update { it.copy(loadingCommit = hash) }
        viewModelScope.launch {
            val files = try {
                repository.commitFiles(hash)
            } catch (e: Exception) {
                FileLogger.e(TAG, "加载提交文件失败: $hash", e)
                emptyList()
            }
            _state.update {
                it.copy(commitFiles = it.commitFiles + (hash to files), loadingCommit = null)
            }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}
