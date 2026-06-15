package com.aicodeeditor.feature.agent.domain.tool.file

import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.*
import java.io.File
import javax.inject.Inject

class ReadFileTool @Inject constructor() : AgentTool() {
    override val name = "read_file"
    override val description = "读取文件内容"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径", required = true),
        "start_line" to ToolParameter("start_line", ParameterType.INTEGER, "开始行号", required = false),
        "end_line" to ToolParameter("end_line", ParameterType.INTEGER, "结束行号", required = false)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val file = File(path)

            if (!file.exists()) {
                return ToolResult.Error("文件不存在: $path", "FILE_NOT_FOUND")
            }

            val content = file.readText()
            val lines = content.lines()
            val startLine = args["start_line"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 1
            val endLine = args["end_line"]?.jsonPrimitive?.intOrNull?.coerceAtMost(lines.size) ?: lines.size

            val result = if (startLine <= endLine && startLine <= lines.size) {
                lines.subList(startLine - 1, minOf(endLine, lines.size)).joinToString("\n")
            } else {
                content
            }

            ToolResult.Success(
                JsonObject(mapOf(
                    "content" to JsonPrimitive(result),
                    "total_lines" to JsonPrimitive(lines.size),
                    "read_lines" to JsonPrimitive(endLine - startLine + 1)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "读取文件失败", "READ_ERROR")
        }
    }
}

class WriteFileTool @Inject constructor() : AgentTool() {
    override val name = "write_file"
    override val description = "写入文件内容（覆盖整个文件）"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径", required = true),
        "content" to ToolParameter("content", ParameterType.STRING, "文件内容", required = true)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""

            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)

            ToolResult.Success(
                JsonObject(mapOf(
                    "path" to JsonPrimitive(path),
                    "bytes_written" to JsonPrimitive(content.length),
                    "lines_written" to JsonPrimitive(content.lines().size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "写入文件失败", "WRITE_ERROR")
        }
    }
}

class ListFilesTool @Inject constructor() : AgentTool() {
    override val name = "list_files"
    override val description = "列出目录下的文件和文件夹"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "目录路径", required = true),
        "recursive" to ToolParameter("recursive", ParameterType.BOOLEAN, "递归列出", required = false),
        "max_depth" to ToolParameter("max_depth", ParameterType.INTEGER, "最大递归深度", required = false)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val recursive = args["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
            val maxDepth = args["max_depth"]?.jsonPrimitive?.intOrNull ?: 5

            val dir = File(path)
            if (!dir.exists()) {
                return ToolResult.Error("目录不存在: $path", "DIR_NOT_FOUND")
            }

            if (!dir.isDirectory) {
                return ToolResult.Error("路径不是目录: $path", "NOT_A_DIRECTORY")
            }

            val files = mutableListOf<JsonObject>()
            listFilesRecursive(dir, files, recursive, 0, maxDepth)

            ToolResult.Success(
                JsonObject(mapOf(
                    "path" to JsonPrimitive(path),
                    "files" to JsonArray(files),
                    "count" to JsonPrimitive(files.size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "列出文件失败", "LIST_ERROR")
        }
    }

    private fun listFilesRecursive(
        dir: File,
        result: MutableList<JsonObject>,
        recursive: Boolean,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (!recursive || currentDepth >= maxDepth) {
            dir.listFiles()?.forEach { file ->
                result.add(
                    JsonObject(mapOf(
                        "name" to JsonPrimitive(file.name),
                        "path" to JsonPrimitive(file.absolutePath),
                        "is_directory" to JsonPrimitive(file.isDirectory),
                        "size" to JsonPrimitive(file.length()),
                        "modified" to JsonPrimitive(file.lastModified())
                    ))
                )
            }
        } else {
            dir.listFiles()?.forEach { file ->
                result.add(
                    JsonObject(mapOf(
                        "name" to JsonPrimitive(file.name),
                        "path" to JsonPrimitive(file.absolutePath),
                        "is_directory" to JsonPrimitive(file.isDirectory),
                        "size" to JsonPrimitive(file.length()),
                        "modified" to JsonPrimitive(file.lastModified())
                    ))
                )
                if (file.isDirectory && recursive) {
                    listFilesRecursive(file, result, recursive, currentDepth + 1, maxDepth)
                }
            }
        }
    }
}

class CreateFileTool @Inject constructor() : AgentTool() {
    override val name = "create_file"
    override val description = "创建新文件"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "完整文件路径", required = true),
        "content" to ToolParameter("content", ParameterType.STRING, "初始内容", required = false)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""

            val file = File(path)
            if (file.exists()) {
                return ToolResult.Error("文件已存在: $path", "FILE_EXISTS")
            }

            file.parentFile?.mkdirs()
            file.createNewFile()
            if (content.isNotEmpty()) {
                file.writeText(content)
            }

            ToolResult.Success(
                JsonObject(mapOf(
                    "path" to JsonPrimitive(file.absolutePath),
                    "created" to JsonPrimitive(true),
                    "bytes_written" to JsonPrimitive(content.length)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "创建文件失败", "CREATE_ERROR")
        }
    }
}
