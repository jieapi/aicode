package com.aicodeeditor.feature.agent.domain.permission

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具授权策略引擎：在弹窗之前评估一次工具调用应「自动放行 / 自动拒绝 / 询问」，并在用户选择
 * 「始终允许」后把规则记忆下来。
 *
 * 评估顺序（execute_command）：
 *   1) 任一段命中 DENY 规则 → DENY（即便命令不可静态判定，DENY 也对已解析的段生效；可覆盖内置白名单）；
 *   2) 命令不可静态判定（含命令替换/分组/绝对路径重定向等）→ ASK，且不可记忆；
 *   3) 所有段都命中内置安全白名单（ls/git status 等只读命令，见 [BuiltInSafeCommands]）→ ALLOW；
 *   4) 所有段都命中已记忆的 ALLOW 规则 → ALLOW；
 *   5) 否则 → ASK，可记忆前缀为各段的「程序名」或「程序名+子命令」（对 git/npm 等子命令分发器，
 *      记 `git pull` 而非 `git`，使不同子命令各自独立授权）。
 * 非 shell 的 ASK 工具按整工具（pattern=`*`）匹配。
 */
@Singleton
class ToolPermissionPolicyEngine @Inject constructor(
    private val rulesRepo: PermissionRulesRepository
) {
    private companion object {
        /** 以 `command` 参数承载 shell 命令、按命令前缀做指令级匹配的工具。 */
        val SHELL_TOOLS = setOf("execute_command")

        /**
         * 合并后的终端会话工具：其 `start` 动作承载 shell 命令，需走指令级前缀匹配；
         * `send`/`read` 动作不承载命令，按整工具匹配。故在 [evaluate] 中按 action 路由。
         */
        const val TERMINAL_TOOL = "terminal"
        const val TERMINAL_SHELL_ACTION = "start"
    }

    enum class Verdict { ALLOW, DENY, ASK }

    /**
     * @param verdict 评估结论。
     * @param rememberablePatterns 当 [verdict] 为 ASK 时，「始终允许」会记忆的模式；为空表示不可记忆
     *   （命令不可静态判定，只能单次放行）。
     */
    data class EvalResult(val verdict: Verdict, val rememberablePatterns: List<String>)

    suspend fun evaluate(toolName: String, args: Map<String, JsonElement>): EvalResult {
        val rules = rulesRepo.loadEffectiveForCurrentProject().filter { it.toolName == toolName }
        return if (isShellTool(toolName, args)) evaluateShell(rules, args) else evaluateGeneric(rules)
    }

    /** 是否按 shell 命令前缀匹配：[SHELL_TOOLS] 中的工具，或终端工具的 start 动作。 */
    private fun isShellTool(toolName: String, args: Map<String, JsonElement>): Boolean {
        if (toolName in SHELL_TOOLS) return true
        if (toolName == TERMINAL_TOOL) {
            val action = (args["action"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            return action == TERMINAL_SHELL_ACTION
        }
        return false
    }

    /** 把「始终允许」的选择落库为 ALLOW 规则（去重交给仓库）。 */
    suspend fun remember(toolName: String, patterns: List<String>, scope: PermissionScope) {
        patterns.distinct().forEach { pattern ->
            rulesRepo.add(scope, PermissionRule(toolName, pattern, PermissionDecision.ALLOW))
        }
    }

    private fun evaluateGeneric(rules: List<PermissionRule>): EvalResult {
        val whole = rules.filter { it.pattern == PermissionRule.WHOLE_TOOL }
        if (whole.any { it.decision == PermissionDecision.DENY }) return EvalResult(Verdict.DENY, emptyList())
        if (whole.any { it.decision == PermissionDecision.ALLOW }) return EvalResult(Verdict.ALLOW, emptyList())
        return EvalResult(Verdict.ASK, listOf(PermissionRule.WHOLE_TOOL))
    }

    private fun evaluateShell(rules: List<PermissionRule>, args: Map<String, JsonElement>): EvalResult {
        val command = (args["command"] as? JsonPrimitive)?.content
            ?: return EvalResult(Verdict.ASK, emptyList())

        val analysis = ShellCommandParser.analyze(command)
        val allow = rules.filter { it.decision == PermissionDecision.ALLOW }
        val deny = rules.filter { it.decision == PermissionDecision.DENY }

        // 1) DENY 优先（含对内置安全白名单的覆盖）：任一段命中 DENY 即拒。
        if (analysis.segments.any { seg -> deny.any { ShellCommandParser.matches(it.pattern, seg) } }) {
            return EvalResult(Verdict.DENY, emptyList())
        }

        // 2) 不可静态判定 → 必须弹窗、不可记忆（内置白名单也不适用）。
        if (!analysis.analyzable) return EvalResult(Verdict.ASK, emptyList())

        // 3) 内置安全白名单：每段都命中安全前缀（ls/git status 等）→ 自动放行，不弹窗。
        if (analysis.segments.isNotEmpty() &&
            analysis.segments.all { BuiltInSafeCommands.isSafe(it) }
        ) {
            return EvalResult(Verdict.ALLOW, emptyList())
        }

        // 4) 已记忆的 ALLOW：每段都命中 → 放行。
        val allAllowed = analysis.segments.isNotEmpty() &&
            analysis.segments.all { seg -> allow.any { ShellCommandParser.matches(it.pattern, seg) } }
        if (allAllowed) return EvalResult(Verdict.ALLOW, emptyList())

        // 5) 否则弹窗，可记忆前缀（子命令分发器记 程序名+子命令）。
        val rememberable = analysis.segments.mapNotNull { ShellCommandParser.rememberablePrefix(it) }.distinct()
        return EvalResult(Verdict.ASK, rememberable)
    }
}
