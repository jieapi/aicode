package com.aicode.feature.agent.domain.tool.editor

import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.PendingToolPermission
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import com.aicode.core.util.FileLogger
import com.aicode.core.util.LineDiff
import com.aicode.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.*
import javax.inject.Inject

private const val TAG = "EditFileTool"

/**
 * 基于「精确字符串匹配」的文件编辑工具，取代旧的按行号 insert/replace/delete 三件套。
 *
 * 选择字符串匹配而非行号的原因：连续编辑时，第一次修改会让后续所有行号发生漂移，
 * 行号方案因此天然脆弱。字符串匹配只依赖内容本身，且一个工具即可覆盖三种语义：
 *   - 替换：old_string -> new_string
 *   - 删除：new_string 传空串
 *   - 插入：把某段替换为「它自身 + 新内容」
 *
 * 一次调用可对同一个文件进行多处编辑：传入 edits 数组，按数组顺序依次应用，
 * 每个编辑都在前一个编辑的结果之上匹配。整批编辑是「全有或全无」的——只要有任何
 * 一个编辑匹配失败（找不到或匹配多处而未开 replace_all），整次调用都不写盘并报错，
 * 文件不会处于改了一半的状态。
 *
 * 为保证安全，默认要求每个 old_string 在当前内容中唯一；若有多处匹配，需提供更长的
 * 上下文，或对该编辑显式设置 replace_all=true 才会全部替换。
 */
class EditFileTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {
    override val name = "editFile"
    override val description =
        "通过精确的字符串匹配替换修改已存在的文件内容。作为局部修改文件的首选工具。支持通过 edits 数组一次性应用多处修改，任一处匹配失败将整批回滚。整文件重写请用 writeFile。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.WRITE_WORKSPACE)

