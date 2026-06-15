package com.aicodeeditor.feature.agent.domain.tool

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    fun register(name: String, tool: AgentTool) {
        tools[name] = tool
    }

    fun getTool(name: String): AgentTool {
        return tools[name] ?: throw IllegalArgumentException("工具不存在: $name")
    }

    fun getAvailableTools(): List<AgentTool> {
        return tools.values.toList()
    }

    fun hasTool(name: String): Boolean {
        return tools.containsKey(name)
    }

    fun getAllToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.toToolDefinition() }
    }
}
