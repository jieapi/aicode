package com.aicodeeditor.feature.agent.domain.tool.file

import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.*
import java.io.File
import javax.inject.Inject

class ReadFileTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {
    override val name = "read_file"
    override val description = "读取文件内容"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径（基于 /workspace）", required = true),
        "start_line" to ToolParameter("start_line", ParameterType.INTEGER, "开始行号", required = false),
        "end_line" to ToolParameter("end_line", ParameterType.INTEGER, "结束行号", required = false)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val file = pathMapper.toHostFile(path)

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

/**
 * 写入整个文件，文件不存在时会自动创建（含父目录）。
 *
 * 通过 overwrite 参数吸收了旧 create_file 的「不覆盖已有文件」语义：
 * overwrite=false 且目标已存在时报错，可用于安全地新建文件。
 */
class WriteFileTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {
    override val name = "write_file"
    override val description = "写入整个文件内容（不存在则创建，存在则覆盖）。可用 overwrite=false 实现「仅新建、不覆盖」。局部修改请用 edit_file。"
    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径（基于 /workspace）", required = true),
        "content" to ToolParameter("content", ParameterType.STRING, "文件内容", required = true),
        "overwrite" to ToolParameter("overwrite", ParameterType.BOOLEAN, "目标已存在时是否覆盖。默认 true；设为 false 时若文件已存在则报错。", required = false)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val overwrite = args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: true

            val file = pathMapper.toHostFile(path)
            val existed = file.exists()
            if (existed && !overwrite) {
                return ToolResult.Error("文件已存在: $path（overwrite=false）", "FILE_EXISTS")
            }

            file.parentFile?.mkdirs()
            file.writeText(content)

            ToolResult.Success(
                JsonObject(mapOf(
                    "path" to JsonPrimitive(pathMapper.toContainerPath(file.absolutePath)),
                    "created" to JsonPrimitive(!existed),
                    "bytes_written" to JsonPrimitive(content.length),
                    "lines_written" to JsonPrimitive(content.lines().size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "写入文件失败", "WRITE_ERROR")
        }
    }
}
