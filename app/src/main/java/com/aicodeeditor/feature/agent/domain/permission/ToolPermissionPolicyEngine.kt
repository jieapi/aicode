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
 *   1) 任一段命中 DENY 规则 → DENY（即便命令不可静态判定，DENY 也对已解析的段生效）；
 *   2) 命令不可静态判定（含命令替换/分组/绝对路径重定向等）→ ASK，且不可记忆；
 *   3) 所有段都命中 ALLOW 规则 → ALLOW；
 *   4) 否则 → ASK，可记忆前缀为各段程序名。
 * 非 shell 的 ASK 工具按整工具（pattern=`*`）匹配。
 */
@Singleton
class ToolPermissionPolicyEngine @Inject constructor(
    private val rulesRepo: PermissionRulesRepository
) {
    private companion object {
        /** 以 `command` 参数承载 shell 命令、按命令前缀做指令级匹配的工具。 */
        val SHELL_TOOLS = setOf("execute_command", "run_background_command")
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
        return if (toolName in SHELL_TOOLS) evaluateShell(rules, args) else evaluateGeneric(rules)
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

        if (analysis.segments.any { seg -> deny.any { ShellCommandParser.matches(it.pattern, seg) } }) {
            return EvalResult(Verdict.DENY, emptyList())
        }

        if (!analysis.analyzable) return EvalResult(Verdict.ASK, emptyList())

        val allAllowed = analysis.segments.isNotEmpty() &&
            analysis.segments.all { seg -> allow.any { ShellCommandParser.matches(it.pattern, seg) } }
        if (allAllowed) return EvalResult(Verdict.ALLOW, emptyList())

        val rememberable = analysis.segments.mapNotNull { ShellCommandParser.programPrefix(it) }.distinct()
        return EvalResult(Verdict.ASK, rememberable)
    }
}
