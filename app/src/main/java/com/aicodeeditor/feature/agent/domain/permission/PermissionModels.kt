package com.aicodeeditor.feature.agent.domain.permission

import kotlinx.serialization.Serializable

/**
 * 工具授权规则的作用域。scope 不存在规则对象里，而是由「存在哪个 store」隐含：
 * 项目 store（按项目名分桶）或全局 store。管理界面「提升为全局」= 从项目 store 删、往全局 store 加。
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
 * @param toolName 适用的工具名，如 `execute_command`、`write_file`。
 * @param pattern 匹配模式。对 shell 命令是「命令前缀」（程序名 token，如 `git`，按 token 前缀匹配，
 *   故 `git` 命中 `git status`/`git push`）；对非 shell 工具是通配 `*`（整工具匹配）。
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
    }
}
