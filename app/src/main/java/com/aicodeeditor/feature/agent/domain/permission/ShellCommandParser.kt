package com.aicodeeditor.feature.agent.domain.permission

/**
 * shell 命令的安全静态分析器——授权系统的安全核心。
 *
 * 职责：把一条命令行拆成「顶层段」并分词，同时判断这条命令是否**可被静态判定**。
 * 自动放行只在「可判定 且 每一段都命中 ALLOW 规则」时发生；任何无法静态判定的构造都把命令
 * 降级为「必须弹窗、不可记忆」，避免授权被绕过。
 *
 * 安全约定（务必维持）：
 * - 引号感知：单/双引号内的 `&& || | ;` 等不视为分隔符，不会被错误拆段。
 * - 不可静态判定（[Analysis.analyzable] = false）的触发条件：
 *     · 命令替换 `$(...)`、反引号 `` `...` ``（双引号内仍生效，单引号内不生效）；
 *     · 子shell/分组括号 `(` `)`、进程替换 `<(` `>(`（均以裸 `(` 命中）；
 *     · 输出重定向到**绝对路径**（`> /...`、`>> /...`，含引号包裹的绝对路径）——可绕过 cwd 写到工作区外；
 *     · 未闭合的引号。
 *   相对重定向（落在 cwd=工作区内）视为安全。输入重定向 `<` 不触发（读取风险较低）。
 * - 匹配按**字面首 token**：`/usr/bin/node` 不会被 `node` 规则命中（宁可多问，不误放）。
 * - 段首的环境赋值（`FOO=bar cmd`）在取程序名/匹配时被跳过，使 `FOO=bar git ...` 仍按 `git` 处理。
 */
object ShellCommandParser {

    /**
     * @param segments 顶层每一段的 token 列表（已去引号、按空白分词；重定向算子不入 token）。
     * @param analyzable 是否可静态判定；false 表示永不自动放行/记忆，只能单次放行。
     */
    data class Analysis(
        val segments: List<List<String>>,
        val analyzable: Boolean
    )

    private const val NONE = 0
    private const val SINGLE = 1
    private const val DOUBLE = 2

    /** 段首环境赋值形如 `NAME=value`。 */
    private val ENV_ASSIGN = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    fun analyze(command: String): Analysis {
        val segments = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        val token = StringBuilder()
        var tokenStarted = false
        var analyzable = true
        var quote = NONE
        // 上一个算子是输出重定向（`>`/`>>`）时为 true：下一个 token 若是绝对路径则判定为不可静态判定。
        var expectRedirectTarget = false
        var i = 0
        val n = command.length

        fun flushToken() {
            if (tokenStarted) {
                val t = token.toString()
                if (expectRedirectTarget) {
                    if (t.startsWith("/")) analyzable = false
                    expectRedirectTarget = false
                }
                current.add(t)
                token.setLength(0)
                tokenStarted = false
            }
        }
        fun flushSegment() {
            flushToken()
            if (current.isNotEmpty()) {
                segments.add(current)
                current = mutableListOf()
            }
        }

        while (i < n) {
            val c = command[i]
            when (quote) {
                SINGLE -> {
                    if (c == '\'') quote = NONE else { token.append(c); tokenStarted = true }
                    i++
                }
                DOUBLE -> {
                    when {
                        c == '"' -> { quote = NONE; tokenStarted = true; i++ }
                        // 双引号内命令替换/反引号仍会执行 → 不可静态判定。
                        c == '`' -> { analyzable = false; token.append(c); tokenStarted = true; i++ }
                        c == '$' && i + 1 < n && command[i + 1] == '(' -> {
                            analyzable = false; token.append(c); tokenStarted = true; i++
                        }
                        else -> { token.append(c); tokenStarted = true; i++ }
                    }
                }
                else -> { // 引号外
                    when {
                        c == '\'' -> { quote = SINGLE; tokenStarted = true; i++ }
                        c == '"' -> { quote = DOUBLE; tokenStarted = true; i++ }
                        c == '\\' -> {
                            // 行末续行 `\<newline>`：整体跳过；其余转义取下一字符字面值。
                            if (i + 1 < n) {
                                val nx = command[i + 1]
                                if (nx != '\n') { token.append(nx); tokenStarted = true }
                                i += 2
                            } else i++
                        }
                        // 反引号 / 分组括号 / 进程替换 `<( >(`（均含裸 `(`）→ 不可静态判定。
                        c == '`' || c == '(' || c == ')' -> {
                            analyzable = false; token.append(c); tokenStarted = true; i++
                        }
                        // 顶层段分隔符。
                        c == '\n' || c == ';' -> { flushSegment(); i++ }
                        c == '&' -> { flushSegment(); i += if (i + 1 < n && command[i + 1] == '&') 2 else 1 }
                        c == '|' -> { flushSegment(); i += if (i + 1 < n && command[i + 1] == '|') 2 else 1 }
                        // 重定向：算子本身不入 token；输出重定向标记，待目标 token 判定是否绝对路径。
                        c == '>' -> { flushToken(); i += if (i + 1 < n && command[i + 1] == '>') 2 else 1; expectRedirectTarget = true }
                        c == '<' -> { flushToken(); i++ }
                        c.isWhitespace() -> { flushToken(); i++ }
                        else -> { token.append(c); tokenStarted = true; i++ }
                    }
                }
            }
        }
        if (quote != NONE) analyzable = false // 未闭合引号：不可信
        flushSegment()
        return Analysis(segments, analyzable)
    }

