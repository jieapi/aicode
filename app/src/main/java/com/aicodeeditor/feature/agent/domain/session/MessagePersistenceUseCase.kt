package com.aicodeeditor.feature.agent.domain.session

import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.CONTEXT_COMPACTION_MARKER
import com.aicodeeditor.feature.agent.domain.model.CONTEXT_SUMMARY_LEGACY_PREFIX
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.presentation.AgentAttachment
import com.aicodeeditor.feature.agent.presentation.MessageRole
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagePersistenceUseCase @Inject constructor(
    private val agentMessageDao: AgentMessageDao
) {
    private companion object {
        const val LEGACY_PENDING_TOOL_MARKER = "\u23F3"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 单调递增时间戳：保证同毫秒内多次落库的顺序稳定（assistant 永远在其 tool 结果之前）。
    @Volatile
    private var lastTimestamp = 0L

    @Synchronized
    fun nextTimestamp(): Long {
        val now = System.currentTimeMillis()
        val ts = if (now > lastTimestamp) now else lastTimestamp + 1
        lastTimestamp = ts
        return ts
    }

    suspend fun persist(
        sessionId: String,
        role: MessageRole,
        content: String,
        id: String = UUID.randomUUID().toString(),
        toolCalls: List<ToolCall> = emptyList(),
        toolCallId: String? = null,
        toolName: String? = null,
        toolArgs: String? = null,
        isError: Boolean = false,
        reasoning: String? = null,
        attachments: List<AgentAttachment> = emptyList()
    ) {
        agentMessageDao.insert(
            AgentMessageEntity(
                id = id,
                sessionId = sessionId,
                role = role.name,
                content = content,
                timestamp = nextTimestamp(),
                toolCallsJson = if (toolCalls.isNotEmpty()) json.encodeToString(toolCalls) else null,
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgs = toolArgs,
                isError = isError,
                reasoning = reasoning,
                attachmentsJson = if (attachments.isNotEmpty()) json.encodeToString(attachments) else null
            )
        )
    }

    /**
     * 从持久化的消息重建合法的上下文历史。
     * 关键：只保留「assistant 的 tool_call」与「tool 结果」能配对成功的部分，
     * 丢弃任何一方缺失的悬挂项，避免回放出现孤儿 tool_use / tool_result 违反 API 约束。
     * 已被上下文压缩标记的消息（isCompacted=true）不参与回放。
     */
    suspend fun buildHistory(sessionId: String, pendingToolMarker: String): List<AgentMessage> {
        val entities = agentMessageDao.getMessagesBySessionOnce(sessionId)
            .filter { !it.isCompacted }

        // 第一遍：求 assistant 声明的 toolCallId 与 tool 结果 toolCallId 的交集。
        val declaredIds = mutableSetOf<String>()
        val resultIds = mutableSetOf<String>()
        for (e in entities) {
            when (MessageRole.valueOf(e.role)) {
                MessageRole.ASSISTANT -> e.toolCallsJson?.let {
                    runCatching { json.decodeFromString<List<ToolCall>>(it) }
                        .getOrNull()?.forEach { tc -> declaredIds.add(tc.id) }
                }
                MessageRole.TOOL -> {
                    // 只有真正完成的结果才计入配对；执行中占位行（完成事件未回来的孤儿）不算。
                    if (!e.content.startsWith(pendingToolMarker) &&
                        !e.content.startsWith(LEGACY_PENDING_TOOL_MARKER)
                    ) {
                        e.toolCallId?.let { resultIds.add(it) }
                    }
                }
                else -> {}
            }
        }
        val validIds = declaredIds intersect resultIds

        // 第二遍：构建消息，过滤掉无法配对的工具调用 / 工具结果。
        val result = mutableListOf<AgentMessage>()
        for (e in entities) {
            when (MessageRole.valueOf(e.role)) {
                MessageRole.USER -> result.add(
                    AgentMessage.UserMessage(
                        id = e.id,
                        content = if (e.isCompactionMarker) CONTEXT_COMPACTION_MARKER else e.content
                    )
                )
                MessageRole.ASSISTANT -> {
                    val toolCalls = e.toolCallsJson?.let {
                        runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrNull()
                    }?.filter { it.id in validIds } ?: emptyList()
                    if (e.content.isNotBlank() || toolCalls.isNotEmpty()) {
                        val previous = result.lastOrNull()
                        if (
                            e.isContextSummary &&
                            !(previous is AgentMessage.UserMessage && previous.content == CONTEXT_COMPACTION_MARKER)
                        ) {
                            result.add(AgentMessage.UserMessage(content = CONTEXT_COMPACTION_MARKER))
                        }
                        result.add(
                            AgentMessage.AssistantMessage(
                                id = e.id,
                                content = e.content.removePrefix(CONTEXT_SUMMARY_LEGACY_PREFIX).trimStart(),
                                toolCalls = toolCalls
                            )
                        )
                    }
                }
                MessageRole.TOOL -> {
                    val tcId = e.toolCallId
                    if (tcId != null && tcId in validIds) {
                        result.add(
                            AgentMessage.ToolResultMessage(
                                id = tcId,
                                toolName = e.toolName ?: "unknown",
                                result = e.content
                            )
                        )
                    }
                }
            }
        }
        return result
    }
}
