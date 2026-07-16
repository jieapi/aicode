package com.aicode.feature.agent.domain.tool.memory

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.memory.MemoryRepository
import com.aicode.feature.agent.domain.memory.MemoryScope
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class MemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : AgentTool() {
    private companion object {
        const val TAG = "MemoryTool"
    }

    override val name = "memory"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_AGENT_CONFIG, ToolCapability.MODIFY_AGENT_CONFIG)
    override val description =
        "管理 AI 的长期记忆。当用户告知新的偏好、项目约定、架构设计，或者你发现了有价值的规律时，使用此工具将其永久记录。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：read=读取记忆正文；save=保存记忆；delete=删除记忆；list=列出所有记忆摘要",
            enum = listOf("read", "save", "delete", "list"),
            required = true
        ),
        "name" to ToolParameter(
            name = "name",
            type = ParameterType.STRING,
            description = "记忆的短名称（作为文件名，如 conventions）。list 操作可省略。",
            required = false
        ),
        "description" to ToolParameter(
            name = "description",
            type = ParameterType.STRING,
            description = "一句话摘要（save 必填，将出现在系统提示词的记忆清单中）。",
            required = false
        ),
        "content" to ToolParameter(
            name = "content",
            type = ParameterType.STRING,
            description = "记忆的详细正文（Markdown 格式，save 必填）。",
            required = false
        ),
        "scope" to ToolParameter(
            name = "scope",
            type = ParameterType.STRING,
            description = "作用域：project=当前项目专属；global=跨项目通用。默认为 project。",
            enum = listOf("project", "global"),
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return ToolResult.Error("MemoryTool 需要 context", "MISSING_CONTEXT")
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数: action", "MISSING_ACTION")
        
        val memoryName = args["name"]?.jsonPrimitive?.contentOrNull?.trim()
        val scopeStr = args["scope"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val scope = if (scopeStr == "global") MemoryScope.GLOBAL else MemoryScope.PROJECT

        return try {
            when (action) {
                "list" -> handleList(context.projectRoot)
                "read" -> handleRead(memoryName, context.projectRoot)
                "save" -> handleSave(args, memoryName, scope, context.projectRoot)
                "delete" -> handleDelete(memoryName, scope, context.projectRoot)
                else -> ToolResult.Error("不支持的操作: $action", "UNSUPPORTED_ACTION")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Memory 工具执行失败: ${e.message}", e)
            ToolResult.Error("记忆操作失败: ${e.message}")
        }
    }

    private fun handleList(projectRoot: String?): ToolResult {
        val memories = memoryRepository.listMemories(projectRoot)
        if (memories.isEmpty()) return ToolResult.Success(JsonPrimitive("当前没有任何记忆。"))
        
        val list = memories.joinToString("\n") { "- ${it.name} (${it.scope.name.lowercase()}): ${it.description}" }
        return ToolResult.Success(JsonPrimitive("当前记忆列表：\n$list"))
    }

    private fun handleRead(name: String?, projectRoot: String?): ToolResult {
        if (name.isNullOrEmpty()) return ToolResult.Error("read 操作需要 name 参数", "MISSING_NAME")
        val content = memoryRepository.loadContent(name, projectRoot)
            ?: return ToolResult.Error("未找到记忆「$name」", "MEMORY_NOT_FOUND")
        return ToolResult.Success(JsonPrimitive(content))
    }

    private fun handleSave(args: Map<String, JsonElement>, name: String?, scope: MemoryScope, projectRoot: String?): ToolResult {
        if (name.isNullOrEmpty()) return ToolResult.Error("save 操作需要 name 参数", "MISSING_NAME")
        val description = args["description"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("save 操作需要 description 参数", "MISSING_DESCRIPTION")
        val content = args["content"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("save 操作需要 content 参数", "MISSING_CONTENT")

        if (scope == MemoryScope.PROJECT && projectRoot.isNullOrBlank()) {
            return ToolResult.Error("当前未选择工作区，无法保存项目级记忆。请改用 scope=global", "NO_WORKSPACE")
        }

        val success = memoryRepository.saveMemory(name, description, content, scope, projectRoot)
        return if (success) {
            ToolResult.Success(JsonPrimitive("已成功保存记忆「$name」到 ${scope.name.lowercase()} 作用域。它将在下一次会话启动时自动注入摘要。当前会话若需立即使用，请通过 read 操作读取。"))
        } else {
            ToolResult.Error("保存记忆失败，请查看日志。", "SAVE_FAILED")
        }
    }

    private fun handleDelete(name: String?, scope: MemoryScope, projectRoot: String?): ToolResult {
        if (name.isNullOrEmpty()) return ToolResult.Error("delete 操作需要 name 参数", "MISSING_NAME")
        
        val success = memoryRepository.deleteMemory(name, scope, projectRoot)
        return if (success) {
            ToolResult.Success(JsonPrimitive("已成功删除 ${scope.name.lowercase()} 作用域的记忆「$name」。"))
        } else {
            ToolResult.Error("删除失败，记忆「$name」可能不存在于该作用域。", "DELETE_FAILED")
        }
    }
}
