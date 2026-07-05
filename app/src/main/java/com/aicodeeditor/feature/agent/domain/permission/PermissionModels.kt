package com.aicodeeditor.feature.agent.domain.permission

import kotlinx.serialization.Serializable

/**
 * 工具授权规则的作用域。scope 不存在规则对象里，而是由「存在哪个文件」隐含：
 * 项目文件（`.aicode/permissions.json`）或全局文件。管理界面「提升为全局」= 从项目文件删、往全局文件加。
 */
enum class PermissionScope { PROJECT, GLOBAL }

/**
 * 规则的判定方向。弹窗的「始终允许」只会产生 [ALLOW]；[DENY] 目前仅由管理界面/未来的手动规则使用，
 * 评估时 DENY 跨 scope 优先于 ALLOW（安全优先：全局禁可挡项目允许）。
 */
enum class PermissionDecision { ALLOW, DENY }

/**
 * 授权弹窗的用户选择。
 * - [REJECT]：拒绝本次调用，不记忆。
 * - [ONCE]：仅放行本次，不记忆。
 * - [ALWAYS]：放行并记忆为规则（弹窗路径固定写入当前项目 scope）。
 */
enum class PermissionChoice { REJECT, ONCE, ALWAYS }

/**
 * 一条工具授权规则。
 *
 * @param toolName 适用的工具名，如 `Bash`、`writeFile`。
 * @param pattern 匹配模式。对 shell 命令是「命令前缀」，按 token 前缀匹配：子命令分发器记
 *   `git pull`（仅命中 `git pull ...`，不命中 `git clone`），普通程序记 `cat`/`ls`（命中其所有调用）；
 *   对非 shell 工具是通配 `*`（整工具匹配）。
 * @param decision 判定方向，默认 [PermissionDecision.ALLOW]。
 */
@Serializable
data class PermissionRule(
    val toolName: String,
    val pattern: String,
    val decision: PermissionDecision = PermissionDecision.ALLOW
) {
    companion object {
        /** 非 shell 工具的整工具匹配模式。 */
        const val WHOLE_TOOL = "*"

        /** 从紧凑格式字符串解析为 PermissionRule。
         *  格式示例：
         *  - `"Bash"` → toolName=Bash, pattern=*, ALLOW
         *  - `"Bash(git pull)"` → toolName=Bash, pattern="git pull", ALLOW
         */
        fun fromCompact(compact: String, decision: PermissionDecision = PermissionDecision.ALLOW): PermissionRule {
            val parenIdx = compact.indexOf('(')
            return if (parenIdx >= 0 && compact.endsWith(')')) {
                val toolName = compact.substring(0, parenIdx)
                val pattern = compact.substring(parenIdx + 1, compact.length - 1)
                PermissionRule(toolName, pattern, decision)
            } else {
                PermissionRule(compact, WHOLE_TOOL, decision)
            }
        }
    }

    /** 转为紧凑格式字符串。
     *  示例：
     *  - toolName=Bash, pattern=* → `"Bash"`
     *  - toolName=Bash, pattern="git pull" → `"Bash(git pull)"`
     */
    fun toCompact(): String =
        if (pattern == WHOLE_TOOL) toolName else "$toolName($pattern)"
}

// ── 权限文件顶层结构 ──────────────────────────────────────────────

/**
 * 权限规则 JSON 文件的顶层结构。
 *
 * 示例：
 * ```json
 * {
 *   "permissions": {
 *     "allow": ["Bash(git pull)", "writeFile"],
 *     "deny": ["Bash(rm -rf /)"]
 *   }
 * }
 * ```
 */
@Serializable
data class PermissionFile(
    val permissions: PermissionSection = PermissionSection()
) {
    @Serializable
    data class PermissionSection(
        val allow: List<String> = emptyList(),
        val deny: List<String> = emptyList()
    )

    companion object {
        val EMPTY = PermissionFile()
    }
}

// PermissionFile 和 List<PermissionRule> 转换

/** 从 [PermissionFile] 解析为规则列表。 */
fun PermissionFile.toRuleList(): List<PermissionRule> =
    permissions.allow.map { PermissionRule.fromCompact(it, PermissionDecision.ALLOW) } +
            permissions.deny.map { PermissionRule.fromCompact(it, PermissionDecision.DENY) }

/** 从规则列表构建 [PermissionFile]。 */
fun List<PermissionRule>.toPermissionFile(): PermissionFile {
    val (allow, deny) = partition { it.decision == PermissionDecision.ALLOW }
    return PermissionFile(
        permissions = PermissionFile.PermissionSection(
            allow = allow.map { it.toCompact() },
            deny = deny.map { it.toCompact() }
        )
    )
}
