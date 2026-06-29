package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.tool.question.PendingUserQuestion
import com.aicodeeditor.feature.agent.domain.tool.question.QuestionItem
import com.aicodeeditor.feature.agent.domain.tool.question.SingleAnswer
import com.aicodeeditor.feature.agent.domain.tool.question.UserQuestionAnswer

private val brandGradient = Brush.linearGradient(listOf(Brand.Blue, Brand.Sky))

/** 「其他」选项的固定 label，不与 AI 传入的选项重复。 */
private const val OTHER_LABEL = "其他"

/**
 * AI 向用户提问的面板：展示 1-4 个结构化问题，每个带 2-4 个预设选项 + 一个「其他」自由输入选项。
 *
 * 风格对齐 [ToolPermissionPanel]：内联 Surface，不用 AlertDialog。
 *
 * @param question 待回答的问题请求。
 * @param onConfirm 用户点击确认后回传答案。
 * @param onSkip 用户点击跳过，返回空答案。
 */
@Composable
fun AskUserQuestionPanel(
    question: PendingUserQuestion,
    onConfirm: (UserQuestionAnswer) -> Unit,
    onSkip: () -> Unit
) {
    // 每个问题的已选 label 集合
    val selectedMap = remember(question.id) {
        mutableStateMapOf<Int, MutableList<String>>().apply {
            question.questions.forEachIndexed { idx, _ -> this[idx] = mutableStateListOf() }
        }
    }
    // 每个问题的「其他」自由文本
    val customTexts = remember(question.id) {
        mutableStateMapOf<Int, String>().apply {
            question.questions.forEachIndexed { idx, _ -> this[idx] = "" }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.md)
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 面板标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Brand.Blue)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "AI 需要你的确认",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "askUserQuestion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(Spacing.md))

            // 逐个渲染问题
            question.questions.forEachIndexed { idx, q ->
                if (idx > 0) Spacer(Modifier.height(Spacing.md))
                QuestionCard(
                    
                    item = q,
                    selected = selectedMap[idx] ?: mutableListOf(),
                    customText = customTexts[idx] ?: "",
                    onSelectionChanged = { newSelection ->
                        selectedMap[idx] = newSelection.toMutableList() as MutableList<String>
                    },
                    onCustomTextChanged = { customTexts[idx] = it }
                )
            }

            Spacer(Modifier.height(Spacing.md))

            // 底部按钮行
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(brandGradient)
                        .clickable {
                            val answers = question.questions.mapIndexed { i, q ->
                                val sel = selectedMap[i] ?: emptyList<String>()
                                val custom = customTexts[i]?.takeIf { it.isNotBlank() && OTHER_LABEL in sel }
                                SingleAnswer(
                                    question = q.question,
                                    selected = sel.filter { it != OTHER_LABEL },
                                    customText = custom
                                )
                            }
                            onConfirm(UserQuestionAnswer(answers))
                        }
                        .padding(vertical = Spacing.sm + 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "确认",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 单个问题的卡片区域。
 */
@Composable
private fun QuestionCard(
    
    item: QuestionItem,
    selected: List<String>,
    customText: String,
    onSelectionChanged: (List<String>) -> Unit,
    onCustomTextChanged: (String) -> Unit
) {
    Column {
        // 标题行：header 标签 + 问题文本
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.header.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.xs)
                ) {
                    Text(
                        text = item.header,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = item.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // 选项列表
        val allOptions = item.options.map { it.label } + OTHER_LABEL

        allOptions.forEachIndexed { optIdx, label ->
            val isOther = label == OTHER_LABEL
            val isSelected = label in selected
            val description = if (!isOther) {
                item.options.getOrNull(optIdx)?.description ?: ""
            } else {
                "输入自定义答案"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xs))
                    .clickable {
                        val newSelection = if (item.multiSelect) {
                            if (isSelected) selected - label else selected + label
                        } else {
                            listOf(label)
                        }
                        onSelectionChanged(newSelection)
                    }
                    .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.multiSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val newSelection = if (checked) selected + label else selected - label
                            onSelectionChanged(newSelection)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectionChanged(listOf(label)) },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 「其他」被选中时展开文本输入框
            if (isOther && isSelected) {
                Spacer(Modifier.height(Spacing.xs))
                TextField(
                    value = customText,
                    onValueChange = onCustomTextChanged,
                    placeholder = { Text("请输入…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(Radius.sm)
                )
            }
        }
    }
}
