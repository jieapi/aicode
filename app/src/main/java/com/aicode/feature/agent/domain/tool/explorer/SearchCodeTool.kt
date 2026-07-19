package com.aicode.feature.agent.domain.tool.explorer

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * rg 风格的项目搜索工具。参数原样传给容器内的 ripgrep，容器未就绪则报错。
 */
class SearchCodeTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool() {

    private companion object {
        const val TAG = "SearchTool"
        const val SEARCH_TIMEOUT_MS = 30_000L
    }

    override val name = "search"
    override val description = "按 rg 风格搜索文本。例：args=\"-n \\\"fun main\\\" /workspace/app\"。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_WORKSPACE)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "args" to ToolParameter(
            name = "args",
            type = ParameterType.STRING,
            description = "rg 风格参数。不填无效。常用：-i -F -e -g --hidden --。不要混入 shell 管道（|）、grep/head 等外部命令或重定向——它们不是 rg 参数。",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val rawArgs = args["args"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (rawArgs.isEmpty()) return ToolResult.Error("缺少搜索参数 args", "MISSING_ARGS")

            val tokens = parseShellWords(rawArgs)
                ?: return ToolResult.Error("args 中存在未闭合的引号", "INVALID_ARGS")
            if (tokens.isEmpty()) return ToolResult.Error("缺少搜索参数 args", "MISSING_ARGS")

            val startedAt = System.currentTimeMillis()
            val result = containerEngine.runCommandSyncIfReady(
                command = buildRgCommand(tokens),
                projectPath = workspaceRepository.currentPath(),
                timeoutMs = SEARCH_TIMEOUT_MS
            ) ?: return ToolResult.Error("容器未就绪，无法执行 rg", "CONTAINER_NOT_READY")

            if (isRgMissing(result.output)) return ToolResult.Error("容器内未安装 rg", "RG_MISSING")
            if (result.exitCode != null && result.exitCode > 1) {
                return ToolResult.Error(result.output.ifBlank { "rg 执行失败" }, "RG_ERROR")
            }

            val lines = result.output.lineSequence().filter { it.isNotBlank() }.toList()
            ToolResult.Success(JsonObject(mapOf(
                "content" to JsonPrimitive(result.output),
                "matches" to JsonPrimitive(lines.size),
                "truncated" to JsonPrimitive(false),
                "elapsed_ms" to JsonPrimitive(System.currentTimeMillis() - startedAt),
                "backend" to JsonPrimitive("rg")
            )))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "search 异常", e)
            ToolResult.Error(e.message ?: "搜索失败", "SEARCH_ERROR")
        }
    }

    private fun buildRgCommand(tokens: List<String>): String {
        val args = mutableListOf(
            "rg",
            "--line-number",
            "--no-heading",
            "--with-filename",
            "--color",
            "never"
        )
        args.addAll(tokens.map { shellQuote(expandTilde(it)) })
        return args.joinToString(" ")
    }

    /** 把 `~/` 开头的路径参数展开为 `/root/`，避免被单引号包裹后 shell 不展开 `~`。 */
    private fun expandTilde(arg: String): String =
        if (arg.startsWith("~/")) "/root/" + arg.removePrefix("~/") else arg

    private fun isRgMissing(output: String): Boolean {
        return output.contains("command not found", ignoreCase = true) ||
            output.contains("rg: not found", ignoreCase = true)
    }

    private fun parseShellWords(input: String): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                quote == '\'' -> {
                    if (c == '\'') quote = null else current.append(c)
                    tokenStarted = true
                }
                quote == '"' -> {
                    when (c) {
                        '"' -> quote = null
                        '\\' -> {
                            if (i + 1 < input.length) {
                                i++
                                current.append(input[i])
                            } else {
                                current.append(c)
                            }
                        }
                        else -> current.append(c)
                    }
                    tokenStarted = true
                }
                c.isWhitespace() -> {
                    if (tokenStarted) {
                        result.add(current.toString())
                        current.clear()
                        tokenStarted = false
                    }
                }
                c == '\'' || c == '"' -> {
                    quote = c
                    tokenStarted = true
                }
                c == '\\' -> {
                    if (i + 1 < input.length) {
                        i++
                        current.append(input[i])
                    } else {
                        current.append(c)
                    }
                    tokenStarted = true
                }
                else -> {
                    current.append(c)
                    tokenStarted = true
                }
            }
            i++
        }
        if (quote != null) return null
        if (tokenStarted) result.add(current.toString())
        return result
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
