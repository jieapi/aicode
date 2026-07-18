package com.aicode.feature.agent.domain.tool

import kotlinx.serialization.json.JsonElement

/**
 * 仅靠会话上下文（[com.aicode.feature.agent.domain.model.AgentContext]）才能执行的工具基类。
 *
 * 统一工作流 `StatefulAgentWorkflow` 对所有工具都走 [executeWithContext]，从不直接调用 [execute]。
 * 因此子类只需重写 [executeWithContext]，本基类把 [execute] 实现为「抛 `UnsupportedOperationException`」，
 * 用以在编译期/调用期明确表达：本工具不存在脱离上下文的执行路径。相较「返回一个固定 Error」，
 * 抛异常能立即暴露误调用（如未来有人新增非 context 的执行分支却忘了走 context），不会把
 * 「缺 context」伪装成一个看似正常的工具失败结果。
 */
abstract class AbstractContextualTool : AgentTool() {

    /**
     * 本工具只支持 [executeWithContext]，此路径不会被提供，调用即视为编程错误。
     * 保留实现以满足 [AgentTool.execute] 的 abstract 签名。
     */
    final override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        throw UnsupportedOperationException(
            "${javaClass.simpleName} 只能通过 executeWithContext 执行，需 AgentContext。"
        )
    }
}
