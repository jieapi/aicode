package com.aicodeeditor.feature.agent.data

import com.aicodeeditor.feature.agent.domain.model.ChangeType
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import javax.inject.Singleton

@Singleton
class CodeChangeTracker {
    private val changes = mutableListOf<CodeChange>()

    fun trackChange(toolCall: ToolCall, result: ToolResult): List<CodeChange> {
        val newChanges = mutableListOf<CodeChange>()

        when {
            toolCall.name == "write_file" && result is ToolResult.Success -> {
                val filePath = toolCall.arguments["path"]?.toString()?.removeSurrounding("\"") ?: return emptyList()
                val content = toolCall.arguments["content"]?.toString()?.removeSurrounding("\"") ?: ""
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
                val filePath = toolCall.arguments["path"]?.toString()?.removeSurrounding("\"") ?: return emptyList()
                val oldCode = toolCall.arguments["old_string"]?.toString()?.removeSurrounding("\"") ?: ""
                val newCode = toolCall.arguments["new_string"]?.toString()?.removeSurrounding("\"") ?: ""
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
