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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 取出 JSON 字符串参数的「真实内容」。
 *
 * ⚠️ 不能用 `toString().removeSurrounding("\"")`：[toString] 返回 JSON 序列化形式（外层带引号、
 * 内部引号被转义成 `\"`），剥掉外层引号后内部仍是字面 `\"`，会把 `echo "*.vue"` 变成
 * `echo \"*.vue\"` 导致 shell 把引号当字面输出。必须用 [jsonPrimitive]/[contentOrNull] 取解码后内容。
 */
private fun JsonElement.asPlainString(): String? = jsonPrimitive.contentOrNull

/**
 * 统一的后台终端会话工具：用一个 `action` 参数区分三种操作，把原先的
 * `run_background_command` / `send_terminal_input` / `read_terminal_output` 三个工具合一。
 *
 * - `start`：把长驻命令（如 `npm run dev`、服务进程）开在一个常驻后台终端标签里，立即返回 tab_id，
 *   不阻塞等待。命令进程持续运行，AI 之后凭返回的 id 用 send/read 继续。
 * - `send`：按 tab_id 向某个后台或交互终端发送一行命令/输入（默认自动回车执行）。
 * - `read`：按 tab_id 读取该终端当前的全部输出（含后台命令实时日志）；省略 tab_id 则列出所有标签。
 *
 * 授权：start 动作按 shell 命令前缀匹配（见 [com.aicodeeditor.feature.agent.domain.permission.ToolPermissionPolicyEngine]），
 * send/read 按整工具匹配。
 */
