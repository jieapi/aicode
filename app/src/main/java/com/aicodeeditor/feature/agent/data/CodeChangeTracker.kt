package com.aicodeeditor.feature.agent.data

import com.aicodeeditor.feature.agent.domain.model.ChangeType
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Singleton

@Singleton
class CodeChangeTracker {
    private val changes = mutableListOf<CodeChange>()

    fun trackChange(toolCall: ToolCall, result: ToolResult): List<CodeChange> {
        val newChanges = mutableListOf<CodeChange>()

        when {
            toolCall.name == "write_file" && result is ToolResult.Success -> {
                val filePath = toolCall.arguments["path"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                val content = toolCall.arguments["content"]?.jsonPrimitive?.contentOrNull ?: ""
                newChanges.add(
                    CodeChange(
                        filePath = filePath,
                        type = ChangeType.CREATE,
                        startLine = 1,
                        endLine = content.lines().size,
                        oldCode = "",
                        newCode = content
                    )
                )
            }

            toolCall.name == "edit_file" && result is ToolResult.Success -> {
                val filePath = (toolCall.arguments["path"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
                // 一次 edit_file 可含多处编辑：edits 数组里每个 {old_string,new_string} 各记一条变更。
                val editsArr = toolCall.arguments["edits"] as? JsonArray
                val pairs: List<Pair<String, String>> = if (editsArr != null) {
                    editsArr.mapNotNull { el ->
                        val obj = el as? JsonObject ?: return@mapNotNull null
                        val old = obj["old_string"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val new = obj["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
                        old to new
                    }
                } else {
                    // 扁平兜底：顶层 old_string/new_string。
                    val old = (toolCall.arguments["old_string"] as? JsonPrimitive)?.contentOrNull ?: ""
                    val new = (toolCall.arguments["new_string"] as? JsonPrimitive)?.contentOrNull ?: ""
                    listOf(old to new)
                }
                pairs.forEach { (oldCode, newCode) ->
                    newChanges.add(
                        CodeChange(
                            filePath = filePath,
                            // 字符串匹配编辑不依赖行号；0 表示「按内容定位，非行号区间」
                            type = ChangeType.REPLACE,
                            startLine = 0,
                            endLine = 0,
                            oldCode = oldCode,
                            newCode = newCode
                        )
                    )
                }
            }
        }

        changes.addAll(newChanges)
        return newChanges
    }

    fun getAllChanges(): List<CodeChange> = changes.toList()

    fun getChangesByFile(filePath: String): List<CodeChange> {
        return changes.filter { it.filePath == filePath }
    }

    fun clearChanges() {
        changes.clear()
    }
}
