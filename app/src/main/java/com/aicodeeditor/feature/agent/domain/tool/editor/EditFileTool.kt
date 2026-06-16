package com.aicodeeditor.feature.agent.domain.tool.editor

import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.*
import javax.inject.Inject

/**
 * 基于「精确字符串匹配」的文件编辑工具，取代旧的按行号 insert/replace/delete 三件套。
 *
 * 选择字符串匹配而非行号的原因：连续编辑时，第一次修改会让后续所有行号发生漂移，
 * 行号方案因此天然脆弱。字符串匹配只依赖内容本身，且一个工具即可覆盖三种语义：
 *   - 替换：old_string -> new_string
 *   - 删除：new_string 传空串
 *   - 插入：把某段替换为「它自身 + 新内容」
 *
 * 为保证安全，默认要求 old_string 在文件中唯一；若有多处匹配，需提供更长的上下文，
 * 或显式设置 replace_all=true 才会全部替换。
 */
class EditFileTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {
    override val name = "edit_file"
    override val description =
        "通过精确字符串匹配修改已有文件：把文件中的 old_string 替换为 new_string。这是修改文件的首选方式。" +
            "old_string 必须与文件内容逐字匹配（含缩进与换行）且默认唯一；new_string 传空串即为删除该段。" +
            "整文件重写请用 write_file，而非本工具。"

    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径（基于 /workspace）", required = true),
        "old_string" to ToolParameter(
            "old_string",
            ParameterType.STRING,
            "要被替换的原文，需与文件内容精确匹配（包含缩进和换行）。带上足够的上下文以保证唯一。",
            required = true
        ),
        "new_string" to ToolParameter(
            "new_string",
            ParameterType.STRING,
            "替换后的新内容。传空字符串表示删除匹配到的内容。",
            required = true
        ),
        "replace_all" to ToolParameter(
            "replace_all",
            ParameterType.BOOLEAN,
            "是否替换全部匹配项。默认 false（要求唯一匹配）。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            val oldString = args["old_string"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error("old_string 参数缺失", "MISSING_OLD_STRING")
            val newString = args["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
            val replaceAll = args["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false

            if (oldString.isEmpty()) {
                return ToolResult.Error("old_string 不能为空；创建文件请用 write_file。", "EMPTY_OLD_STRING")
            }
            if (oldString == newString) {
                return ToolResult.Error("old_string 与 new_string 相同，无需修改", "NO_OP")
            }

            val file = pathMapper.toHostFile(path)
            if (!file.exists()) {
                return ToolResult.Error("文件不存在: $path", "FILE_NOT_FOUND")
            }

            val content = file.readText()
            val occurrences = content.split(oldString).size - 1

            if (occurrences == 0) {
                return ToolResult.Error(
                    "未在文件中找到 old_string，请确认内容（含缩进/换行）与文件完全一致",
                    "NO_MATCH"
                )
            }
            if (occurrences > 1 && !replaceAll) {
                return ToolResult.Error(
                    "old_string 在文件中匹配到 $occurrences 处，请提供更长的唯一上下文，或设置 replace_all=true",
                    "MULTIPLE_MATCHES"
                )
            }

            val newContent = if (replaceAll) {
                content.replace(oldString, newString)
            } else {
                content.replaceFirst(oldString, newString)
            }
            file.writeText(newContent)

            ToolResult.Success(
                JsonObject(mapOf(
                    "status" to JsonPrimitive("edited"),
                    "path" to JsonPrimitive(pathMapper.toContainerPath(file.absolutePath)),
                    "replacements" to JsonPrimitive(if (replaceAll) occurrences else 1),
                    "total_lines" to JsonPrimitive(newContent.lines().size)
                ))
            )
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "编辑文件失败", "EDIT_ERROR")
        }
    }
}
