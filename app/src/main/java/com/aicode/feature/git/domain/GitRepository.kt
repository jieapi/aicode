package com.aicode.feature.git.domain

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import com.aicode.feature.git.domain.model.GitBranch
import com.aicode.feature.git.domain.model.GitCommit
import com.aicode.feature.git.domain.model.GitFileChange
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
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
    private val workspaceRepository: WorkspaceRepository,
    private val credentialRepository: CredentialRepository
) {
    /**
     * 执行一条 `git` 子命令，返回合并后的 stdout+stderr 文本。
     *
     * [extraConfig] 以 `key=value` 形式逐条注入为 `git -c <kv> ...`（在 args 之前），用于按命令
     * 一次性注入 HTTP 认证头而不污染 .gitconfig；默认空，所有现有调用零改动。每条经 [shellQuote]
     * 单引号转义，含空格/特殊字符的值（如 `http.extraHeader=Authorization: Basic xxx`）安全传递。
     */
    private suspend fun git(
        vararg args: String,
        extraConfig: List<String> = emptyList()
    ): String {
        val cmd = buildString {
            append("git")
            extraConfig.forEach {
                append(" -c ")
                append(shellQuote(it))
            }
            args.forEach { append(' '); append(shellQuote(it)) }
        }
        return engine.runCommandSync(cmd, workspaceRepository.currentPath())
    }

    /**
     * 解析当前仓库的「当前远程」信息，供 pull/push 注入凭据。
     *
     * 取 active remote：优先 `git rev-parse` 拿当前分支上游对应的 remote（@{upstream}），失败回退
     * `git remote` 首个。解析 url 判 [isHttps] 与 host(小写归一)。SSH 形如 `git@host:` 由正则兜底取 host，
     * 本期 SSH 不注入凭据（仅提示），但 host 仍返回便于 UI 显示。
     */
    private suspend fun resolveRemote(): RemoteInfo {
        // 当前分支上游对应的 remote 名，失败则回退 remote 列表首个。
        val name = runCatching { git("rev-parse", "--abbrev-ref", "@{upstream}").trim() }
            .getOrDefault("")
            .takeIf { it.isNotBlank() && it != "HEAD" && !it.startsWith("fatal") }
            ?.substringBefore('/')
            ?: git("remote").split('\n').firstOrNull { it.removeSuffix("\r").isNotBlank() }?.removeSuffix("\r")?.trim()
            ?: throw IllegalStateException("未配置远程仓库")

        val url = runCatching { git("remote", "get-url", name).trim() }
            .getOrDefault("")
            .removeSuffix("\r")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("无法解析远程地址: $name")

        val lower = url.lowercase()
        val isHttps = lower.startsWith("https://") || lower.startsWith("http://")
        val host = when {
            isHttps -> runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()
            else -> Regex("^(?:ssh://)?[^@]+@([^:/]+)").find(url)?.groupValues?.getOrNull(1)?.lowercase()
        } ?: ""
        return RemoteInfo(name = name, url = url, isHttps = isHttps, host = host)
    }

    private data class RemoteInfo(val name: String, val url: String, val isHttps: Boolean, val host: String)

    /** 当前工作区是否处于一个 git 工作树内。 */
    suspend fun isRepo(): Boolean {
        val out = git("rev-parse", "--is-inside-work-tree")
        return out.trim() == "true"
    }

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
        val raw = git("for-each-ref", "--format=%(refname:short)", "refs/heads", "refs/remotes")
        return raw.split('\n')
            .map { it.removeSuffix("\r").trim() }
            .filter { it.isNotBlank() }
            .map { name ->
                GitBranch(
                    name = name,
                    current = name == current && current.isNotBlank() && current != "HEAD",
                    remote = name.contains('/')
                )
            }
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

    suspend fun stage(path: String) = git("add", "--", path)
    suspend fun unstage(path: String) = git("reset", "HEAD", "--", path)
    suspend fun stageAll() = git("add", "-A")
    suspend fun unstageAll() = git("reset")
    suspend fun commit(message: String) = git("commit", "-m", message)

    /**
     * 拉取：解析当前 remote，若为 https 且有匹配 host 的凭据则注入 `Authorization: Basic` 头；
     * 无凭据或非 https（SSH）则按默认流程执行（公仓裸拉 / ssh-agent / 环境凭据）。remote 不存在抛异常，
     * 由上层 runAction 捕获后 toast。
     */
    suspend fun pull(): String {
        val remote = resolveRemote()
        return git("pull", extraConfig = authConfig(remote))
    }

    suspend fun push(): String {
        val remote = resolveRemote()
        return git("push", extraConfig = authConfig(remote))
    }

    /**
     * 据 [remote] 构造 http.extraHeader 注入项。仅 https 且能匹配到凭据时返回非空；
     * 否则返回空列表（沿用 git 默认凭据流程，不报错——公仓或已配置环境凭据情况下仍可正常）。
     * SSH remote（isHttps=false）本期不注入。
     */
    private suspend fun authConfig(remote: RemoteInfo): List<String> {
        if (!remote.isHttps || remote.host.isEmpty()) return emptyList()
        val cred = credentialRepository.findForHost(remote.host) ?: return emptyList()
        val basic = Base64.getEncoder().encodeToString("${cred.username}:${cred.token}".toByteArray(Charsets.UTF_8))
        return listOf("http.extraHeader=Authorization: Basic $basic")
    }

    /**
     * 写入 git 全局提交署名。容器 HOME=/root 固定，`--global` 落 `/root/.gitconfig`，跨工作区生效。
     * 空值跳过对应项不动现有全局配置。由 UI 在用户保存身份时显式调用，DataStore 才是真源。
     */
    suspend fun setUserGlobal(name: String, email: String) {
        if (name.isNotBlank()) git("config", "--global", "user.name", name)
        if (email.isNotBlank()) git("config", "--global", "user.email", email)
    }

    /** 读取容器内 `git config --global` 当前署名，用于 UI 回显「git 实际值」。失败返回空串。 */
    suspend fun getGlobalUserName(): String =
        runCatching { git("config", "--global", "--get", "user.name").trim() }.getOrDefault("").removeSuffix("\r")


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
