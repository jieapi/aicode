package com.aicodeeditor.feature.agent.domain.tool.mcp

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.agent.domain.mcp.McpConfigRepository
import com.aicodeeditor.feature.agent.domain.mcp.McpServerConfig
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class ManageMcpTool @Inject constructor(
    private val mcpConfigRepository: McpConfigRepository,
    private val containerEngine: LinuxContainerEngine
) : AgentTool() {
    private companion object {
        const val TAG = "ManageMcpTool"
    }

    override val name = "manage_mcp"
    override val description = "管理 Model Context Protocol (MCP) 服务器。支持添加、删除与列表查询，并自动准备本地运行环境。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "执行的操作类型，必填",
            enum = listOf("list", "add_stdio", "add_http", "remove")
        ),
        "server_name" to ToolParameter(
            name = "server_name",
            type = ParameterType.STRING,
            description = "MCP 服务器名称（操作非 list 时必填）",
            required = false
        ),
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "执行命令，如 npx 或 python (仅 add_stdio 必填)",
            required = false
        ),
        "args" to ToolParameter(
            name = "args",
            type = ParameterType.ARRAY,
            description = "命令参数数组 (仅 add_stdio 可选)",
            required = false,
            itemsSchema = mapOf("type" to "string")
        ),
        "url" to ToolParameter(
            name = "url",
            type = ParameterType.STRING,
            description = "HTTP 远程服务地址 (仅 add_http 必填)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少 action 参数")
        
        return try {
            when (action) {
                "list" -> {
                    val servers = mcpConfigRepository.getServers()
                    ToolResult.Success(JsonPrimitive(mcpConfigRepository.serialize(servers)))
                }
                "remove" -> {
                    val name = args["server_name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("remove 缺少 server_name")
                    val servers = mcpConfigRepository.getServers().toMutableList()
                    val removed = servers.removeIf { it.name == name }
                    if (removed) {
                        mcpConfigRepository.setServers(servers)
                        ToolResult.Success(JsonPrimitive("已成功移除 MCP server: $name"))
                    } else {
                        ToolResult.Error("未找到 MCP server: $name")
                    }
                }
                "add_stdio" -> {
                    val name = args["server_name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("add_stdio 缺少 server_name")
                    val command = args["command"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("add_stdio 缺少 command")
                    val commandArgs = args["args"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    
                    // 自动环境准备 (AI 自治增强)
                    ensureEnvironment(command)
                    
                    val newServer = McpServerConfig(
                        name = name,
                        command = command,
                        args = commandArgs,
                        env = emptyMap(),
                        enabled = true
                    )
                    
                    val servers = mcpConfigRepository.getServers().toMutableList()
                    servers.removeIf { it.name == name }
                    servers.add(newServer)
                    mcpConfigRepository.setServers(servers)
                    
                    ToolResult.Success(JsonPrimitive("成功添加本地 MCP server: $name. 配置将在下一次会话生效。"))
                }
                "add_http" -> {
                    val name = args["server_name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("add_http 缺少 server_name")
                    val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("add_http 缺少 url")
                    
                    val newServer = McpServerConfig(
                        name = name,
                        url = url,
                        headers = emptyMap(),
                        enabled = true
                    )
                    
                    val servers = mcpConfigRepository.getServers().toMutableList()
                    servers.removeIf { it.name == name }
                    servers.add(newServer)
                    mcpConfigRepository.setServers(servers)
                    
                    ToolResult.Success(JsonPrimitive("成功添加 HTTP MCP server: $name. 配置将在下一次会话生效。"))
                }
                else -> ToolResult.Error("未知的 action: $action")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "manage_mcp 执行失败: ${e.message}", e)
            ToolResult.Error("管理 MCP 失败: ${e.message}")
        }
    }
    
    private suspend fun ensureEnvironment(command: String) {
        val installCmd = when (command) {
            "npx", "node", "npm" -> "command -v node >/dev/null 2>&1 || (echo 'Installing Node.js...' && apk update && apk add --no-cache nodejs npm)"
            "python", "python3", "pip" -> "command -v python3 >/dev/null 2>&1 || (echo 'Installing Python3...' && apk update && apk add --no-cache python3 py3-pip)"
            else -> null
        }
        
        installCmd?.let {
            FileLogger.i(TAG, "正在自动准备环境: $it")
            withContext(Dispatchers.IO) {
                containerEngine.runCommandSync(it, "/", 60_000L) // 1 分钟超时
            }
        }
    }
}
