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
import com.aicode.feature.workspace.domain.WorkspacePathMapper
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

/**
 * rg 风格的项目搜索工具。
 *
 * 容器和基础包已就绪时执行真实 rg；否则用宿主文件系统实现常用 rg 参数的只读兜底。
 */
class SearchCodeTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper,
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool() {

    private companion object {
        const val TAG = "SearchTool"
        const val MAX_RESULTS = 200
        const val MAX_FILE_BYTES = 500L * 1024
        const val MAX_LINE_PREVIEW = 200
        const val SEARCH_TIMEOUT_MS = 30_000L
        val BINARY_INDICATORS = setOf(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg", ".webp",
            ".mp3", ".mp4", ".wav", ".avi", ".mov", ".mkv", ".flac",
            ".zip", ".tar", ".gz", ".bz2", ".xz", ".7z", ".rar",
            ".jar", ".aar", ".apk", ".aab", ".dex", ".so", ".o", ".class",
            ".woff", ".woff2", ".ttf", ".otf", ".eot",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".db", ".sqlite", ".sqlite3"
        )
    }

    override val name = "search"
    override val description = "按 rg 风格搜索文本。例：args=\"-n \\\"fun main\\\" /workspace/app\"。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_WORKSPACE)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "args" to ToolParameter(
            name = "args",
            type = ParameterType.STRING,
            description = "rg 风格参数。不填无效。常用：-i -F -e -g --hidden --。",
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

            trySearchWithRg(tokens)?.let { return it }

            val options = parseRgOptions(tokens)
                ?: return ToolResult.Error("兜底搜索仅支持常用 rg 参数：-i -s -F -e -g --glob --hidden --", "UNSUPPORTED_OPTION")
            if (options.patterns.isEmpty()) return ToolResult.Error("缺少搜索模式", "MISSING_PATTERN")

            val startedAt = System.currentTimeMillis()
            val results = mutableListOf<SearchMatch>()
            var filesScanned = 0
            var filesSkipped = 0
            var truncated = false

            val regexes = options.patterns.map { pattern ->
                buildRegex(pattern, options)
                    ?: return ToolResult.Error("无效正则: $pattern", "INVALID_REGEX")
            }

            for (path in options.paths) {
                val root = pathMapper.toHostFile(path)
                if (!root.exists()) return ToolResult.Error("路径不存在: $path", "PATH_NOT_FOUND")
                searchPath(
                    file = root,
                    options = options,
                    regexes = regexes,
                    results = results,
                    startedAt = startedAt,
                    fileScannedCounter = { filesScanned++ },
                    fileSkippedCounter = { filesSkipped++ }
                )
                if (results.size >= MAX_RESULTS || System.currentTimeMillis() - startedAt > SEARCH_TIMEOUT_MS) {
                    truncated = true
                    break
                }
            }

            val output = formatResults(results, truncated)
            ToolResult.Success(JsonObject(mapOf(
                "content" to JsonPrimitive(output),
                "matches" to JsonPrimitive(results.size),
                "files_scanned" to JsonPrimitive(filesScanned),
                "files_skipped" to JsonPrimitive(filesSkipped),
                "truncated" to JsonPrimitive(truncated || results.size >= MAX_RESULTS),
                "elapsed_ms" to JsonPrimitive(System.currentTimeMillis() - startedAt),
                "backend" to JsonPrimitive("kotlin")
            )))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "search 异常", e)
            ToolResult.Error(e.message ?: "搜索失败", "SEARCH_ERROR")
        }
    }

    private suspend fun trySearchWithRg(tokens: List<String>): ToolResult? {
        val command = buildRgCommand(tokens)
        val startedAt = System.currentTimeMillis()
        val result = containerEngine.runCommandSyncIfReady(
            command = command,
            projectPath = workspaceRepository.currentPath(),
            timeoutMs = SEARCH_TIMEOUT_MS
        ) ?: return null

        if (isRgMissing(result.output)) return null
        if (result.exitCode != null && result.exitCode > 1) {
            return ToolResult.Error(result.output.ifBlank { "rg 执行失败" }, "RG_ERROR")
        }

        val lines = result.output.lineSequence().filter { it.isNotBlank() }.toList()
        return ToolResult.Success(JsonObject(mapOf(
            "content" to JsonPrimitive(result.output),
            "matches" to JsonPrimitive(lines.size),
            "files_scanned" to JsonPrimitive(0),
            "files_skipped" to JsonPrimitive(0),
            "truncated" to JsonPrimitive(false),
            "elapsed_ms" to JsonPrimitive(System.currentTimeMillis() - startedAt),
            "backend" to JsonPrimitive("rg")
        )))
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
        args.addAll(tokens.map(::shellQuote))
        return args.joinToString(" ")
    }

    private fun isRgMissing(output: String): Boolean {
        return output.contains("command not found", ignoreCase = true) ||
            output.contains("rg: not found", ignoreCase = true)
    }

    private fun parseRgOptions(tokens: List<String>): RgOptions? {
        val options = RgOptions()
        val positional = mutableListOf<String>()
        var parseOptions = true
        var i = 0

        fun nextValue(): String? {
            i++
            return tokens.getOrNull(i)
        }

        while (i < tokens.size) {
            val token = tokens[i]
            when {
                parseOptions && token == "--" -> parseOptions = false
                parseOptions && token == "-e" || parseOptions && token == "--regexp" -> {
                    options.patterns.add(nextValue() ?: return null)
                }
                parseOptions && token.startsWith("-e") && token.length > 2 -> {
                    options.patterns.add(token.drop(2))
                }
                parseOptions && (token == "-g" || token == "--glob") -> {
                    options.globs.add(nextValue() ?: return null)
                }
                parseOptions && token.startsWith("--glob=") -> {
                    options.globs.add(token.substringAfter("="))
                }
                parseOptions && token.startsWith("--") -> {
                    when (token) {
                        "--ignore-case" -> options.ignoreCase = true
                        "--case-sensitive" -> options.ignoreCase = false
                        "--fixed-strings" -> options.fixedStrings = true
                        "--hidden" -> options.hidden = true
                        "--line-number", "--no-heading", "--with-filename", "--no-ignore" -> Unit
                        "--color", "--max-count", "--max-filesize" -> nextValue() ?: return null
                        else -> return null
                    }
                }
                parseOptions && token.startsWith("-") && token.length > 1 -> {
                    for (flag in token.drop(1)) {
                        when (flag) {
                            'i' -> options.ignoreCase = true
                            's' -> options.ignoreCase = false
                            'F' -> options.fixedStrings = true
                            'n', 'H' -> Unit
                            else -> return null
                        }
                    }
                }
                else -> positional.add(token)
            }
            i++
        }

        if (options.patterns.isEmpty() && positional.isNotEmpty()) {
            options.patterns.add(positional.removeAt(0))
        }
        options.paths.addAll(positional.ifEmpty { listOf(WorkspacePathMapper.CONTAINER_ROOT) })
        return options
    }

    private fun buildRegex(pattern: String, options: RgOptions): Regex? {
        val regexOptions = if (options.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val source = if (options.fixedStrings) Regex.escape(pattern) else pattern
        return try {
            Regex(source, regexOptions)
        } catch (_: Exception) {
            null
        }
    }

    private fun searchPath(
        file: File,
        options: RgOptions,
        regexes: List<Regex>,
        results: MutableList<SearchMatch>,
        startedAt: Long,
        fileScannedCounter: () -> Unit,
        fileSkippedCounter: () -> Unit
    ) {
        if (results.size >= MAX_RESULTS) return
        if (System.currentTimeMillis() - startedAt > SEARCH_TIMEOUT_MS) return

        if (file.isDirectory) {
            if (!options.hidden && file.name.startsWith(".") && pathMapper.toContainerPath(file.absolutePath) != WorkspacePathMapper.CONTAINER_ROOT) return
            file.listFiles().orEmpty().forEach {
                searchPath(it, options, regexes, results, startedAt, fileScannedCounter, fileSkippedCounter)
                if (results.size >= MAX_RESULTS) return
            }
            return
        }

        if (!file.isFile || isBinaryFile(file) || file.length() > MAX_FILE_BYTES || !matchesGlobs(file, options)) {
            fileSkippedCounter()
            return
        }

        fileScannedCounter()
        runCatching {
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (regexes.any { it.containsMatchIn(line) }) {
                        results.add(SearchMatch(pathMapper.toContainerPath(file.absolutePath), index + 1, line.trimEnd()))
                        if (results.size >= MAX_RESULTS) return
                    }
                }
            }
        }.onFailure {
            FileLogger.w(TAG, "搜索文件失败: ${file.absolutePath}", it)
        }
    }

    private fun matchesGlobs(file: File, options: RgOptions): Boolean {
        if (options.globs.isEmpty()) return true
        val containerPath = pathMapper.toContainerPath(file.absolutePath).removePrefix("${WorkspacePathMapper.CONTAINER_ROOT}/")
        var included = options.globs.none { !it.startsWith("!") }
        for (glob in options.globs) {
            val negated = glob.startsWith("!")
            val pattern = glob.removePrefix("!")
            val matched = globToRegex(pattern).matches(containerPath) || globToRegex(pattern).matches(file.name)
            if (matched) included = !negated
        }
        return included
    }

    private fun formatResults(results: List<SearchMatch>, truncated: Boolean): String {
        return buildString {
            for (match in results) {
                val preview = if (match.lineContent.length > MAX_LINE_PREVIEW) {
                    match.lineContent.take(MAX_LINE_PREVIEW) + "…"
                } else {
                    match.lineContent
                }
                append("${match.filePath}:${match.lineNumber}:$preview\n")
            }
            if (truncated || results.size >= MAX_RESULTS) {
                append("\n已达 $MAX_RESULTS 条上限，可能有更多结果。\n")
            }
        }
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

    private fun isBinaryFile(file: File): Boolean {
        val name = file.name.lowercase()
        return BINARY_INDICATORS.any { name.endsWith(it) }
    }

    private fun globToRegex(glob: String): Regex {
        val escaped = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex("^$escaped$")
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private data class RgOptions(
        val patterns: MutableList<String> = mutableListOf(),
        val paths: MutableList<String> = mutableListOf(),
        val globs: MutableList<String> = mutableListOf(),
        var ignoreCase: Boolean = false,
        var fixedStrings: Boolean = false,
        var hidden: Boolean = false
    )

    private data class SearchMatch(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String
    )
}
