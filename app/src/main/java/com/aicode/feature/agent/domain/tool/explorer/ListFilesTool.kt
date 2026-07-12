package com.aicode.feature.agent.domain.tool.explorer

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import com.aicode.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 以 ls 风格列出目录内容——纯只读探索工具。
 *
 * 不进容器，通过 [WorkspacePathMapper] 映射容器路径到宿主文件系统直接遍历，
 * 与 `readFile` 一致的路径解析方式。两种模式下均可使用。
 */
class ListFilesTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {

    private companion object {
        const val TAG = "ListTool"
        const val MAX_ENTRIES = 500
    }

    override val name = "list"
    override val description = "按 ls 风格列出文件和目录。例：args=\"-la /workspace/app\"。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_WORKSPACE)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "args" to ToolParameter(
            name = "args",
            type = ParameterType.STRING,
            description = "ls 风格参数。不填等同 /workspace。支持 -a -A -l -R -d -1 -h -r -t -f --。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val rawArgs = args["args"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val tokens = parseShellWords(rawArgs)
                ?: return ToolResult.Error("args 中存在未闭合的引号", "INVALID_ARGS")
            val options = parseLsOptions(tokens)
                ?: return ToolResult.Error("不支持的 ls 参数。支持：-a, -A, -l, -R, -d, -1, -h, -r, -t, -f, --", "UNSUPPORTED_OPTION")

            FileLogger.d(TAG, "list args=$rawArgs paths=${options.paths}")

            val output = StringBuilder()
            var entryCount = 0
            var truncated = false

            fun appendLine(line: String = "") {
                if (entryCount >= MAX_ENTRIES) {
                    truncated = true
                    return
                }
                output.append(line).append('\n')
                entryCount++
            }

            fun listPath(path: String, showHeader: Boolean) {
                if (entryCount >= MAX_ENTRIES) {
                    truncated = true
                    return
                }

                val file = pathMapper.toHostFile(path)
                val containerPath = pathMapper.toContainerPath(file.absolutePath)

                if (!file.exists()) {
                    appendLine("ls: cannot access '$path': No such file or directory")
                    return
                }

                if (!file.isDirectory || options.directoryOnly) {
                    appendEntry(LsEntry(file.name.ifBlank { containerPath }, file), options, ::appendLine)
                    return
                }

                if (showHeader) appendLine("${containerPath.trimEnd('/')}:")
                val children = listEntries(file, options)
                for (entry in children) {
                    if (entryCount >= MAX_ENTRIES) {
                        truncated = true
                        return
                    }
                    appendEntry(entry, options, ::appendLine)
                }

                if (options.recursive) {
                    for (entry in children) {
                        if (entryCount >= MAX_ENTRIES) {
                            truncated = true
                            return
                        }
                        if (!entry.file.isDirectory || entry.name == "." || entry.name == "..") continue
                        appendLine()
                        listPath(pathMapper.toContainerPath(entry.file.absolutePath), showHeader = true)
                    }
                }
            }

            val showHeaders = options.paths.size > 1 || options.recursive
            for ((index, path) in options.paths.withIndex()) {
                if (index > 0) appendLine()
                listPath(path, showHeaders)
            }

            if (truncated) {
                output.append("... (已达 $MAX_ENTRIES 条上限，剩余条目未列出)\n")
            }

