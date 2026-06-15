package com.aicodeeditor.feature.agent.domain.tool.editor

import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.*
import java.io.File
import javax.inject.Inject

class InsertCodeTool @Inject constructor() : AgentTool() {
    override val name = "insert_code"
    override val description = "在指定位置插入代码"
    override val parameters = mapOf(
        "file_path" to ToolParameter("file_path", ParameterType.STRING, "文件路径", required = true),
        "line" to ToolParameter("line", ParameterType.INTEGER, "行号", required = true),
        "code" to ToolParameter("code", ParameterType.STRING, "要插入的代码", required = true)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("文件路径缺失", "MISSING_PATH")
            val line = args["line"]?.jsonPrimitive?.intOrNull ?: return ToolResult.Error("行号缺失", "MISSING_LINE")
            val code = args["code"]?.jsonPrimitive?.contentOrNull ?: ""

            val file = File(filePath)
            if (!file.exists()) {
                return ToolResult.Error("文件不存在: $filePath", "FILE_NOT_FOUND")
            }

            val lines = file.readLines().toMutableList()
            val insertLine = (line - 1).coerceIn(0, lines.size)

            // 在指定行插入
            code.lines().reversed().forEach { codeLine ->
                lines.add(insertLine, codeLine)
            }

            file.writeText(lines.joinToString("\n"))

            ToolResult.Success(
                JsonObject(mapOf(
                    "status" to JsonPrimitive("inserted"),
                    "file_path" to JsonPrimitive(filePath),
                    "inserted_at_line" to JsonPrimitive(line),
                    "total_lines" to JsonPrimitive(lines.size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "插入代码失败", "INSERT_ERROR")
        }
    }
}

class ReplaceCodeTool @Inject constructor() : AgentTool() {
    override val name = "replace_code"
    override val description = "替换指定范围的代码"
    override val parameters = mapOf(
        "file_path" to ToolParameter("file_path", ParameterType.STRING, "文件路径", required = true),
        "start_line" to ToolParameter("start_line", ParameterType.INTEGER, "开始行号", required = true),
        "end_line" to ToolParameter("end_line", ParameterType.INTEGER, "结束行号", required = true),
        "new_code" to ToolParameter("new_code", ParameterType.STRING, "新代码", required = true)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("文件路径缺失", "MISSING_PATH")
            val startLine = args["start_line"]?.jsonPrimitive?.intOrNull ?: return ToolResult.Error("开始行号缺失", "MISSING_START_LINE")
            val endLine = args["end_line"]?.jsonPrimitive?.intOrNull ?: return ToolResult.Error("结束行号缺失", "MISSING_END_LINE")
            val newCode = args["new_code"]?.jsonPrimitive?.contentOrNull ?: ""

            val file = File(filePath)
            if (!file.exists()) {
                return ToolResult.Error("文件不存在: $filePath", "FILE_NOT_FOUND")
            }

            val lines = file.readLines().toMutableList()
            val start = (startLine - 1).coerceIn(0, lines.size)
            val end = (endLine - 1).coerceIn(0, lines.size - 1)

            if (start > end) {
                return ToolResult.Error("开始行号大于结束行号", "INVALID_RANGE")
            }

            // 删除旧行
            repeat(end - start + 1) {
                if (start < lines.size) lines.removeAt(start)
            }

            // 插入新行
            newCode.lines().reversed().forEach { codeLine ->
                lines.add(start, codeLine)
            }

            file.writeText(lines.joinToString("\n"))

            ToolResult.Success(
                JsonObject(mapOf(
                    "status" to JsonPrimitive("replaced"),
                    "file_path" to JsonPrimitive(filePath),
                    "replaced_lines" to JsonPrimitive(end - start + 1),
                    "new_lines" to JsonPrimitive(newCode.lines().size),
                    "total_lines" to JsonPrimitive(lines.size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "替换代码失败", "REPLACE_ERROR")
        }
    }
}

class DeleteCodeTool @Inject constructor() : AgentTool() {
    override val name = "delete_code"
    override val description = "删除指定范围的代码"
    override val parameters = mapOf(
        "file_path" to ToolParameter("file_path", ParameterType.STRING, "文件路径", required = true),
        "start_line" to ToolParameter("start_line", ParameterType.INTEGER, "开始行号", required = true),
        "end_line" to ToolParameter("end_line", ParameterType.INTEGER, "结束行号", required = true)
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("文件路径缺失", "MISSING_PATH")
            val startLine = args["start_line"]?.jsonPrimitive?.intOrNull ?: return ToolResult.Error("开始行号缺失", "MISSING_START_LINE")
            val endLine = args["end_line"]?.jsonPrimitive?.intOrNull ?: return ToolResult.Error("结束行号缺失", "MISSING_END_LINE")

            val file = File(filePath)
            if (!file.exists()) {
                return ToolResult.Error("文件不存在: $filePath", "FILE_NOT_FOUND")
            }

            val lines = file.readLines().toMutableList()
            val start = (startLine - 1).coerceIn(0, lines.size)
            val end = (endLine - 1).coerceIn(0, lines.size - 1)

            if (start > end) {
                return ToolResult.Error("开始行号大于结束行号", "INVALID_RANGE")
            }

            // 从后向前删除，避免索引混乱
            for (i in end downTo start) {
                if (i < lines.size) lines.removeAt(i)
            }

            file.writeText(lines.joinToString("\n"))

            ToolResult.Success(
                JsonObject(mapOf(
                    "status" to JsonPrimitive("deleted"),
                    "file_path" to JsonPrimitive(filePath),
                    "deleted_lines" to JsonPrimitive(end - start + 1),
                    "remaining_lines" to JsonPrimitive(lines.size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "删除代码失败", "DELETE_ERROR")
        }
    }
}
