package com.aicode.feature.git.domain

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.git.domain.model.GitBranch
import com.aicode.feature.git.domain.model.GitCommit
import com.aicode.feature.git.domain.model.GitFileChange
import com.aicode.feature.git.domain.model.GitGraph
import com.aicode.feature.git.domain.model.GitGraphRef
import com.aicode.feature.git.domain.model.GraphCommit
import com.aicode.feature.git.domain.model.GraphEdge
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTag
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GitRepository"

/**
 * 在容器内执行 git 命令并解析输出。
 *
 * 直接复用 [LinuxContainerEngine.runCommandSync]（cwd = 当前工作区，绑定挂载到 /workspace），
 * 不经 agent 工具链 / 权限引擎——Git 页是用户主动操作。所有输出解析为纯领域模型。
 *
 * 命令经 [shellQuote] 逐参数转义后拼成单条 `git ...` 字符串交给 `/bin/sh -c`，故格式串里的
 * `|`、`%(...)`、含空格的路径都能安全传递。
 */
@Singleton
class GitRepository @Inject constructor(
    private val engine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        /** 提交拓扑图每页加载条数。首批与每次「加载更多」都取这么多条，超过的需滚到底再拉。 */
        const val GRAPH_PAGE_SIZE = 100
    }
    /**
     * 执行一条 `git` 子命令，返回合并后的 stdout+stderr 文本。仅用于只读命令（status/branches/log/remote 等）：
     * 这些靠输出解析、容错，git 非零退出码不会让 UI 误判（解析得空罢了）。凭据由 `credential.helper=store`
     * 经落盘文件自动注入（见 [GitCredentialsFileSync]），不在此按命令塞 `http.extraHeader`。
     * 每条参数经 [shellQuote] 单引号转义，含空格/特殊字符的值（提交消息、含空格路径等）安全传递。
     */
    private suspend fun git(
        vararg args: String
    ): String = gitRaw(args)

    /**
     * 执行一条 `git` **写**子命令，据退出码判成败：非零（真实失败）抛 [GitCommandFailureException]
     * 携带 git 输出文本，上层 [com.aicode.feature.git.presentation.GitViewModel.runAction] 据此如实显示
     * 「失败 + 原因」而非误报成功。代表场景：未配置署名提交、未授权推送、合并冲突。空退出码（超时/异常）
     * 同样按失败抛，避免静默成功。
     */
    private suspend fun gitChecked(
        vararg args: String
    ): String {
        val cmd = buildString {
            append("git")
            args.forEach { append(' '); append(shellQuote(it)) }
        }
        val result = engine.runCommandSyncWithExit(cmd, workspaceRepository.currentPath())
        if (result.exitCode == 0) return result.output
        throw GitCommandFailureException(result.output.ifBlank { "git 退出码 ${result.exitCode}" })
    }

    /** 拼命令并跑（不判退出码），[git] 与 [gitChecked] 复用。 */
    private suspend fun gitRaw(args: Array<out String>): String {
        val cmd = buildString {
            append("git")
            args.forEach { append(' '); append(shellQuote(it)) }
        }
        return engine.runCommandSync(cmd, workspaceRepository.currentPath())
    }

    /** 当前工作区是否处于一个 git 工作树内。 */
    suspend fun isRepo(): Boolean {
        val out = git("rev-parse", "--is-inside-work-tree")
        return out.trim() == "true"
    }

    /** 在当前工作区初始化 git 仓库（`git init`）。据退出码判成败，失败抛 [GitCommandFailureException]。 */
    suspend fun initRepo(): String = gitChecked("init")

    /** 是否已配置至少一个远程仓库（`git remote` 输出非空）。拉取/推送前据此门控。 */
    suspend fun hasRemote(): Boolean = git("remote").trim().isNotEmpty()

    /** `git status` 聚合视图。 */
    suspend fun status(): GitStatus {
        val raw = git("status", "--porcelain=v1", "-b")
        val lines = raw.split('\n').map { it.removeSuffix("\r") }

        var branch = "(unknown)"
        var ahead = 0
        var behind = 0
        val staged = mutableListOf<GitFileChange>()
        val unstaged = mutableListOf<GitFileChange>()
        val untracked = mutableListOf<String>()

        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("## ")) {
                val header = line.removePrefix("## ")
                // 形如 "main" / "main...origin/main" / "main...origin/main [ahead 2, behind 1]"
                val tracking = header.substringBefore(" [")
                branch = tracking.substringBefore("...").ifBlank { tracking }
                val bracket = header.substringAfter(" [", "")
                if (bracket.isNotBlank()) {
                    bracket.removeSuffix("]").split(",").forEach { tok ->
                        val t = tok.trim()
                        val n = t.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (t.startsWith("ahead")) ahead = n
                        else if (t.startsWith("behind")) behind = n
                    }
                }
                continue
            }
            if (line.length < 3) continue
            val x = line[0]
            val y = line[1]
            val rawPath = line.substring(3)
            // 重命名形如 "old -> new"，展示新路径。
            val path = rawPath.substringAfter(" -> ").trim()

            if (x == '?' && y == '?') {
                untracked.add(path)
                continue
            }
            if (x != ' ' && x != '?') {
                staged.add(GitFileChange(path, x.toString(), staged = true))
            }
            if (y != ' ' && y != '?') {
                unstaged.add(GitFileChange(path, y.toString(), staged = false))
            }
        }
        return GitStatus(branch, ahead, behind, staged, unstaged, untracked)
    }

    /** 本地 + 远程分支列表，当前分支高亮。 */
    suspend fun branches(): List<GitBranch> {
        val current = runCatching { git("rev-parse", "--abbrev-ref", "HEAD").trim() }
            .getOrDefault("")
        val localRaw = git("for-each-ref", "--format=%(refname:short)", "refs/heads")
        val remoteRaw = git("for-each-ref", "--format=%(refname:short)", "refs/remotes")
        val result = mutableListOf<GitBranch>()
        for (name in localRaw.split('\n').map { it.removeSuffix("\r").trim() }.filter { it.isNotBlank() }) {
            result.add(GitBranch(name, current = name == current && current.isNotBlank() && current != "HEAD", remote = false))
        }
        for (name in remoteRaw.split('\n').map { it.removeSuffix("\r").trim() }.filter { it.isNotBlank() }) {
            result.add(GitBranch(name, current = false, remote = true))
        }
        return result
    }

    /** 最近 [limit] 条提交。 */
    suspend fun log(limit: Int = 50): List<GitCommit> {
        val raw = git("log", "--pretty=format:%H|%h|%an|%ar|%s", "-n", limit.toString())
        if (raw.isBlank() || raw.startsWith("fatal:")) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.removeSuffix("\r").split('|', limit = 5)
            if (parts.size < 5) null
            else GitCommit(parts[0], parts[1], parts[2], parts[3], parts[4])
        }
    }

    /**
     * 拓扑图视图：提交（含父哈希）+ 引用（分支/标签）+ 泳道布局。
     *
     * `git log --pretty=format:...%P` 在常规字段后追加父哈希列表（空格分隔，多个即合并提交）；
     * `git for-each-ref` 一次拿全部分支/标签及其指向的提交哈希与是否 HEAD。提交与引用解析后，
     * 在 [Dispatchers.Default] 上跑纯 Kotlin 泳道分配算法（见 [computeLanes]），产出 [GitGraph]。
     * 失败（非仓库/无提交）返回 [GitGraph.EMPTY]，不抛——UI 据空图显示空态。
     *
     * 首批加载取 [GRAPH_PAGE_SIZE] 条；[hasMore] 按本批返回条数是否达到页大小判定，UI 滚到底据此
     * 触发 [graphAppend] 追加下一页。提交数不足页大小时 hasMore=false，UI 不再显示加载更多。
     */
    suspend fun graph(limit: Int = GRAPH_PAGE_SIZE): GitGraph =
        graphAppend(emptyList(), limit)

    /**
     * 分页加载下一批提交并整体重算泳道布局。
     *
     * 从已加载数量处 `git log --skip=<existingCommits.size> -n <limit>` 取下一批提交，与 [existingCommits]
     * 合并为完整列表后整体调 [computeLanes] 重算布局——泳道分配依赖全局子父顺序，父提交可能跨批次
     * 指向已加载提交，单算新批次会让列号与旧布局冲突导致连线断裂，故必须整图重算。重算开销 O(已加载数)，
     * 移动端纯内存 Kotlin 计算毫秒级，可接受。
     *
     * 返回含全部已加载提交（旧+新）的完整 [GitGraph]，ViewModel 直接整体替换 state.graph；UI 的 LazyColumn
     * 因 `item(key=...)` 机制只增量重组新行不闪烁。[hasMore] = 新批次返回条数达到页大小（未到则历史末尾）。
     */
    suspend fun graphAppend(
        existingCommits: List<GraphCommit>,
        limit: Int = GRAPH_PAGE_SIZE
    ): GitGraph = withContext(Dispatchers.Default) {
        val skip = existingCommits.size
        val logRaw = runCatching { git("log", "--pretty=format:%H|%h|%an|%ar|%s|%P", "--skip", skip.toString(), "-n", limit.toString()) }
            .getOrDefault("")
        if (logRaw.isBlank() || logRaw.startsWith("fatal:")) {
            return@withContext if (existingCommits.isEmpty()) GitGraph.EMPTY
            else buildGraph(existingCommits, hasMore = false)
        }

        val newCommits = parseGraphCommits(logRaw)
        if (newCommits.isEmpty() && existingCommits.isEmpty()) return@withContext GitGraph.EMPTY

        // 合并去重：新批次理论上不会与已加载重复（--skip 跳过已加载数量），但防御性按 hash 去重保顺序。
        val existingHashes = existingCommits.mapTo(HashSet()) { it.hash }
        val merged = existingCommits + newCommits.filter { it.hash !in existingHashes }
        buildGraph(merged, hasMore = newCommits.size >= limit)
    }

    /** 解析 `git log --pretty=format:%H|%h|%an|%ar|%s|%P` 输出为 [GraphCommit] 列表。 */
    private fun parseGraphCommits(raw: String): List<GraphCommit> =
        raw.split('\n').mapNotNull { line ->
            val parts = line.removeSuffix("\r").split('|', limit = 6)
            if (parts.size < 6) null
            else {
                val parents = parts[5].split(' ').filter { it.isNotBlank() }
                GraphCommit(parts[0], parts[1], parts[2], parts[3], parts[4], parents)
            }
        }

    /** 对完整提交列表重算泳道布局并组装 [GitGraph]。 */
    private suspend fun buildGraph(commits: List<GraphCommit>, hasMore: Boolean): GitGraph {
        if (commits.isEmpty()) return GitGraph.EMPTY
        val refs = loadRefs()
        val layout = computeLanes(commits)
        return GitGraph(
            commits,
            refs,
            layout.lanes,
            layout.edges,
            layout.activeTopLanes,
            layout.activeBottomLanes,
            layout.activeLanes,
            layout.maxLane,
            hasMore
        )
    }

    /**
     * 一次拉取所有分支/标签及其指向的提交哈希，解析为 `commitHash -> List<GitGraphRef>`。
     * `%(refname:short)` 短名、`%(objectname)` 指向的完整哈希、`%(HEAD)` 标记当前分支（输出 `*` 或空）。
     * 本地分支（refs/heads）isRemote=false，远程分支（refs/remotes）isRemote=true，标签（refs/tags）isBranch=false。
     */
    private suspend fun loadRefs(): Map<String, List<GitGraphRef>> {
        val raw = runCatching {
            git(
                "for-each-ref",
                "--format=%(refname:short)|%(objectname)|%(HEAD)|%(refname)",
                "refs/heads", "refs/remotes", "refs/tags"
            )
        }.getOrDefault("")
        if (raw.isBlank() || raw.startsWith("fatal:")) return emptyMap()
        val result = mutableMapOf<String, MutableList<GitGraphRef>>()
        for (line in raw.split('\n')) {
            val l = line.removeSuffix("\r").trim()
            if (l.isBlank()) continue
            val parts = l.split('|', limit = 4)
            if (parts.size < 4) continue
            val name = parts[0]
            val hash = parts[1]
            val isHead = parts[2].trim() == "*"
            val refname = parts[3]
            val isBranch = refname.startsWith("refs/heads") || refname.startsWith("refs/remotes")
            val isRemote = refname.startsWith("refs/remotes")
            result.getOrPut(hash) { mutableListOf() }
                .add(GitGraphRef(name, isBranch, isHead && !isRemote, isRemote))
        }
        return result
    }

    /**
     * 纯 Kotlin 泳道分配算法（IDE 风格拓扑布局）。
     *
     * 维护「活跃泳道」数组 `active: Array<String?>`，每个槽位记录当前占据该列的提交哈希。
     * 按提交从新到旧顺序处理（与 git log 输出一致）：
     * - 找当前提交是否已占据某泳道（其哈希已在 active 中，即被某子提交的父引用占位）→ 复用该泳道为当前列。
     * - 未占据则取最左侧空闲槽位为新泳道（该提交是某分支的最新提交，未被任何子提交引用）。
     * - 记录当前提交的 lane。
     * - 快照此刻 [active] 中所有非空槽位的列号为该提交的 [GitGraph.activeLanes]：这些泳道
     *   从上一行延续下来并穿过本行，UI 需为它们画贯穿竖线，避免分支支线在中间行断裂。
     * - 处理父提交：第一个父若未占位则复用当前提交的泳道（主线延续），若已在其他活跃泳道则
     *   生成跨列出边并释放当前泳道（主线跳列）；其余父（合并的第二个及以后）各自分配泳道——
     *   若该父已在某活跃泳道则复用，否则取最左空闲槽位占位。
     * - 当前提交无父（根提交）则释放其泳道。
     * - 同时为每个父生成一条 [GraphEdge]：第一父跨列时为出边（fromLane=当前列, toLane=父列,
     *   lane=当前列）；第一父同列时为竖直边。其余父为合并入边（fromLane=当前列, toLane=父列,
     *   lane=父列），跨列即合并折线。
     *
     * 父提交可能不在本次 limit 范围内（哈希在 active 中查不到对应提交记录），此时仍保留泳道占位
     * 以维持连线连续性，直至其被后续提交释放。
     */
    private fun computeLanes(commits: List<GraphCommit>): GraphLayout {
        val commitMap = commits.associateBy { it.hash }
        val lanes = mutableMapOf<String, Int>()
        val edges = mutableListOf<GraphEdge>()
        val activeTopLanes = mutableMapOf<String, List<Int>>()
        val activeBottomLanes = mutableMapOf<String, List<Int>>()
        val activeLanes = mutableMapOf<String, List<Int>>()
        // 活跃泳道：槽位索引即列号，值为占据该列的提交哈希（或 null=空闲）。
        val active = mutableListOf<String?>()
        var maxLane = 0

        for (commit in commits) {
            // 当前提交是否已被某子提交的父引用占位。
            var lane = active.indexOf(commit.hash)
            if (lane < 0) {
                // 未占位：取最左空闲槽位。
                lane = active.indexOf(null)
                if (lane < 0) {
                    lane = active.size
                    active.add(commit.hash)
                } else {
                    active[lane] = commit.hash
                }
            }
            if (lane > maxLane) maxLane = lane
            lanes[commit.hash] = lane

            // 快照上半段（0 -> centerY）活跃泳道：此时 active 反应进入本节点前上方的分支通道。
            val topSnapshot = active.mapIndexedNotNull { idx, h -> if (h != null) idx else null }
            activeTopLanes[commit.hash] = topSnapshot

            if (commit.parents.isEmpty()) {
                // 根提交：释放当前泳道后快照下半段。
                active[lane] = null
                val botSnapshot = active.mapIndexedNotNull { idx, h -> if (h != null) idx else null }
                activeBottomLanes[commit.hash] = botSnapshot
                activeLanes[commit.hash] = botSnapshot
                continue
            }

            // 第一父复用当前泳道（主线延续）。
            val parents = commit.parents
            val firstParent = parents[0]
            val firstParentLane = active.indexOf(firstParent)
            if (firstParentLane >= 0 && firstParentLane != lane) {
                edges.add(GraphEdge(lane, firstParentLane, lane, isMergeIn = false))
                active[lane] = null
            } else {
                edges.add(GraphEdge(lane, lane, lane, isMergeIn = false))
                active[lane] = firstParent
            }

            // 其余父（合并的第二个及以后）：优先复用活跃列表中后续能继承该父的泳道，否则分配新泳道。
            for (i in 1 until parents.size) {
                val p = parents[i]
                val existing = active.indexOf(p)
                val pLane = if (existing >= 0) {
                    existing
                } else {
                    // 查找 active 中是否有占位提交 X（非当前列），X 的父引用直接包含 p。
                    // 若有，说明 p 是 X 的父，会在 X 处理完后自然继承该列，入边直接指向该列而不抢占 active。
                    val reuse = active.indexOfFirst { aHash ->
                        aHash != null && active.indexOf(aHash) != lane && commitMap[aHash]?.parents?.contains(p) == true
                    }
                    if (reuse >= 0) {
                        reuse
                    } else {
                        val free = active.indexOf(null)
                        if (free < 0) {
                            active.add(p)
                            active.size - 1
                        } else {
                            active[free] = p
                            free
                        }
                    }
                }
                if (pLane > maxLane) maxLane = pLane
                edges.add(GraphEdge(lane, pLane, pLane, isMergeIn = true))
            }

            // 快照下半段（centerY -> height）活跃泳道：在所有父分配完毕后反应离开本节点向下的分支通道。
            val botSnapshot = active.mapIndexedNotNull { idx, h -> if (h != null) idx else null }
            activeBottomLanes[commit.hash] = botSnapshot
            activeLanes[commit.hash] = botSnapshot
        }
        return GraphLayout(lanes, edges, activeTopLanes, activeBottomLanes, activeLanes, maxLane)
    }

    /** [computeLanes] 的输出：泳道映射 + 边列表 + 每行活跃泳道快照 + 最大列号。 */
    private class GraphLayout(
        val lanes: Map<String, Int>,
        val edges: List<GraphEdge>,
        val activeTopLanes: Map<String, List<Int>>,
        val activeBottomLanes: Map<String, List<Int>>,
        val activeLanes: Map<String, List<Int>>,
        val maxLane: Int
    )

    /**
     * 某次提交改动的文件清单。用 `diff-tree --root` 以兼容无父的根提交；`--no-renames` 让重命名
     * 退化为「删除旧 + 新增新」，状态码取首字符即可复用 [com.aicode.feature.git.domain.model.GitFileChange]。
     * 返回空列表表示该提交无文件改动（如空提交）。
     */
    suspend fun commitFiles(hash: String): List<GitFileChange> {
        val raw = git("diff-tree", "--no-commit-id", "-r", "--root", "--name-status", "--no-renames", hash)
        return withContext(Dispatchers.Default) {
            raw.lineSequence().mapNotNull { line ->
                val l = line.removeSuffix("\r").trim()
                val tab = l.indexOf('\t')
                if (l.isBlank() || tab < 0) null
                else {
                    val status = l.substring(0, tab).trim()
                    val path = l.substring(tab + 1).trim()
                    GitFileChange(path, status, staged = false)
                }
            }.toList()
        }
    }

    suspend fun stage(path: String) = gitChecked("add", "--", path)
    suspend fun unstage(path: String) = gitChecked("reset", "HEAD", "--", path)
    suspend fun stageAll() = gitChecked("add", "-A")
    suspend fun unstageAll() = gitChecked("reset")
    suspend fun commit(message: String) = gitChecked("commit", "-m", message)

    /**
     * 拉取：直接 `git pull`，凭据由容器 `credential.helper` 链自动注入——`store` 命中已有凭据秒过，
     * 未命中时自定义 helper 经文件 IPC 触发 app 弹窗回填，git 自动续跑（见 [CredentialRequestBridge]）。
     * 故不再在此预查 host 凭据：三端（UI/终端/AI）共用同一 helper 兜底，逻辑单一来源。remote 不存在
     * 或真实失败由 [gitChecked] 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    suspend fun pull(): String = gitChecked("pull")

    /**
     * 推送：有上游则 `git push`；当前分支无上游时自动 `git push --set-upstream <remote> <branch>` 首推建关联，
     * 仿 Win/Mac git 客户端「首次推送自动建上游」体验，避免用户撞到 `fatal: has no upstream branch` 原始报错。
     * remote 取 `git remote` 首个（多 remote 默认第一；无 remote 已被上层 hasRemote 门控挡掉）；
     * 分支取 `git rev-parse --abbrev-ref HEAD`。凭据仍由容器 credential.helper 链兜底注入。
     */
    suspend fun push(): String {
        val hasUpstream = runCatching { git("rev-parse", "--abbrev-ref", "@{upstream}").trim() }
            .getOrDefault("")
            .takeIf { it.isNotBlank() && it != "HEAD" && !it.startsWith("fatal") } != null
        if (hasUpstream) return gitChecked("push")
        val remote = git("remote").split('\n').firstOrNull { it.removeSuffix("\r").isNotBlank() }?.removeSuffix("\r")?.trim()
            ?: throw GitCommandFailureException("未配置远程仓库")
        val branch = git("rev-parse", "--abbrev-ref", "HEAD").removeSuffix("\r").trim()
            .takeIf { it.isNotBlank() && it != "HEAD" }
            ?: throw GitCommandFailureException("无法确定当前分支（处于 detached HEAD）")
        return gitChecked("push", "--set-upstream", remote, branch)
    }

    /** 本地标签列表，按创建时间倒序（最新在前）。 */
    suspend fun listTags(): List<GitTag> {
        val raw = git("tag", "--sort=-creatordate", "--format=%(refname:short) %(objectname:short)")
        if (raw.isBlank() || raw.startsWith("fatal:")) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val l = line.removeSuffix("\r").trim()
            if (l.isBlank()) return@mapNotNull null
            val parts = l.split(' ', limit = 2)
            if (parts.size < 2) return@mapNotNull null
            GitTag(parts[0], parts[1])
        }
    }

    /**
     * 创建新分支。name 为新分支名；startPoint 为基准分支名（null/空 → 从当前 HEAD）；
     * checkout=true 则创建并切换（`git checkout -b`），否则仅创建不切换（`git branch`）。
     * 起点不存在或分支名非法时由 [gitChecked] 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    suspend fun createBranch(name: String, startPoint: String?, checkout: Boolean): String {
        return if (checkout) {
            if (startPoint.isNullOrBlank()) gitChecked("checkout", "-b", name)
            else gitChecked("checkout", "-b", name, startPoint)
        } else {
            if (startPoint.isNullOrBlank()) gitChecked("branch", name)
            else gitChecked("branch", name, startPoint)
        }
    }

    /**
     * 安全删除本地分支（`git branch -d`）：仅删除已合并到上游的分支，未合并时 git 报错
     * 由 [gitChecked] 据退出码抛 [GitCommandFailureException]，上层 toast。当前分支不可删（git 自身拦截）。
     */
    suspend fun deleteBranch(name: String): String = gitChecked("branch", "-d", name)

    /**
     * 重命名本地分支（`git branch -m <old> <new>`）。当前分支也可重命名：传单参数 `git branch -m <new>`
     * 重命名当前分支；这里统一用双参数形式，由上层保证 oldName 非空。名字非法或已存在时由 [gitChecked]
     * 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    suspend fun renameBranch(oldName: String, newName: String): String =
        gitChecked("branch", "-m", oldName, newName)

    /**
     * 创建轻量标签（`git tag <name>`），指向当前 HEAD。附注标签需消息且交互复杂，暂只做轻量标签；
     * 名字非法或已存在时由 [gitChecked] 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    suspend fun createTag(name: String): String = gitChecked("tag", name)

    /**
     * 删除本地标签（`git tag -d <name>`）。不存在时由 [gitChecked] 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    suspend fun deleteTag(name: String): String = gitChecked("tag", "-d", name)

    /**
     * 删除远程分支（`git push <remote> --delete <branch>`）。ref 形如 `origin/feature`，拆出 remote 与分支名；
     * 无 remote 前缀时按 `origin` 兜底。会改远端，失败由 [gitChecked] 抛 [GitCommandFailureException]。
     */
    suspend fun deleteRemoteBranch(ref: String): String {
        val remote = ref.substringBefore('/', "origin")
        val branch = ref.substringAfter('/', ref)
        return gitChecked("push", remote, "--delete", branch)
    }

    /**
     * 切换到指定分支或标签。branch 可以是本地分支名、远程分支名或 tag 名。
     * 远程分支用 `git checkout -b <local> <remote>` 创建本地跟踪分支，去掉远程前缀（如 origin/）。
     */
    suspend fun checkout(branch: String, isRemote: Boolean): String {
        return if (isRemote) {
            val localName = branch.substringAfter('/', branch)
            gitChecked("checkout", "-b", localName, "--track", branch)
        } else {
            gitChecked("checkout", branch)
        }
    }

    /**
     * 写入提交署名，**优先项目级**：当前工作区（/workspace/.git/config）已有项目级署名时写 local，
     * 否则写 global（容器 `GIT_CONFIG_GLOBAL=/root/.aicode/.gitconfig`，持久挂载，跨 rootfs 升级不丢）作默认。
     * 这样 UI 与终端 `git config user.name` 读到的同一份——优先项目级、无则退全局，对齐 git 自身解析顺序。
     * 空值跳过对应项不动现有配置。由 UI 在用户保存身份时显式调用。
     */
    suspend fun setUserIdentity(name: String, email: String) {
        if (name.isNotBlank()) gitChecked("config", if (hasLocalConfig("user.name")) "--local" else "--global", "user.name", name)
        if (email.isNotBlank()) gitChecked("config", if (hasLocalConfig("user.email")) "--local" else "--global", "user.email", email)
    }

    /** 当前工作区是否有项目级（`--local`）配置值。非 git 仓库或无值时返回 false。 */
    private suspend fun hasLocalConfig(key: String): Boolean =
        runCatching { git("config", "--local", "--get", key).trim() }.getOrDefault("").isNotBlank()

    /** 读取 git 当前实际生效的 user.name（按 git 解析顺序：local→global→system），UI 回显与提交按钮判空用。失败返回空串。 */
    suspend fun getUserName(): String =
        runCatching { git("config", "--get", "user.name").trim() }.getOrDefault("").removeSuffix("\r")

    /** 读取 git 当前实际生效的 user.email（local→global→system），UI 回显与编辑框初值。失败返回空串。 */
    suspend fun getUserEmail(): String =
        runCatching { git("config", "--get", "user.email").trim() }.getOrDefault("").removeSuffix("\r")

    /**
     * 写入仓库地址 remote.origin.url，**仅写项目级**：remote.origin.url 是单个仓库的远端地址，
     * 与 user.name/email 这种「身份默认值」不同——它绝不该写 global。一旦写进 global，后续 `git clone`
     * 任何新仓库时，git 会同时解析出全局与 clone 写入的 local 两个 origin.url，导致 fetch 走全局旧值、
     * push 出现多条指向不同仓库的条目（实测复现），clone 下来的仓库 fetch 到的是错误仓库。
     *
     * 故此处始终写 `--local`（当前工作区须是 git 仓库），非仓库时抛 [GitCommandFailureException] 提示用户，
     * 不再退到 global 兑底。同时顺手清掉全局可能残留的 remote.origin.url（历史版本误写下的），一次性消除污染。
     */
    suspend fun setRepoUrl(url: String) {
        if (url.isNotBlank()) {
            gitChecked("config", "--local", "remote.origin.url", url)
        } else {
            // 空值删除 local 的 remote.origin.url，git config --unset 对不存在的 key 返回非零但不影响其它配置
            runCatching { gitChecked("config", "--local", "--unset", "remote.origin.url") }
        }
        // 清除全局残留的 remote.origin.url（历史误写），避免污染后续 git clone 等命令。
        runCatching { git("config", "--global", "--unset", "remote.origin.url") }
    }

    /** 读取 git 当前实际生效的 remote.origin.url（local→global→system），UI 回显与编辑框初值。失败返回空串。 */
    suspend fun getRepoUrl(): String =
        runCatching { git("config", "--get", "remote.origin.url").trim() }.getOrDefault("").removeSuffix("\r")

    /**
     * 读取指定 ref（提交/分支/标签）下某文件的完整内容（`git show <ref>:<path>`）。
 * 用于提交文件 diff：取 `<hash>^:<path>`（改动前）与 `<hash>:<path>`（改动后）对比。
 * 文件在指定 ref 不存在时（如新增文件的首个提交）git 报错输出 `fatal:`，此处检测到即返回空串，
 * 上层据空串判定为「新增/删除」，整个文件按全增或全删呈现。
 */
    suspend fun showFileContent(ref: String, path: String): String {
        val out = git("show", "$ref:$path")
        // git show 对不存在的路径输出 fatal 到 stderr，runCommandSync 合并了 stdout+stderr。
        // 检测到 fatal 前缀视为该版本无此文件，返回空串让 diff 按全增/全删处理。
        return if (out.startsWith("fatal:") || out.startsWith("error:")) "" else out
    }

    /**
     * 读取工作区当前文件内容。用于工作区改动 diff：与 `HEAD:<path>` 对比看出未暂存的改动。
 * 文件不存在或读取失败返回空串。经容器内直接读文件而非 git show，因为工作区文件即当前内容。
 */
    suspend fun worktreeFileContent(path: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                java.io.File(workspaceRepository.currentPath(), path).takeIf { it.isFile }?.readText() ?: ""
            }.getOrDefault("")
        }

    /**
     * 对单个 shell 参数做单引号转义。含「安全字符」之外的字符（空格、`|`、`$`、反引号、`*` 等）时
     * 整体包单引号，内嵌单引号用 `'\''` 关闭-转义-重开。格式串（带 `|`、`%(...)`）、含空格路径、
     * 提交消息均由此安全传递。注意 `|` 是 shell 管道符，**不可**列入安全集。
     */
    private fun shellQuote(arg: String): String {
        if (arg.isEmpty()) return "''"
        if (arg.all { it.isLetterOrDigit() || it in "_.@/:=+,%-" }) return arg
        return "'" + arg.replace("'", "'\\''") + "'"
    }
}
