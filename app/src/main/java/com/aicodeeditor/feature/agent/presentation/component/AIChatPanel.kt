package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.ChangeType
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.MessageRole

@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    currentFile: String? = null,
    selectedCode: String? = null,
    projectRoot: String = "",
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val changes by viewModel.changes.collectAsState()

    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                AgentMessageItem(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 代码变更预览
        AnimatedVisibility(visible = changes.isNotEmpty()) {
            ChangePreviewPanel(
                changes = changes,
                onApply = { viewModel.applyChanges(changes) },
                onReject = { viewModel.rejectChanges() }
            )
        }

        // 输入框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = { Text("输入需求...") },
                enabled = agentState !is AgentUIState.Loading && agentState !is AgentUIState.Streaming,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotEmpty()) {
                                viewModel.executeAgentRequestStream(
                                inputText,
                                currentFile,
                                selectedCode,
                                projectRoot
                            )
                            inputText = ""
                        }
                    }
                ),
                singleLine = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotEmpty()) {
                        viewModel.executeAgentRequest(
                            inputText,
                            currentFile,
                            selectedCode,
                            projectRoot
                        )
                        inputText = ""
                    }
                },
                enabled = agentState !is AgentUIState.Loading && agentState !is AgentUIState.Streaming && inputText.isNotEmpty()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }

        // 状态指示
        AnimatedContent(
            targetState = agentState,
            modifier = Modifier.fillMaxWidth()
        ) { state ->
            when (state) {
                is AgentUIState.Loading, is AgentUIState.Streaming -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is AgentUIState.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                is AgentUIState.Applied -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "代码变更已应用",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun AgentMessageItem(message: AgentUIMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (message.role == MessageRole.USER) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when (message.role) {
                MessageRole.USER -> MaterialTheme.colorScheme.primary
                MessageRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
                MessageRole.TOOL -> MaterialTheme.colorScheme.tertiaryContainer
            },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = message.content,
                color = when (message.role) {
                    MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ChangePreviewPanel(
    changes: List<CodeChange>,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            "预览代码变更 (${changes.size} 处)",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(changes) { change ->
                ChangeItem(change)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.width(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("应用")
            }
            Button(
                onClick = onReject,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.width(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("拒绝")
            }
        }
    }
}

@Composable
fun ChangeItem(change: CodeChange) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (change.type) {
                ChangeType.CREATE -> "+"
                ChangeType.DELETE -> "−"
                ChangeType.REPLACE -> "~"
                else -> "→"
            },
            modifier = Modifier.padding(end = 8.dp),
            color = when (change.type) {
                ChangeType.CREATE -> Color.Green
                ChangeType.DELETE -> Color.Red
                else -> Color.Yellow
            },
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "${change.filePath} (L${change.startLine}-${change.endLine})",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
    }
}
