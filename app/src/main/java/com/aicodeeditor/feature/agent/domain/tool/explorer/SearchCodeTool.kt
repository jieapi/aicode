package com.aicodeeditor.feature.agent.domain.tool.explorer

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

/**
 * 在代码中搜索内容——纯只读探索工具。
 *
 * 不进容器，通过 [WorkspacePathMapper] 映射容器路径到宿主文件系统直接遍历，
 * 与 `readFile` 一致的路径解析方式，无需容器就绪即可使用。两种模式下均可使用。
 */
class SearchCodeTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {

    private companion object {
        const val TAG = "SearchTool"
        const val DEFAULT_MAX_RESULTS = 50
        const val MAX_MAX_RESULTS = 200
        /** 单文件超过此字节数则跳过（约 500KB），避免大文件拖慢搜索。 */
        const val MAX_FILE_BYTES = 500L * 1024
        /** 单行匹配内容截断上限。 */
        const val MAX_LINE_PREVIEW = 200
        /** 搜索超时（毫秒），防止超大目录卡死。 */
        const val SEARCH_TIMEOUT_MS = 30_000L
        /** 二进制文件的常见扩展名——跳过不搜。 */
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
    override val description = "在项目代码中搜索匹配指定字符串或正则模式的行。返回文件路径、行号和匹配内容。纯只读工具，不会修改任何文件。适用于代码探索、查找引用、追踪调用关系等场景。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE

    override val parameters: Map<String, ToolParameter> = mapOf(
        "query" to ToolParameter(
            name = "query",
            type = ParameterType.ARRAY,
            description = "搜索查询数组，每个元素是一个搜索内容（纯文本按子串匹配，或正则表达式）。所有查询共享同一次文件遍历，各自独立匹配返回。单个查询也用数组，如 [\"fun main\"]；多个查询如 [\"class Foo\", \"class Bar\"]。",
            required = true,
            itemsSchema = mapOf("type" to "string", "description" to "搜索内容：纯文本或正则表达式")
        ),
        "path" to ToolParameter(
            name = "path",
            type = ParameterType.STRING,
            description = "搜索起始路径，默认为 /workspace（项目根目录）。可缩小到子目录加速搜索。",
            required = false
        ),
        "file_pattern" to ToolParameter(
            name = "file_pattern",
            type = ParameterType.STRING,
            description = "文件名 glob 过滤，如 '*.kt' 只搜索 Kotlin 文件，'*.{ts,tsx}' 搜索 TypeScript 文件。不填则搜索所有文本文件。",
            required = false
        ),
        "exclude" to ToolParameter(
            name = "exclude",
            type = ParameterType.STRING,
            description = "要排除的目录名，逗号分隔，如 '.git,node_modules,build'。不填则不排除任何目录。",
            required = false
        ),
        "case_sensitive" to ToolParameter(
            name = "case_sensitive",
            type = ParameterType.BOOLEAN,
            description = "是否区分大小写，默认 false。",
            required = false
        ),
        "max_results" to ToolParameter(
            name = "max_results",
            type = ParameterType.INTEGER,
            description = "每个查询的最大返回匹配数，默认 $DEFAULT_MAX_RESULTS，上限 $MAX_MAX_RESULTS。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            // query 为数组参数
            val queryArr = args["query"]?.jsonArray
                ?: return ToolResult.Error("缺少必需参数: query", "MISSING_QUERY")

            val queries = queryArr.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it.isNotEmpty() }.distinct()
            if (queries.isEmpty()) {
                return ToolResult.Error("查询内容为空", "EMPTY_QUERY")
            }
            val maxQueries = 10
            if (queries.size > maxQueries) {
                return ToolResult.Error("最多支持 $maxQueries 个查询，当前 ${queries.size} 个。请减少查询数量。", "TOO_MANY_QUERIES")
            }

            val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { WorkspacePathMapper.CONTAINER_ROOT }
                ?: WorkspacePathMapper.CONTAINER_ROOT
            val filePattern = args["file_pattern"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            val caseSensitive = args["case_sensitive"]?.jsonPrimitive?.booleanOrNull ?: false
            val maxResults = args["max_results"]?.jsonPrimitive?.intOrNull?.coerceIn(1, MAX_MAX_RESULTS) ?: DEFAULT_MAX_RESULTS
            val excludeStr = args["exclude"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            val excludeDirs = excludeStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

            val searchDir = pathMapper.toHostFile(path)

            FileLogger.d(TAG, "search queries=${queries.size} path=$path pattern=$filePattern case=$caseSensitive max=$maxResults")

            if (!searchDir.exists()) {
                return ToolResult.Error("搜索路径不存在: $path", "DIR_NOT_FOUND")
            }
            if (!searchDir.isDirectory) {
                return ToolResult.Error("搜索路径不是目录: $path", "NOT_DIRECTORY")
            }

            // 为每个查询编译正则
            val regexOptions = if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
            val regexes = queries.map { q ->
                try {
                    Regex(q, regexOptions)
                } catch (_: Exception) {
                    Regex(Regex.escape(q), regexOptions)
                }
            }

            val fileGlobRegex = filePattern?.let { globToRegex(it) }
            // 每个查询独立的成绩列表和计数
            val allResults = Array(queries.size) { mutableListOf<SearchMatch>() }
            val skippedDirs = mutableListOf<String>()
            var filesScanned = 0
            var filesSkipped = 0
            val startTime = System.currentTimeMillis()

            // 只有所有查询都达到上限才停止
            val activeIndices = queries.indices.toMutableSet()

            walkAndSearchMulti(searchDir, regexes, fileGlobRegex, maxResults, excludeDirs,
                allResults, activeIndices, skippedDirs, startTime,
                fileScannedCounter = { filesScanned++ },
                fileSkippedCounter = { filesSkipped++ }
            )

            val elapsed = System.currentTimeMillis() - startTime

            FileLogger.v(TAG, "search 完成: ${allResults.sumOf { it.size }} total matches, $filesScanned files scanned, ${elapsed}ms")

            // 格式化输出：每个查询一组，类似 grep 输出
            val output = buildString {
                for (i in queries.indices) {
                    val results = allResults[i]
                    if (queries.size > 1) {
                        append("--- 查询: ${queries[i]} (${results.size} 条匹配) ---\n")
                    }
                    for (match in results) {
                        val containerPath = pathMapper.toContainerPath(match.filePath)
                        val preview = if (match.lineContent.length > MAX_LINE_PREVIEW) {
                            match.lineContent.take(MAX_LINE_PREVIEW) + "…"
                        } else {
                            match.lineContent
                        }
                        append("$containerPath:${match.lineNumber}:$preview\n")
                    }
                    val wasTruncated = results.size >= maxResults
                    if (wasTruncated) {
                        append("\n已达 $maxResults 条匹配上限，可能有更多结果。可缩小搜索路径或添加 file_pattern 过滤。\n")
                    }
                }
                if (skippedDirs.isNotEmpty()) {
                    append("\n跳过的目录: ${skippedDirs.joinToString(", ")}")
                }
            }

            ToolResult.Success(JsonObject(mapOf(
                "content" to JsonPrimitive(output),
                "matches" to JsonPrimitive(allResults.sumOf { it.size }),
                "files_scanned" to JsonPrimitive(filesScanned),
                "files_skipped" to JsonPrimitive(filesSkipped),
                "truncated" to JsonPrimitive(allResults.any { it.size >= maxResults }),
                "elapsed_ms" to JsonPrimitive(elapsed)
            )))
        } catch (e: Exception) {
            FileLogger.e(TAG, "search 异常", e)
            ToolResult.Error(e.message ?: "搜索失败", "SEARCH_ERROR")
        }
    }

    /**
     * 递归遍历目录并多查询同时搜索。
     * 所有查询共享同一次文件遍历，每个查询独立计数，达上限后该查询停止但其他查询继续。
     */
    private fun walkAndSearchMulti(
        dir: File,
        regexes: List<Regex>,
        fileGlobRegex: Regex?,
        maxResults: Int,
        excludeDirs: Set<String>,
        allResults: Array<MutableList<SearchMatch>>,
        activeIndices: MutableSet<Int>,
        skippedDirs: MutableList<String>,
        startTime: Long,
        fileScannedCounter: () -> Unit,
        fileSkippedCounter: () -> Unit
    ) {
        if (activeIndices.isEmpty()) return
        val children = dir.listFiles() ?: return

        for (child in children) {
            if (activeIndices.isEmpty()) return
            if (System.currentTimeMillis() - startTime > SEARCH_TIMEOUT_MS) return

            if (child.isDirectory) {
                if (child.name in excludeDirs) {
                    if (child.name !in skippedDirs) skippedDirs.add(child.name)
                    continue
                }
                walkAndSearchMulti(child, regexes, fileGlobRegex, maxResults, excludeDirs,
                    allResults, activeIndices, skippedDirs, startTime,
                    fileScannedCounter, fileSkippedCounter)
            } else if (child.isFile) {
                // glob 过滤
                if (fileGlobRegex != null && !fileGlobRegex.matches(child.name)) {
                    fileSkippedCounter()
                    continue
                }
                // 二进制检测
                if (isBinaryFile(child)) {
                    fileSkippedCounter()
                    continue
                }
                // 大文件跳过
                if (child.length() > MAX_FILE_BYTES) {
                    fileSkippedCounter()
                    continue
                }

                fileScannedCounter()
                searchInFileMulti(child, regexes, maxResults, allResults, activeIndices)
            }
        }
    }

    /** 在单个文件中逐行搜索多个查询，匹配行添加到对应查询的结果列表。 */
    private fun searchInFileMulti(
        file: File,
        regexes: List<Regex>,
        maxResults: Int,
        allResults: Array<MutableList<SearchMatch>>,
        activeIndices: MutableSet<Int>
    ) {
        try {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                for (qi in activeIndices.toList()) {
                    if (regexes[qi].containsMatchIn(line)) {
                        allResults[qi].add(SearchMatch(
                            filePath = file.absolutePath,
                            lineNumber = index + 1,
                            lineContent = line.trimEnd()
                        ))
                        if (allResults[qi].size >= maxResults) {
                            activeIndices.remove(qi)
                        }
                    }
                }
                if (activeIndices.isEmpty()) return
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "搜索文件失败: ${file.absolutePath}", e)
        }
    }

    /** 根据扩展名判断是否为二进制文件。 */
    private fun isBinaryFile(file: File): Boolean {
        val name = file.name.lowercase()
        return BINARY_INDICATORS.any { name.endsWith(it) }
    }

    /** 将简单 glob 模式转为正则：支持 * 和 ? 通配符，以及 {a,b} 语法。 */
    private fun globToRegex(glob: String): Regex {
        // 处理 {a,b,c} 语法 → (a|b|c)
        val withBrace = glob.replace(Regex("\\{([^}]+)\\}")) { match ->
            val options = match.groupValues[1].split(",").joinToString("|") { Regex.escape(it) }
            "($options)"
        }
        val escaped = withBrace.replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex("^$escaped$")
    }

    /** 单条搜索结果。 */
    private data class SearchMatch(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String
    )
}
