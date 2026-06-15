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

            toolCall.name == "replace_code" && result is ToolResult.Success -> {
                val filePath = toolCall.arguments["file_path"]?.toString()?.removeSurrounding("\"") ?: return emptyList()
                val startLine = toolCall.arguments["start_line"]?.toString()?.toIntOrNull() ?: 1
                val endLine = toolCall.arguments["end_line"]?.toString()?.toIntOrNull() ?: 1
                val newCode = toolCall.arguments["new_code"]?.toString()?.removeSurrounding("\"") ?: ""
                newChanges.add(
                    CodeChange(
                        filePath = filePath,
                        type = ChangeType.REPLACE,
                        startLine = startLine,
                        endLine = endLine,
                        oldCode = "",
                        newCode = newCode
                    )
                )
            }

            toolCall.name == "insert_code" && result is ToolResult.Success -> {
                val filePath = toolCall.arguments["file_path"]?.toString()?.removeSurrounding("\"") ?: return emptyList()
                val line = toolCall.arguments["line"]?.toString()?.toIntOrNull() ?: 1
                val code = toolCall.arguments["code"]?.toString()?.removeSurrounding("\"") ?: ""
                newChanges.add(
                    CodeChange(
                        filePath = filePath,
                        type = ChangeType.INSERT,
                        startLine = line,
                        endLine = line,
                        oldCode = "",
                        newCode = code
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
