package com.aicodeeditor.feature.agent.domain.tool.code

import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import javax.inject.Inject

class ParseCodeTool @Inject constructor() : AgentTool() {
    override val name: String = "parse_code"
    override val description: String = "解析指定代码文件并返回该文件中包含的类、函数/方法列表的粗略结构。这不提供完整的AST，只提供结构化大纲以便于定位方法行号。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "file_path" to ToolParameter(
            name = "file_path",
            type = ParameterType.STRING,
            description = "需要解析的代码文件绝对路径",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val filePath = args["file_path"]?.toString()?.removeSurrounding("\"")
            ?: return ToolResult.Error("Missing required parameter: file_path")

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return ToolResult.Error("File not found or is a directory: $filePath")
        }

        try {
            val lines = file.readLines()
            val parsedResult = parseSimpleStructure(lines)

            return ToolResult.Success(parsedResult)
        } catch (e: Exception) {
            return ToolResult.Error("Failed to parse file: ${e.message}")
        }
    }

    // A simple regex based parser as a placeholder before introducing complex AST libs like detekt / kotlin-compiler.
    // Works roughly for Kotlin and Java files.
    private fun parseSimpleStructure(lines: List<String>): JsonObject {
        val classes = mutableListOf<JsonObject>()
        val functions = mutableListOf<JsonObject>()

        val classRegex = Regex("""(?:class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
        val funRegex = Regex("""(?:fun|void|int|String|boolean|Boolean)\s+([A-Za-z0-9_]+)\s*\(""")

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmedLine = line.trim()

            // Skip single-line comments
            if (trimmedLine.startsWith("//")) return@forEachIndexed

            val classMatch = classRegex.find(trimmedLine)
            if (classMatch != null) {
                classes.add(buildJsonObject {
                    put("name", JsonPrimitive(classMatch.groupValues[1]))
                    put("line", JsonPrimitive(lineNumber))
                })
            }

            val funMatch = funRegex.find(trimmedLine)
            // Prevent matching constructor declarations or keywords like if/while/for that look like functions
            if (funMatch != null && !trimmedLine.startsWith("class") && !trimmedLine.startsWith("if") && !trimmedLine.startsWith("while") && !trimmedLine.startsWith("for") && !trimmedLine.startsWith("switch")) {
                functions.add(buildJsonObject {
                    put("name", JsonPrimitive(funMatch.groupValues[1]))
                    put("line", JsonPrimitive(lineNumber))
                })
            }
        }

        return buildJsonObject {
            put("classes", kotlinx.serialization.json.JsonArray(classes))
            put("functions", kotlinx.serialization.json.JsonArray(functions))
        }
    }
}
