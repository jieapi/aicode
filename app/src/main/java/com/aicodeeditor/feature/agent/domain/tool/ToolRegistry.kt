package com.aicodeeditor.feature.agent.domain.tool

import com.aicodeeditor.feature.agent.domain.model.AgentMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    /** PLAN 模式下应从工具列表中移除的写操作/危险工具 */
    private val planModeBlockedTools = setOf("writeFile", "editFile", "Bash")

    fun register(name: String, tool: AgentTool) {
        tools[name] = tool
    }

    fun getTool(name: String): AgentTool? {
        return tools[name]
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getToolNames(): Set<String> {
        return tools.keys.toSet()
    }

    fun getAvailableTools(): List<AgentTool> {
        return tools.values.toList()
    }

    /**
     * 根据当前模式返回可用工具列表。
     * PLAN 模式下会过滤掉写操作工具（writeFile、editFile、Bash），
     * 防止模型尝试调用不可用的工具。terminal 工具保留，
     * 由 [ToolPermissionPolicyEngine] 在运行时拦截其 start/send 操作。
     */
    fun getAvailableTools(mode: AgentMode): List<AgentTool> {
        if (mode != AgentMode.PLAN) return tools.values.toList()
        return tools.values.filter { it.name !in planModeBlockedTools }
    }

    fun hasTool(name: String): Boolean {
        return tools.containsKey(name)
    }

    fun getAllToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.toToolDefinition() }
    }
}