    /** 跳过段首环境赋值后的有效 token（用于取程序名与匹配）。 */
    private fun effectiveTokens(tokens: List<String>): List<String> {
        var idx = 0
        while (idx < tokens.size && ENV_ASSIGN.matches(tokens[idx])) idx++
        return if (idx == 0) tokens else tokens.subList(idx, tokens.size)
    }

    /** 该段默认记忆的命令前缀 = 程序名 token；空段返回 null。 */
    fun programPrefix(tokens: List<String>): String? = effectiveTokens(tokens).firstOrNull()

    /**
     * 已知的「子命令分发器」程序：其第一个非 flag 参数是子命令（`git pull`/`npm install`/`cargo build`）。
     * 对这类程序，「始终允许」应连子命令一起记忆，避免仅记 `git` 就把 `git push`/`git reset --hard` 等
     * 一并放行。非分发器程序仍按程序名整体记忆（`cat`/`ls` 等）。
     */
    private val SUBCOMMAND_DISPATCHERS = setOf(
        "git", "gh",
        "npm", "npx", "yarn", "pnpm", "pnpx",
        "cargo", "rustup",
        "go",
        "pip", "pip3", "poetry", "uv", "conda",
        "gradle", "mvn", "sbt",
        "docker", "kubectl", "helm",
        "adb", "fastlane",
        "brew", "apt", "apt-get", "gem", "bundle",
        "az", "aws", "gcloud", "terraform", "ansible"
    )

    data class RmInfo(
        val isRm: Boolean,
        val isRecursive: Boolean,
        val isWildcard: Boolean,
        val targetPaths: List<String>
    )

    /** 解析该命令段是否为 rm 删除操作及其参数信息（是否递归、通配以及操作目标路径）。 */
    fun parseRmInfo(tokens: List<String>): RmInfo {
        val eff = effectiveTokens(tokens)
        val program = eff.firstOrNull()
        if (program != "rm" && program?.endsWith("/rm") != true) {
            return RmInfo(isRm = false, isRecursive = false, isWildcard = false, targetPaths = emptyList())
        }
        var isRecursive = false
        var isWildcard = false
        val targetPaths = mutableListOf<String>()
        var hasDoubleDash = false
        for (i in 1 until eff.size) {
            val t = eff[i]
            if (!hasDoubleDash && t == "--") {
                hasDoubleDash = true
                continue
            }
            if (!hasDoubleDash && t.startsWith("-") && t != "-") {
                if (t == "--recursive" || (!t.startsWith("--") && (t.contains('r') || t.contains('R')))) {
                    isRecursive = true
                }
                continue
            }
            if (t.contains('*') || t.contains('?')) {
                isWildcard = true
            }
            targetPaths.add(t)
        }
        return RmInfo(isRm = true, isRecursive = isRecursive, isWildcard = isWildcard, targetPaths = targetPaths)
    }

    /**
     * 「始终允许」可记忆的命令前缀：
     * - rm 命令精细处理：递归/通配符删除不可记忆；普通单文件删除精细记忆为完整命令前缀；
     * - 非分发器程序：仅程序名（`cat`/`ls`），与 [programPrefix] 一致；
     * - 子命令分发器：程序名 + 紧随其后的第一个非 flag 子命令（`git pull`/`git clone`/`npm install`），
     *   程序名后紧跟 flag（如 `git --version`）时退化为仅程序名。空段返回 null。
     *
     * 匹配仍走 [matches] 的 token 前缀语义：`git pull` 命中 `git pull origin main`，但不命中 `git clone`，
     * 故不同子命令各自独立记忆、互不串放。
     */
    fun rememberablePrefix(tokens: List<String>): String? {
        val eff = effectiveTokens(tokens)
        val program = eff.firstOrNull() ?: return null
        val rmInfo = parseRmInfo(tokens)
        if (rmInfo.isRm) {
            if (rmInfo.targetPaths.isEmpty() || rmInfo.isRecursive || rmInfo.isWildcard) {
                return null
            }
            return eff.joinToString(" ")
        }
        if (program !in SUBCOMMAND_DISPATCHERS) return program
        val subcommand = eff.getOrNull(1)?.takeIf { !it.startsWith("-") } ?: return program
        return "$program $subcommand"
    }

    /** token 前缀匹配：规则 [pattern] 的 token 序列是该段有效 token 序列的前缀即命中。 */
    fun matches(pattern: String, segmentTokens: List<String>): Boolean {
        val patternTokens = pattern.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (patternTokens.isEmpty()) return false
        val eff = effectiveTokens(segmentTokens)
        if (eff.size < patternTokens.size) return false
        for (k in patternTokens.indices) if (eff[k] != patternTokens[k]) return false
        return true
    }
}