class TerminalSessionTool @Inject constructor(
    private val sessionManager: TerminalSessionManager
) : AgentTool() {
    private companion object {
        const val TAG = "TerminalSessionTool"

        /** start 启动后捕获初始输出的最长时间（毫秒）：命令若提前退出则立即返回，否则最多等满。 */
        const val START_CAPTURE_MS = 5_000L

        /** 轮询命令是否退出的间隔（毫秒）。 */
        const val START_POLL_INTERVAL_MS = 200L
    }

    override val name = "terminal"
    override val description =
        "管理常驻后台终端会话页面。支持启动长驻命令、按标签发送输入、读取终端输出或列出所有标签。对于极其耗时的任务（如构建、安装依赖），推荐用本工具的 'start' 动作挂后台执行，不阻塞后续其他工作，需要结果时再 'read' 即可。"
    override val permissionPolicy = ToolPermissionPolicy.ASK

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：start=启动后台命令；send=向终端发输入；read=读终端输出",
            required = true,
            enum = listOf("start", "send", "read")
        ),
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "start 必填：要在后台运行的 shell 命令，如 'npm run dev'",
            required = false
        ),
        "title" to ToolParameter(
            name = "title",
            type = ParameterType.STRING,
            description = "start 可选：标签显示名；不填则用 tab id",
            required = false
        ),
        "tab_id" to ToolParameter(
            name = "tab_id",
            type = ParameterType.STRING,
            description = "send/read 用：目标终端标签 id，如 'term-2'；read 省略则列出所有标签",
            required = false
        ),
        "input" to ToolParameter(
            name = "input",
            type = ParameterType.STRING,
            description = "send 必填：要发送的命令或文本",
            required = false
        ),
        "submit" to ToolParameter(
            name = "submit",
            type = ParameterType.BOOLEAN,
            description = "send 可选：是否在末尾追加回车以执行该命令，默认 true",
            required = false
        )
    )

    private fun actionOf(args: Map<String, JsonElement>): String =
        args["action"]?.asPlainString()?.trim()?.lowercase() ?: ""

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission = when (actionOf(args)) {
        "start" -> {
            val command = args["command"]?.asPlainString() ?: "未知命令"
            val title = args["title"]?.asPlainString()
            PendingToolPermission(
                id = callId,
                toolName = name,
                title = "确认启动后台命令",
                summary = command,
                details = "后台标签：${title?.takeIf { it.isNotBlank() } ?: "自动命名"}\n命令会持续运行，直到进程退出或手动停止。",
                argsPreview = argsPreview
            )
        }
        "send" -> {
            val tabId = args["tab_id"]?.asPlainString() ?: "未知标签"
            val input = args["input"]?.asPlainString() ?: "未知输入"
            val submit = args["submit"]?.asPlainString()?.toBooleanStrictOrNull() ?: true
            PendingToolPermission(
                id = callId,
                toolName = name,
                title = "确认发送终端输入",
                summary = input,
                details = "目标终端：$tabId\n自动回车执行：${if (submit) "是" else "否"}",
                argsPreview = argsPreview
            )
        }
        else -> {
            val tabId = args["tab_id"]?.asPlainString()
            PendingToolPermission(
                id = callId,
                toolName = name,
                title = "确认读取终端输出",
                summary = tabId?.takeIf { it.isNotBlank() } ?: "列出所有标签",
                details = if (tabId.isNullOrBlank()) "列出当前所有终端标签及其状态" else "读取终端 $tabId 的当前输出",
                argsPreview = argsPreview
            )
        }
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult = when (actionOf(args)) {
        "start" -> start(args)
        "send" -> send(args)
        "read" -> read(args)
        else -> ToolResult.Error("缺少或非法的 action 参数（应为 start/send/read）")
    }

    /**
     * 启动后台命令标签，并捕获最多 [START_CAPTURE_MS] 毫秒内的初始输出后返回。
     *
     * 不像普通 start 那样只返回 tab_id 就结束——那样 AI 还得再调一次 read 才知道命令有没有立刻报错
     * （如命令不存在、端口被占）。这里启动后轮询：命令提前退出则立即返回（早失败早看见），否则等满
     * [START_CAPTURE_MS] 抓一段初始输出（dev server 的启动横幅、首次报错等通常都在这几秒内）。
     * 返回对象含 tab_id / 是否仍在运行 / 捕获到的输出，AI 据此判断要不要继续 send/read。
     *
     * TerminalSessionManager 内部需主线程，故整段切到 Main。
     */
    private suspend fun start(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
        val command = args["command"]?.asPlainString()
            ?: return@withContext ToolResult.Error("start 操作缺少必需参数: command")
        val title = args["title"]?.asPlainString()
        try {
            val tabId = sessionManager.startBackgroundCommand(command, title)
            FileLogger.i(TAG, "后台命令已启动 tab=$tabId: $command")

            // 轮询捕获初始输出：命令退出则提前结束，否则最多等满 START_CAPTURE_MS。
            var elapsed = 0L
            while (elapsed < START_CAPTURE_MS) {
                delay(START_POLL_INTERVAL_MS)
                elapsed += START_POLL_INTERVAL_MS
                val running = sessionManager.listTabs().firstOrNull { it.id == tabId }?.running ?: false
                if (!running) break
            }

            val running = sessionManager.listTabs().firstOrNull { it.id == tabId }?.running ?: false
            val output = sessionManager.getTabOutput(tabId) ?: ""
            FileLogger.v(TAG, "start 捕获 tab=$tabId running=$running 输出 ${output.length} 字符")
            ToolResult.Success(
                JsonObject(
                    mapOf(
                        "tab_id" to JsonPrimitive(tabId),
                        "running" to JsonPrimitive(running),
                        "output" to JsonPrimitive(output)
                    )
                )
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "启动后台命令失败: $command", e)
            ToolResult.Error("启动后台命令失败: ${e.message}")
        }
    }

    /** 向指定终端发送一行输入，默认回车执行。TerminalSessionManager 需主线程。 */
    private suspend fun send(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
        val tabId = args["tab_id"]?.asPlainString()
            ?: return@withContext ToolResult.Error("send 操作缺少必需参数: tab_id")
        val input = args["input"]?.asPlainString()
            ?: return@withContext ToolResult.Error("send 操作缺少必需参数: input")
        val submit = args["submit"]?.asPlainString()?.toBooleanStrictOrNull() ?: true
        val ok = sessionManager.sendInput(tabId, input, appendNewline = submit)
        if (ok) {
            FileLogger.i(TAG, "向 $tabId 发送输入: $input")
            ToolResult.Success(JsonPrimitive("已向终端标签 $tabId 发送输入。可用 terminal(action=\"read\", tab_id=\"$tabId\") 查看结果。"))
        } else {
            ToolResult.Error("未找到终端标签: $tabId")
        }
    }

    /** 读取终端输出；省略 tab_id 则列出所有标签。TerminalSessionManager 需主线程。 */
    private suspend fun read(args: Map<String, JsonElement>): ToolResult = withContext(Dispatchers.Main) {
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