    /** edits 数组单个元素的结构，供 function-calling 的 items schema。 */
    private val editItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "old_string" to mapOf(
                "type" to "string",
                "description" to "要被替换的原文，需与文件当前内容精确匹配（含缩进和换行）。带足够上下文以保证唯一。"
            ),
            "new_string" to mapOf(
                "type" to "string",
                "description" to "替换后的新内容。传空字符串表示删除匹配到的内容。"
            ),
            "replace_all" to mapOf(
                "type" to "boolean",
                "description" to "是否替换该 old_string 的全部匹配项。默认 false（要求唯一匹配）。"
            )
        ),
        "required" to listOf("old_string", "new_string")
    )

    override val parameters = mapOf(
        "path" to ToolParameter("path", ParameterType.STRING, "文件路径：/workspace/... 为项目文件；其它绝对路径（如 /etc/...、/root/...）为容器系统文件。", required = true),
        "edits" to ToolParameter(
            "edits",
            ParameterType.ARRAY,
            "要应用的编辑列表，按顺序依次生效，每个编辑在前一个的结果上匹配。" +
                "单处修改也用只含一个元素的数组。每个元素：{old_string, new_string, replace_all?}。",
            required = true,
            itemsSchema = editItemSchema
        )
    )

    /** 解析后的单个编辑。 */
    private data class Edit(val oldString: String, val newString: String, val replaceAll: Boolean)

    /** 单个编辑应用后的差异，用于 UI 渲染与喂回模型。 */
    private data class Hunk(val startLine: Int, val added: Int, val removed: Int, val diff: String)

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: "未知文件"
        val editCount = (args["edits"] as? JsonArray)?.size ?: 1
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认修改文件",
            summary = "AI 请求修改 $path",
            details = "编辑数量：$editCount\n工具会先完整匹配，任一编辑失败则不会写入。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error("路径参数缺失", "MISSING_PATH")

            val edits = parseEdits(args)
                ?: return ToolResult.Error("edits 参数缺失或为空：请在 edits 数组里给出至少一个 {old_string,new_string} 编辑", "MISSING_EDITS")

            // 逐个校验编辑本身的合法性（空 old_string / 无变化），避免无意义改动。
            edits.forEachIndexed { i, e ->
                if (e.oldString.isEmpty()) {
                    return ToolResult.Error("第 ${i + 1} 个编辑的 old_string 不能为空；创建文件请用 writeFile。", "EMPTY_OLD_STRING")
                }
                if (e.oldString == e.newString) {
                    return ToolResult.Error("第 ${i + 1} 个编辑的 old_string 与 new_string 相同，无需修改", "NO_OP")
                }
            }

            val file = pathMapper.toHostFile(path)
            FileLogger.d(TAG, "edit_file path=$path -> ${file.absolutePath} (edits=${edits.size})")
            if (!file.exists()) {
                FileLogger.w(TAG, "edit_file 文件不存在: $path")
                return ToolResult.Error("文件不存在: $path", "FILE_NOT_FOUND")
            }

            // 先在内存里顺序应用所有编辑；任一失败立刻返回、绝不写盘（全有或全无）。
            var content = file.readText()
            val hunks = ArrayList<Hunk>(edits.size)
            var totalReplacements = 0

            edits.forEachIndexed { i, e ->
                val occurrences = content.split(e.oldString).size - 1
                if (occurrences == 0) {
                    FileLogger.w(TAG, "edit_file 第 ${i + 1} 个编辑未匹配: $path")
                    return ToolResult.Error(
                        "第 ${i + 1} 个编辑未在文件中找到 old_string，请确认内容（含缩进/换行）与当前文件完全一致" +
                            "（注意：前面的编辑可能已经改动了这段内容）",
                        "NO_MATCH"
                    )
                }
                if (occurrences > 1 && !e.replaceAll) {
                    FileLogger.w(TAG, "edit_file 第 ${i + 1} 个编辑匹配 $occurrences 处且非 replace_all: $path")
                    return ToolResult.Error(
                        "第 ${i + 1} 个编辑的 old_string 在文件中匹配到 $occurrences 处，请提供更长的唯一上下文，或对该编辑设置 replace_all=true",
                        "MULTIPLE_MATCHES"
                    )
                }

                // 变更在「应用本编辑前」内容中的起始行号（1 基）。因为内容已包含此前所有编辑的结果，
                // 这个行号已经反映了前序编辑造成的行漂移，对自上而下的常规编辑顺序是准确的。
                val matchIndex = content.indexOf(e.oldString)
                val startLine = if (matchIndex >= 0) content.substring(0, matchIndex).count { it == '\n' } + 1 else 1

                val diff = LineDiff.toUnified(e.oldString, e.newString)
                val added = diff.lines().count { it.startsWith("+") }
                val removed = diff.lines().count { it.startsWith("-") }
                hunks.add(Hunk(startLine = startLine, added = added, removed = removed, diff = diff))

                content = if (e.replaceAll) content.replace(e.oldString, e.newString)
                else content.replaceFirst(e.oldString, e.newString)
                totalReplacements += if (e.replaceAll) occurrences else 1
            }

            file.writeText(content)

            val addedTotal = hunks.sumOf { it.added }
            val removedTotal = hunks.sumOf { it.removed }
            val hunksJson = JsonArray(hunks.map { h ->
                JsonObject(mapOf(
                    "start_line" to JsonPrimitive(h.startLine),
                    "added" to JsonPrimitive(h.added),
                    "removed" to JsonPrimitive(h.removed),
                    "diff" to JsonPrimitive(h.diff)
                ))
            })

            FileLogger.v(TAG, "edit_file 成功 path=$path edits=${edits.size} replacements=$totalReplacements")
            ToolResult.Success(
                JsonObject(mapOf(
                    "status" to JsonPrimitive("edited"),
                    "path" to JsonPrimitive(pathMapper.toContainerPath(file.absolutePath)),
                    "edits_count" to JsonPrimitive(edits.size),
                    "replacements" to JsonPrimitive(totalReplacements),
                    "total_lines" to JsonPrimitive(content.lines().size),
                    "added_lines" to JsonPrimitive(addedTotal),
                    "removed_lines" to JsonPrimitive(removedTotal),
                    "hunks" to hunksJson
                ))
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "edit_file 异常", e)
            ToolResult.Error(e.message ?: "编辑文件失败", "EDIT_ERROR")
        }
    }

    /**
     * 解析编辑列表：读取 edits 数组。
     */
    private fun parseEdits(args: Map<String, JsonElement>): List<Edit>? {
        val arr = args["edits"] as? JsonArray ?: return null
        if (arr.isEmpty()) return null
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val old = obj["old_string"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val new = obj["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
            val all = obj["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
            Edit(old, new, all)
        }.takeIf { it.isNotEmpty() }
    }
}
