package com.aicodeeditor.feature.agent.domain.tool.container

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.PendingToolPermission
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.terminal.domain.TerminalSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

private fun JsonElement.asPlainString(): String = toString().removeSurrounding("\"")

/**
 * 让 AI 把需要挂后台的命令（如 `npm run dev`）开在一个常驻终端标签里，立刻返回该标签唯一 id。
 *
 * 与 [ExecuteCommandTool]（同步等命令结束、适合一次性命令）互补：本工具用于长驻服务，
 * 不阻塞、命令进程在后台持续运行，AI 之后凭返回的 tab_id 用 [ReadTerminalOutputTool] 读日志、
 * 用 [SendTerminalInputTool] 继续发命令。
 */
class RunBackgroundCommandTool @Inject constructor(
    private val sessionManager: TerminalSessionManager
) : AgentTool() {
    private companion object { const val TAG = "RunBackgroundCommandTool" }

    override val name = "run_background_command"
    override val description =
        "在一个常驻后台终端标签里运行长驻命令（如 npm run dev、服务进程），立即返回该标签的唯一 id；" +
        "不会阻塞等待命令结束。之后可用 read_terminal_output(tab_id) 读输出、send_terminal_input(tab_id) 继续发命令。"
    override val permissionPolicy = ToolPermissionPolicy.ASK

    override val parameters: Map<String, ToolParameter> = mapOf(
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "要在后台运行的 shell 命令，如 'npm run dev'",
            required = true
        ),
        "title" to ToolParameter(
            name = "title",
            type = ParameterType.STRING,
            description = "可选的标签显示名；不填则用 tab id",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val command = args["command"]?.asPlainString() ?: "未知命令"
        val title = args["title"]?.asPlainString()
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认启动后台命令",
            summary = command,
            details = "后台标签：${title?.takeIf { it.isNotBlank() } ?: "自动命名"}\n命令会持续运行，直到进程退出或手动停止。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
        val command = args["command"]?.asPlainString()
            ?: return@withContext ToolResult.Error("缺少必需参数: command")
        val title = args["title"]?.asPlainString()
        try {
            val tabId = sessionManager.startBackgroundCommand(command, title)
            FileLogger.i(TAG, "后台命令已启动 tab=$tabId: $command")
            ToolResult.Success(
                JsonPrimitive("已在后台终端标签 $tabId 启动命令：$command。可用 read_terminal_output(\"$tabId\") 查看输出。")
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "启动后台命令失败: $command", e)
            ToolResult.Error("启动后台命令失败: ${e.message}")
        }
    }
}

/**
 * 按终端标签 id 持续发送命令/输入（自动回车执行）。配合 [RunBackgroundCommandTool] 返回的 id 使用，
 * 也可对任意现存交互标签发命令。
 */
class SendTerminalInputTool @Inject constructor(
    private val sessionManager: TerminalSessionManager
) : AgentTool() {
    private companion object { const val TAG = "SendTerminalInputTool" }

    override val name = "send_terminal_input"
    override val description = "按终端标签 id 发送一行命令/输入（默认自动回车执行）。用于对后台或交互终端持续发命令。"
    override val permissionPolicy = ToolPermissionPolicy.ASK

    override val parameters: Map<String, ToolParameter> = mapOf(
        "tab_id" to ToolParameter(
            name = "tab_id",
            type = ParameterType.STRING,
            description = "目标终端标签 id，如 'term-2'",
            required = true
        ),
        "input" to ToolParameter(
            name = "input",
            type = ParameterType.STRING,
            description = "要发送的命令或文本",
            required = true
        ),
        "submit" to ToolParameter(
            name = "submit",
            type = ParameterType.BOOLEAN,
            description = "是否在末尾追加回车以执行该命令，默认 true",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val tabId = args["tab_id"]?.asPlainString() ?: "未知标签"
        val input = args["input"]?.asPlainString() ?: "未知输入"
        val submit = args["submit"]?.asPlainString()?.toBooleanStrictOrNull() ?: true
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认发送终端输入",
            summary = input,
            details = "目标终端：$tabId\n自动回车执行：${if (submit) "是" else "否"}",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
        val tabId = args["tab_id"]?.asPlainString()
            ?: return@withContext ToolResult.Error("缺少必需参数: tab_id")
        val input = args["input"]?.asPlainString()
            ?: return@withContext ToolResult.Error("缺少必需参数: input")
        val submit = args["submit"]?.asPlainString()?.toBooleanStrictOrNull() ?: true
        val ok = sessionManager.sendInput(tabId, input, appendNewline = submit)
        if (ok) {
            FileLogger.i(TAG, "向 $tabId 发送输入: $input")
            ToolResult.Success(JsonPrimitive("已向终端标签 $tabId 发送输入。可用 read_terminal_output(\"$tabId\") 查看结果。"))
        } else {
            ToolResult.Error("未找到终端标签: $tabId")
        }
    }
}

/**
 * 按终端标签 id 读取当前终端内容（屏幕缓冲全文），供 AI 查看命令输出/服务日志。
 */
class ReadTerminalOutputTool @Inject constructor(
    private val sessionManager: TerminalSessionManager
) : AgentTool() {
    private companion object { const val TAG = "ReadTerminalOutputTool" }

    override val name = "read_terminal_output"
    override val description = "按终端标签 id 读取该终端当前的全部输出内容（包括后台命令的实时日志）。不传 tab_id 时返回所有标签列表。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "tab_id" to ToolParameter(
            name = "tab_id",
            type = ParameterType.STRING,
            description = "目标终端标签 id，如 'term-2'；省略则列出所有标签的 id 与状态",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
        val tabId = args["tab_id"]?.asPlainString()
        if (tabId.isNullOrBlank()) {
            val list = sessionManager.listTabs()
            if (list.isEmpty()) return@withContext ToolResult.Success(JsonPrimitive("当前没有任何终端标签。"))
            val text = list.joinToString("\n") {
                val state = if (it.running) "运行中" else "已结束"
                val kind = if (it.isBackground) "后台" else "交互"
                "${it.id}  [$kind/$state]  ${it.title}" + (it.command?.let { c -> "  ($c)" } ?: "")
            }
            return@withContext ToolResult.Success(JsonPrimitive(text))
        }
        val output = sessionManager.getTabOutput(tabId)
            ?: return@withContext ToolResult.Error("未找到终端标签: $tabId")
        FileLogger.v(TAG, "读取 $tabId 输出 ${output.length} 字符")
        ToolResult.Success(JsonPrimitive(output))
    }
}