            FileLogger.v(TAG, "list 完成 entries=$entryCount truncated=$truncated")
            ToolResult.Success(JsonObject(mapOf(
                "content" to JsonPrimitive(output.toString()),
                "entries" to JsonPrimitive(entryCount),
                "truncated" to JsonPrimitive(truncated)
            )))
        } catch (e: Exception) {
            FileLogger.e(TAG, "list 异常", e)
            ToolResult.Error(e.message ?: "列出目录失败", "LIST_ERROR")
        }
    }

    private fun parseLsOptions(tokens: List<String>): LsOptions? {
        val options = LsOptions()
        var parseOptions = true
        for (token in tokens) {
            when {
                parseOptions && token == "--" -> parseOptions = false
                parseOptions && token.startsWith("--") -> {
                    when (token) {
                        "--all" -> options.showAll = true
                        "--almost-all" -> options.showAlmostAll = true
                        "--long" -> options.longFormat = true
                        "--recursive" -> options.recursive = true
                        "--directory" -> options.directoryOnly = true
                        "--human-readable" -> options.humanReadable = true
                        "--reverse" -> options.reverse = true
                        "--time" -> options.sortByTime = true
                        else -> return null
                    }
                }
                parseOptions && token.startsWith("-") && token.length > 1 -> {
                    for (flag in token.drop(1)) {
                        when (flag) {
                            'a' -> options.showAll = true
                            'A' -> options.showAlmostAll = true
                            'l' -> options.longFormat = true
                            'R' -> options.recursive = true
                            'd' -> options.directoryOnly = true
                            '1' -> options.onePerLine = true
                            'h' -> options.humanReadable = true
                            'r' -> options.reverse = true
                            't' -> options.sortByTime = true
                            'f' -> {
                                options.noSort = true
                                options.showAll = true
                            }
                            else -> return null
                        }
                    }
                }
                else -> options.paths.add(token)
            }
        }
        if (options.paths.isEmpty()) options.paths.add(WorkspacePathMapper.CONTAINER_ROOT)
        return options
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

    private fun listEntries(dir: File, options: LsOptions): List<LsEntry> {
        val entries = mutableListOf<LsEntry>()
        if (options.showAll && !options.showAlmostAll) {
            entries.add(LsEntry(".", dir))
            dir.parentFile?.let { entries.add(LsEntry("..", it)) }
        }
        val children = dir.listFiles().orEmpty()
            .filter { options.showAll || options.showAlmostAll || !it.name.startsWith(".") }
            .map { LsEntry(it.name, it) }
        entries.addAll(children)

        if (!options.noSort) {
            entries.sortWith(compareBy<LsEntry> {
                if (options.sortByTime) -it.file.lastModified() else 0L
            }.thenBy { it.name })
        }
        if (options.reverse) entries.reverse()
        return entries
    }

    private fun appendEntry(
        entry: LsEntry,
        options: LsOptions,
        appendLine: (String) -> Unit
    ) {
        if (options.longFormat) {
            appendLine(longFormat(entry, options))
        } else {
            appendLine(entry.name)
        }
    }

    private fun longFormat(entry: LsEntry, options: LsOptions): String {
        val file = entry.file
        val type = if (file.isDirectory) "d" else "-"
        val owner = permissions(file, owner = true)
        val group = permissions(file, owner = false)
        val other = permissions(file, owner = false)
        val size = if (options.humanReadable) humanSize(file.length()) else file.length().toString()
        val time = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(file.lastModified()))
        return "$type$owner$group$other 1 user group ${size.padStart(8)} $time ${entry.name}"
    }

    private fun permissions(file: File, owner: Boolean): String {
        val read = if (file.canRead()) "r" else "-"
        val write = if (owner && file.canWrite()) "w" else "-"
        val execute = if (file.canExecute() || file.isDirectory) "x" else "-"
        return read + write + execute
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val units = arrayOf("K", "M", "G", "T")
        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (value >= 10) "%.0f%s".format(Locale.US, value, units[unit])
        else "%.1f%s".format(Locale.US, value, units[unit])
    }

    private data class LsOptions(
        var showAll: Boolean = false,
        var showAlmostAll: Boolean = false,
        var longFormat: Boolean = false,
        var recursive: Boolean = false,
        var directoryOnly: Boolean = false,
        var onePerLine: Boolean = false,
        var humanReadable: Boolean = false,
        var reverse: Boolean = false,
        var sortByTime: Boolean = false,
        var noSort: Boolean = false,
        val paths: MutableList<String> = mutableListOf()
    )

    private data class LsEntry(
        val name: String,
        val file: File
    )
}
