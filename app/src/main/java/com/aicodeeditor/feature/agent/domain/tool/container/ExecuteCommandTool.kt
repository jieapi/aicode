package com.aicodeeditor.feature.agent.domain.tool.container

import com.aicodeeditor.feature.agent.domain.container.CommandEvent
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.StreamingAgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.agent.domain.tool.ToolStreamEvent
import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/**
 * Tool that allows the AI agent to execute commands inside the Linux container.
 *
 * 命令在当前选中工作区目录下执行，使 AI 的 shell 操作（npm install、git 等）
 * 与文件工具作用于同一目录。
 *
 * 同时实现 [StreamingAgentTool]：优先逐行流式输出，让聊天里能实时看到命令执行过程；
 * [execute] 作为非流式兜底保留，最终聚合结果两者一致（喂回模型不变）。
 */
class ExecuteCommandTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool(), StreamingAgentTool {
    override val name = "execute_command"
    override val description = "在 Linux 容器/终端环境中执行 shell 命令 (如 npm install, git clone 等)"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "The shell command to execute",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val command = args["command"]?.toString()?.removeSurrounding("\"")
            ?: return ToolResult.Error("缺少必需参数: command")

        return try {
            // 在当前工作区目录内执行，与文件工具保持同一根目录
            val workdir = workspaceRepository.currentPath()
            val output = containerEngine.runCommandSync(command, workdir)
            ToolResult.Success(JsonPrimitive(output))
        } catch (e: Exception) {
            ToolResult.Error("执行命令失败: ${e.message}")
        }
    }

    /**
     * 流式执行：逐行 emit [ToolStreamEvent.Progress]，命令结束 emit [ToolStreamEvent.Completed]，
     * 其最终结果与 [execute] 等价（累积的完整 stdout），保证喂回模型的内容不变。
     */
    override fun executeStream(args: Map<String, JsonElement>): Flow<ToolStreamEvent> = flow {
        val command = args["command"]?.toString()?.removeSurrounding("\"")
        if (command == null) {
            emit(ToolStreamEvent.Completed(ToolResult.Error("缺少必需参数: command")))
            return@flow
        }

        val accumulated = StringBuilder()
        try {
            val workdir = workspaceRepository.currentPath()
            containerEngine.runCommandStream(command, workdir).collect { event ->
                when (event) {
                    is CommandEvent.Line -> {
                        accumulated.append(event.text).append("\n")
                        emit(ToolStreamEvent.Progress(event.text))
                    }
                    is CommandEvent.Exit -> { /* 结束在流完成后统一聚合 */ }
                }
            }
            emit(ToolStreamEvent.Completed(ToolResult.Success(JsonPrimitive(accumulated.toString()))))
        } catch (e: Exception) {
            emit(ToolStreamEvent.Completed(ToolResult.Error("执行命令失败: ${e.message}")))
        }
    }
}
