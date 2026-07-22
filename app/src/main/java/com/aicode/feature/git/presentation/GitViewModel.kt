package com.aicode.feature.git.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.core.util.LineDiff
import com.aicode.feature.git.domain.GitCommandFailureException
import com.aicode.feature.git.domain.GitErrorMessage
import com.aicode.feature.git.domain.GitRepository
import com.aicode.feature.git.domain.model.GitBranch
import com.aicode.feature.git.domain.model.GitCommit
import com.aicode.feature.git.domain.model.GitFileChange
import com.aicode.feature.git.domain.model.GitGraph
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import com.aicode.feature.git.domain.model.GitTag
import com.aicode.feature.git.presentation.component.DiffData
import com.aicode.feature.git.presentation.component.DiffRow
import com.aicode.feature.git.presentation.component.highlightCode
import com.aicode.feature.git.presentation.component.inferSyntaxLanguage
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

    private companion object {
        const val TAG = "GitViewModel"
        /** diff 行数上限：超过则跳过 LCS，避免移动端 O(n·m) 内存压力。与 FileTools 对齐。 */
        const val MAX_DIFF_LINES = 2000
    }

    data class GitUiState(
        val loading: Boolean = true,
        val notARepo: Boolean = false,
        val status: GitStatus? = null,
        val branches: List<GitBranch> = emptyList(),
        val commits: List<GitCommit> = emptyList(),
        /** 拓扑图视图：提交（含父哈希）+ 引用 + 泳道布局，供 LogTab 绘制彩色分支连线。 */
        val graph: GitGraph = GitGraph.EMPTY,
        val tags: List<GitTag> = emptyList(),
        val tab: GitTab = GitTab.STATUS,
        val busy: Boolean = false,
        val toast: String? = null,
        /** 是否已配置远程仓库，控制拉取/推送按钮可用性。 */
        val hasRemote: Boolean = false,
        /** 是否已配置全局署名 user.name（git config --global），控制提交按钮可用性；无署名提交会成为失败提交。 */
        val hasIdentity: Boolean = false,
        /** 已展开的提交 hash 集合。 */
        val expandedCommits: Set<String> = emptySet(),
        /** 已懒加载的提交文件清单，按 hash 缓存。 */
        val commitFiles: Map<String, List<GitFileChange>> = emptyMap(),
        /** 正在加载文件清单的提交 hash。 */
        val loadingCommit: String? = null,
        /** 正在分页加载更旧的提交（滚动到底触发）。不置 busy，避免阻塞写操作。 */
        val graphLoadingMore: Boolean = false,
        /** 正在切换分支。 */
        val checkoutLoading: String? = null,
        /** diff 视图数据；非 null 时 UI 全屏渲染 diff 页。 */
        val diffData: DiffData? = null,
        /** 正在加载 diff。 */
        val diffLoading: Boolean = false
    )

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    init { refresh() }

    /** 仓库快照：并发拉取 status/branches/graph/remote/tags（+可选 identity）的结果。 */
    private data class RepoSnapshot(
        val status: GitStatus,
        val branches: List<GitBranch>,
        val graph: GitGraph,
        val hasRemote: Boolean,
        val tags: List<GitTag>,
        val hasIdentity: Boolean = false
    )

    /** 并发拉取仓库快照。[includeIdentity] 为真时额外拉取 user.name 是否已配置（提交按钮门控用）。 */
    private suspend fun loadSnapshot(includeIdentity: Boolean): RepoSnapshot = coroutineScope {
        val s = async { repository.status() }
        val b = async { repository.branches() }
        val g = async { repository.graph() }
        val r = async { repository.hasRemote() }
        val t = async { repository.listTags() }
        val id = async { if (includeIdentity) repository.getUserName().isNotBlank() else false }
        RepoSnapshot(s.await(), b.await(), g.await(), r.await(), t.await(), id.await())
    }

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
                val snap = loadSnapshot(includeIdentity = true)
                val commits = snap.graph.commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message) }
                _state.update {
                    it.copy(loading = false, notARepo = false, status = snap.status, branches = snap.branches, commits = commits, graph = snap.graph, tags = snap.tags, hasRemote = snap.hasRemote, hasIdentity = snap.hasIdentity)
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "刷新失败", e)
                _state.update { it.copy(loading = false, toast = "刷新失败: ${e.message}") }
            }
        }
    }

    /** 执行一个写操作：置 busy → 跑命令 → 刷新 → 反馈。操作间互斥。 */
    private fun runAction(
        name: String,
        action: suspend () -> String
    ) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, toast = null) }
        viewModelScope.launch {
            val msg = try {
                action()
                "${name}成功"
            } catch (e: Exception) {
                FileLogger.e(TAG, "${name}失败", e)
                val reason = (e as? GitCommandFailureException)?.output ?: e.message
                "${name}失败: ${GitErrorMessage.friendly(reason ?: "")}"
            }
            // 刷新以反映新状态；失败也刷新，让 UI 与仓库一致。
            try {
                if (repository.isRepo()) {
                    val snap = loadSnapshot(includeIdentity = false)
                    val commits = snap.graph.commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message) }
                    _state.update { it.copy(busy = false, status = snap.status, branches = snap.branches, commits = commits, graph = snap.graph, tags = snap.tags, hasRemote = snap.hasRemote, notARepo = false, toast = msg) }
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
    /** 在当前工作区执行 `git init` 初始化仓库；成功后 runAction 末尾自动刷新（notARepo 翻 false）。 */
    fun initRepo() = runAction("初始化", { repository.initRepo() })
    fun pull() {
        if (!_state.value.hasRemote) {
            _state.update { it.copy(toast = "未配置远程仓库，无法拉取") }
            return
        }
        runAction("拉取", { repository.pull() })
    }
    fun push() {
        if (!_state.value.hasRemote) {
            _state.update { it.copy(toast = "未配置远程仓库，无法推送") }
            return
        }
        runAction("推送", { repository.push() })
    }

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

    /**
     * 分页加载更旧的提交。UI 滚到底时调用：取已加载提交作为锚点，[repository.graphAppend] 用
     * `--skip` 取下一页并整体重算泳道布局，返回完整图直接替换 state.graph。不置 busy，避免阻塞写操作。
     * 守卫：正在加载或无更多时直接返回。失败 toast 提示且保留 hasMore 允许重试。
     */
    fun loadMoreCommits() {
        val current = _state.value
        if (current.graphLoadingMore || !current.graph.hasMore) return
        _state.update { it.copy(graphLoadingMore = true) }
        viewModelScope.launch {
            try {
                val graph = repository.graphAppend(current.graph.commits)
                val commits = graph.commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message) }
                _state.update { it.copy(graph = graph, commits = commits, graphLoadingMore = false) }
            } catch (e: Exception) {
                FileLogger.e(TAG, "加载更多提交失败", e)
                _state.update { it.copy(graphLoadingMore = false, toast = "加载更多失败: ${e.message}") }
            }
        }
    }

    /**
     * 切换到指定分支或标签。成功后刷新全量状态。
     */
    fun checkoutBranch(ref: String, isRemote: Boolean = false) {
        if (_state.value.busy || _state.value.checkoutLoading != null) return
        _state.update { it.copy(checkoutLoading = ref, toast = null) }
        viewModelScope.launch {
            val msg = try {
                repository.checkout(ref, isRemote)
                "已切换到 $ref"
            } catch (e: Exception) {
                FileLogger.e(TAG, "切换分支失败", e)
                val reason = (e as? GitCommandFailureException)?.output ?: e.message
                "切换失败: ${GitErrorMessage.friendly(reason ?: "")}"
            }
            try {
                if (repository.isRepo()) {
                    val snap = loadSnapshot(includeIdentity = false)
                    val commits = snap.graph.commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message) }
                    _state.update { it.copy(checkoutLoading = null, status = snap.status, branches = snap.branches, commits = commits, graph = snap.graph, tags = snap.tags, hasRemote = snap.hasRemote, notARepo = false, toast = msg) }
                } else {
                    _state.update { it.copy(checkoutLoading = null, notARepo = true, toast = msg) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(checkoutLoading = null, toast = "$msg（刷新失败）") }
            }
        }
    }

    /**
     * 创建新分支。name 为新分支名；startPoint 为基准分支（null/空 → 从当前 HEAD）；
     * checkout=true 则创建并切换。复用 runAction：置 busy → 跑命令 → 刷新 → toast。
     */
    fun createBranch(name: String, startPoint: String?, checkout: Boolean) {
        if (name.isBlank()) return
        runAction("创建分支", { repository.createBranch(name, startPoint, checkout) })
    }

    /**
     * 安全删除本地分支（git branch -d）。未合并或为当前分支时 git 报错，经 runAction toast 透出。
     */
    fun deleteBranch(name: String) {
        if (name.isBlank()) return
        runAction("删除分支", { repository.deleteBranch(name) })
    }

    /**
     * 删除远程分支（git push --delete）。会改远端，失败经 runAction toast 透出。
     */
    fun deleteRemoteBranch(ref: String) {
        if (ref.isBlank()) return
        runAction("删除远程分支", { repository.deleteRemoteBranch(ref) })
    }

    /**
     * 重命名本地分支（git branch -m）。当前分支也可重命名。失败经 runAction toast 透出。
     */
    fun renameBranch(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank()) return
        runAction("重命名分支", { repository.renameBranch(oldName, newName) })
    }

    /**
     * 创建轻量标签（git tag <name>），指向当前 HEAD。失败经 runAction toast 透出。
     */
    fun createTag(name: String) {
        if (name.isBlank()) return
        runAction("创建标签", { repository.createTag(name) })
    }

    /**
     * 删除本地标签（git tag -d）。失败经 runAction toast 透出。
     */
    fun deleteTag(name: String) {
        if (name.isBlank()) return
        runAction("删除标签", { repository.deleteTag(name) })
    }

    /**
     * 退出 diff 视图，清空 diff 状态。
     */
    fun clearDiff() = _state.update { it.copy(diffData = null) }

    /**
     * 加载某次提交中某文件的差异（改动前 vs 改动后）。
 * hash 为提交 hash，path 为文件路径。后台线程算 diff + 高亮，完成后填 diffData。
 */
    fun loadCommitFileDiff(hash: String, path: String) {
        if (_state.value.diffLoading) return
        _state.update { it.copy(diffLoading = true, diffData = null) }
        viewModelScope.launch {
            val data = runCatching { computeDiff(path, "${hash}^", hash, repository::showFileContent) }
                .getOrElse { e ->
                    FileLogger.e(TAG, "加载提交文件 diff 失败: $path", e)
                    null
                }
            _state.update { it.copy(diffLoading = false, diffData = data, toast = if (data == null) "加载差异失败" else null) }
        }
    }

    /**
     * 加载工作区某文件的未暂存差异（HEAD vs 工作区当前内容）。
 * path 为文件路径。用 showFileContent("HEAD", path) 取版本库快照，用 worktreeFileContent(path) 取工作区当前内容。
 */
    fun loadWorktreeDiff(path: String) {
        if (_state.value.diffLoading) return
        _state.update { it.copy(diffLoading = true, diffData = null) }
        viewModelScope.launch {
            val data = runCatching {
                computeDiff(path, "HEAD", "工作区") { ref, p ->
                    if (ref == "工作区") repository.worktreeFileContent(p)
                    else repository.showFileContent(ref, p)
                }
            }.getOrElse { e ->
                FileLogger.e(TAG, "加载工作区 diff 失败: $path", e)
                null
            }
            _state.update { it.copy(diffLoading = false, diffData = data, toast = if (data == null) "加载差异失败" else null) }
        }
    }

    /**
     * 取旧/新两份文件内容，用 [LineDiff] 算行级差异，对每行做语法高亮，组装成 [DiffData]。
 * 二进制文件（含 NUL 字节）或超大文件（任一侧超 [MAX_DIFF_LINES]）降级处理。
 * [contentProvider] 负责按 ref 取文件内容，供提交 diff 和工作区 diff 复用。
 */
    private suspend fun computeDiff(
        path: String,
        oldRef: String,
        newRef: String,
        contentProvider: suspend (String, String) -> String
    ): DiffData {
        val oldContent = contentProvider(oldRef, path)
        val newContent = contentProvider(newRef, path)

        // 二进制检测：git show 对二进制文件返回乱码，直接看是否含 NUL。
        if (oldContent.contains('\u0000') || newContent.contains('\u0000')) {
            return DiffData(path, oldRef, newRef, emptyList(), 0, 0, isBinary = true)
        }

        val oldLines = oldContent.split('\n')
        val newLines = newContent.split('\n')
        if (maxOf(oldLines.size, newLines.size) > MAX_DIFF_LINES) {
            return DiffData(path, oldRef, newRef, emptyList(), 0, 0, isLarge = true)
        }

        val diffLines = LineDiff.diff(oldContent, newContent)
        val language = inferSyntaxLanguage(path)

        // 对旧/新文件整体各跑一次高亮，拿到全文的 token 区间；渲染时按行偏移截取对应 SpanStyle。
        val oldHighlighted = highlightCode(oldContent, language)
        val newHighlighted = highlightCode(newContent, language)

        // 构建行号 + 高亮截取。oldLineOffsets/newLineOffsets 为每行在全文中的起始偏移。
        val oldOffsets = lineOffsets(oldContent)
        val newOffsets = lineOffsets(newContent)

        var oldNum = 0
        var newNum = 0
        val rows = diffLines.map { line ->
            val (oldN, newN) = when (line.type) {
                LineDiff.LineType.CONTEXT -> { oldNum++; newNum++; oldNum to newNum }
                LineDiff.LineType.REMOVE -> { oldNum++; oldNum to null }
                LineDiff.LineType.ADD -> { newNum++; null to newNum }
            }
            // 截取该行在高亮全文中的 SpanStyle。行索引 = 行号 - 1（0-based）。
            val highlighted = when (line.type) {
                LineDiff.LineType.REMOVE -> sliceLineHighlight(oldHighlighted, oldOffsets, oldN?.let { it - 1 })
                LineDiff.LineType.ADD -> sliceLineHighlight(newHighlighted, newOffsets, newN?.let { it - 1 })
                LineDiff.LineType.CONTEXT -> sliceLineHighlight(oldHighlighted, oldOffsets, oldN?.let { it - 1 })
            }
            DiffRow(line.type, line.text, highlighted, oldN, newN)
        }

        val added = rows.count { it.type == LineDiff.LineType.ADD }
        val removed = rows.count { it.type == LineDiff.LineType.REMOVE }
        return DiffData(path, oldRef, newRef, rows, added, removed)
    }

    /**
     * 从全文高亮 AnnotatedString 中截取某行的 SpanStyle，返回只含该行文本+样式的新 AnnotatedString。
 * lineIndex 超出范围或高亮为 null 时返回 null（降级纯文本）。
 */
    private fun sliceLineHighlight(
        highlighted: androidx.compose.ui.text.AnnotatedString?,
        offsets: IntArray,
        lineIndex: Int?
    ): androidx.compose.ui.text.AnnotatedString? {
        if (highlighted == null || lineIndex == null || lineIndex < 0 || lineIndex >= offsets.size) return null
        val start = offsets[lineIndex]
        val end = if (lineIndex + 1 < offsets.size) offsets[lineIndex + 1] - 1 else highlighted.length
        if (start >= highlighted.length || end <= start) return null
        return highlighted.subSequence(start, end)
    }

    /** 计算每行在全文中的起始偏移（含末行后的虚拟偏移 = 全文长度）。 */
    private fun lineOffsets(text: String): IntArray {
        val lines = text.split('\n')
        val offsets = IntArray(lines.size + 1)
        var pos = 0
        for (i in lines.indices) {
            offsets[i] = pos
            pos += lines[i].length + 1 // +1 为换行符
        }
        offsets[lines.size] = pos
        return offsets
    }
}
