package com.aicodeeditor.feature.git.domain.model

/**
 * Git 领域模型。所有字段均由 [com.aicodeeditor.feature.git.domain.GitRepository] 解析
 * `git status --porcelain` / `git log` / `git for-each-ref` 的输出得到，纯数据、无 Android 依赖。
 */

/** 单个文件的改动：路径 + git status 的 XY 两字符状态码 + 是否已暂存（由 X 列决定）。 */
data class GitFileChange(
    val path: String,
    /** 形如 "M"/"A"/"D"/"R"/"??"，来自 porcelain 输出。 */
    val statusCode: String,
    val staged: Boolean
)

/** 一条提交记录。 */
data class GitCommit(
    val hash: String,
    val shortHash: String,
    val author: String,
    val date: String,
    val message: String
)

/** 一个分支引用。 */
data class GitBranch(
    val name: String,
    val current: Boolean,
    val remote: Boolean
)

/** `git status` 的聚合视图：分支跟踪信息 + 分组后的文件改动。 */
data class GitStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val staged: List<GitFileChange>,
    val unstaged: List<GitFileChange>,
    val untracked: List<String>
) {
    val hasChanges: Boolean
        get() = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty()
}

/** Git 页的三个标签页。 */
enum class GitTab { STATUS, BRANCHES, LOG }
