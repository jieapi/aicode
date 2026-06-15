package com.aicodeeditor.feature.agent.domain.tool.container

import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import javax.inject.Inject

/**
 * Tool that allows the AI agent to execute commands inside the Linux container.
 */
class ExecuteCommandTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine
) : AgentTool() {
    override val name = "execute_command"
    override val description = "在 Linux 容器/终端环境中执行 shell 命令 (如 npm install, git clone 等)"

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.Error("缺少必需参数: command")

        // In a real app with multiple projects, this path should come from session/state
        // For now, we'll try to execute in the app's files directory or a default workspace
        // If your file tools use a specific base directory, you'd pass that here.

        return try {
            // By passing null for projectPath, the engine will use default directory
            val output = containerEngine.runCommandSync(command, null)
            ToolResult.Success(output)
        } catch (e: Exception) {
            ToolResult.Error("执行命令失败: ${e.message}")
        }
    }
}