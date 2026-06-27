import re
import os

def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

chat_panel = read_file('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/AIChatPanel.kt')

lines = chat_panel.split('\n')

def get_lines(start, end):
    return "\n".join(lines[start-1:end])

# Diff lines: 136-139, 1410-1582
diff_content = get_lines(136, 139) + "\n" + get_lines(1410, 1582)
diff_content = diff_content.replace("private fun DiffStat", "fun DiffStat")
diff_content = diff_content.replace("private fun DiffView", "fun DiffView")
diff_content = diff_content.replace("private fun DiffExpandToggle", "fun DiffExpandToggle")
diff_content = diff_content.replace("private data class EditDiff", "data class EditDiff")
diff_content = diff_content.replace("private data class EditHunk", "data class EditHunk")
diff_content = diff_content.replace("private fun parseEditDiff", "fun parseEditDiff")

diff_header = """package com.aicodeeditor.feature.agent.presentation.component.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.theme.Radius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer

"""
write_file('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/diff/DiffComponents.kt', diff_header + diff_content)

tool_content = get_lines(1243, 1389) + "\n" + get_lines(1585, 1648)
tool_content = tool_content.replace("private fun ToolMessageBody", "fun ToolMessageBody")
tool_content = tool_content.replace("private fun ToolStatusDot", "fun ToolStatusDot")
tool_content = tool_content.replace("private fun ToolSection", "fun ToolSection")
tool_content = tool_content.replace("private fun formatToolResult", "fun formatToolResult")
tool_content = tool_content.replace("private fun formatJsonData", "fun formatJsonData")
tool_content = tool_content.replace("private fun formatToolArgs", "fun formatToolArgs")

tool_header = """package com.aicodeeditor.feature.agent.presentation.component.tool

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.component.diff.*
import kotlinx.serialization.json.*
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.theme.Radius

val DiffAddText = Color(0xFF22C55E)
val DiffRemoveText = Color(0xFFEF4444)
"""
write_file('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/tool/ToolMessageBody.kt', tool_header + tool_content)

md_content = get_lines(689, 1105)
md_content = md_content.replace("private fun MarkdownContent", "fun MarkdownContent")
md_content = md_content.replace("private fun parseMarkdownBlocks", "fun parseMarkdownBlocks")
md_content = md_content.replace("private fun renderInlineMarkdown", "fun renderInlineMarkdown")
md_content = md_content.replace("private sealed interface MdBlock", "sealed interface MdBlock")
md_content = md_content.replace("private data class MdListItem", "data class MdListItem")

md_header = """package com.aicodeeditor.feature.agent.presentation.component.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.theme.Radius

"""
write_file('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/markdown/MarkdownContent.kt', md_header + md_content)

to_delete = []
to_delete += list(range(1243-1, 1389))
to_delete += list(range(1585-1, 1648))
to_delete += list(range(1410-1, 1582))
to_delete += list(range(689-1, 1105))
to_delete += list(range(136-1, 139)) 

imports = """import com.aicodeeditor.feature.agent.presentation.component.diff.*
import com.aicodeeditor.feature.agent.presentation.component.markdown.*
import com.aicodeeditor.feature.agent.presentation.component.tool.*
"""

new_lines = []
for i, line in enumerate(lines):
    if i == 2:
        new_lines.append(imports)
    if i not in to_delete:
        new_lines.append(line)

new_content = "\n".join(new_lines)
write_file('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/AIChatPanel.kt', new_content)

