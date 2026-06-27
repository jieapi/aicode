package com.aicodeeditor.feature.agent.domain.permission

/**
 * 内置安全白名单：常见且确属「只读、无副作用、无网络、不派生子进程」的命令前缀，自动放行不弹窗。
 *
 * 这是代码内置、不可变的白名单——刻意不落盘到 DataStore、更不进 `/workspace`，契合授权层
 * 「规则绝不落盘到容器」的安全原则：容器内的 AI 无法读写本表给自己扩权。
 *
 * 设计原则（务必维持）：
 * - 只收录「无论如何传参都只读」的命令。任何能写文件、改状态、联网、或派生执行子进程的命令一律不收。
 * - 派生执行类绝不收：`node`/`python`/`ruby`/`sh -c`/`xargs`/`find -exec`/`env CMD`/`command CMD`/
 *   `source`/`.`/`eval`/`sed -i`/`awk system()`——否则 `env rm -rf /` 之类会被前缀规则误放行。
 * - 可写命令不收：`find`(可 -exec/-delete)、`sed`/`awk`(可 -i/system)、`tee`、`cp`/`mv`/`rm`/`ln`/
 *   `mkdir`/`rmdir`/`chmod`/`chown`/`touch`/`dd`。
 * - 网络类不收：`curl`/`wget`/`ssh`/`scp`/`rsync`。
 * - 泄密/可派生不收：`env`/`printenv`(泄环境变量且 `env CMD` 派生)。
 * - git 仅收「任意参数都只读」的子命令；pull/clone/push/commit/add/reset/checkout/branch/tag/merge/rebase 不收。
 *
 * 匹配走 [ShellCommandParser.matches] 的 token 前缀语义：`git status` 命中 `git status -s`，但不命中 `git push`。
 *
 * 安全兜底：用户可在管理界面加 DENY 规则覆盖本白名单（DENY 在评估中优先于一切 ALLOW，含内置）。
 * 非可静态判定的命令（含命令替换/分组/绝对路径重定向）不走本白名单，仍弹窗。
 */
object BuiltInSafeCommands {

    /** git 中「任意参数都只读」的子命令前缀；写操作子命令不在此列，仍需弹窗授权。 */
    private val GIT_READ_ONLY_PREFIXES: List<String> = listOf(
        "git status", "git log", "git diff", "git show", "git blame",
        "git rev-parse", "git ls-files", "git describe", "git for-each-ref",
        "git cat-file", "git ls-tree", "git config --get",
        "git stash list", "git remote -v"
    )

    /** 自动放行的安全命令前缀（按 token 前缀匹配）。 */
    val SAFE_PREFIXES: List<String> = listOf(
        // —— 目录/文件元信息查看（只读）——
        "ls", "pwd", "tree", "stat", "file", "du", "df",
        "basename", "dirname", "realpath", "readlink", "lsof",
        // —— 文件内容查看（只读；相对重定向写工作区文件由 ShellCommandParser 既定策略视为可接受）——
        "cat", "head", "tail", "less", "more", "wc", "nl", "tac", "paste", "column",
        // —— 搜索（只读）——
        "grep", "egrep", "fgrep", "rg", "ack", "zgrep",
        // —— 文本处理（只读，输出到 stdout；不含 sed/awk，二者可 -i/ system 改文件）——
        "sort", "uniq", "cut", "tr", "fold", "fmt", "expand", "unexpand", "rev", "comm", "diff", "cmp",
        // —— 信息/查找（只读；不含 env/printenv——会泄环境变量且可派生执行）——
        "echo", "printf", "date", "whoami", "id", "uname", "hostname", "uptime", "nproc", "cal",
        "which", "type", "whereis", "locate", "getconf"
    ) + GIT_READ_ONLY_PREFIXES

    /** 该段是否命中任一内置安全前缀。 */
    fun isSafe(segmentTokens: List<String>): Boolean =
        SAFE_PREFIXES.any { ShellCommandParser.matches(it, segmentTokens) }
}
