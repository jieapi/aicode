package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.id
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.presentation.MessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextCompactor @Inject constructor(
    private val agentMessageDao: AgentMessageDao
) {

    private companion object {
        const val TAG = "ContextCompactor"

        // 估算：通常一个 Token 约等于 3-4 个英文字符或 1-2 个中文字符。
        // 这里为了简单，我们用字符数作为硬截断指标。
        // 假设模型支持至少 100k Token，大概是 30 万字符。
        // 我们设定软阈值为 150_000 字符。
        const val CHAR_THRESHOLD = 150_000

        // 压缩后保留的尾部最新消息的最小数量，防止把刚发生的对话也截断了
        const val MIN_TAIL_MESSAGES = 10
    }

    /**
     * 如果消息体总长度超过阈值，则将早期的消息（Head）提取出来，
     * 通过后台 LLM 调用进行结构化摘要，然后替换回原来的位置。
     *
     * 压缩结果持久化到数据库：
     * - 被压缩的 head 部分消息标记 isCompacted=true（不删除，保留数据完整性）
     * - 摘要消息插入数据库，作为压缩后的上下文起点
     * - 重启后 [MessagePersistenceUseCase.buildHistory] 会跳过 isCompacted 的消息，
     *   只回放摘要 + tail 部分
     *
     * @return 压缩后的新列表（如果没有触发压缩则返回原列表的副本）
     */
    suspend fun compactIfNeeded(
        messages: List<AgentMessage>,
        aiProvider: AIProvider,
        sessionId: String? = null,
        onEvent: suspend (AgentEvent) -> Unit = {}
    ): List<AgentMessage> {
        val totalChars = messages.sumOf {
            when (it) {
                is AgentMessage.UserMessage -> it.content.length
                is AgentMessage.AssistantMessage -> it.content.length
                is AgentMessage.ToolResultMessage -> it.result.length
            }
        }
        if (totalChars < CHAR_THRESHOLD || messages.size <= MIN_TAIL_MESSAGES) {
            return messages.toList()
        }

        FileLogger.i(TAG, "上下文达到 $totalChars 字符，触发压缩机制。")
        val callId = UUID.randomUUID().toString().take(8)
        onEvent(AgentEvent.ToolCallStarted(
            id = callId,
            toolName = "context_compactor",
            argsPreview = "{\n  \"action\": \"compress_history\",\n  \"reason\": \"上下文超过阈值 ($totalChars 字符)，触发自动清理以防止内存与 Token 溢出\"\n}"
        ))

        // 拆分 Head（需要压缩的老数据）和 Tail（保留的新数据）
        var splitIndex = messages.size - MIN_TAIL_MESSAGES
        if (splitIndex <= 0) {
            onEvent(AgentEvent.ToolCallFinished(callId, "context_compactor", "不满足截断条件", false))
            return messages.toList()
        }

        // 确保 tail 的第一条消息不是孤立的 ToolResultMessage：
        // 如果 tail 以 ToolResultMessage 开头，需要向前回溯到其配对的 AssistantMessage(with toolCalls)，
        // 否则压缩后摘要 assistant 消息不含 toolCalls，导致 tool 消息变成孤立的，API 报 400。
        splitIndex = adjustSplitIndex(messages, splitIndex)

        val head = messages.subList(0, splitIndex)
        val tail = messages.subList(splitIndex, messages.size)

        val headContent = head.joinToString("\n\n") { msg ->
            when (msg) {
                is AgentMessage.UserMessage -> "[USER]:\n${msg.content}"
                is AgentMessage.AssistantMessage -> {
                    val base = "[ASSISTANT]:\n${msg.content}"
                    if (msg.toolCalls.isNotEmpty()) {
                        val toolCallsDesc = msg.toolCalls.joinToString(", ") { "${it.name}(${it.arguments})" }
                        "$base\n[TOOL_CALLS]: $toolCallsDesc"
                    } else base
                }
                is AgentMessage.ToolResultMessage -> "[TOOL_RESULT (${msg.toolName})]:\n${msg.result}"
            }
        }

        val summaryPrompt = """
            你是一个高度专业的上下文压缩引擎。以下是一段长对话的早期历史记录。
            为了防止上下文 Token 溢出，请将这段历史压缩为一个极其精简的摘要。

            你必须严格遵守以下 Markdown 结构返回：
            ## Goal (用户最初的目标)
            ## Progress (已经完成的核心步骤)
            ## Key Decisions (重要的架构决定、已修改的文件路径)
            ## Blockers/Errors (目前遗留的问题或错误)
            ## Next Steps (接下来要做的步骤)

            请直接输出摘要，不要说多余的客套话。

            以下是历史记录：
            $headContent
        """.trimIndent()

        val summaryResponse = try {
            val response = aiProvider.complete(
                systemPrompt = "你是一个上下文压缩引擎。",
                messages = listOf(AgentMessage.UserMessage(content = summaryPrompt)),
                tools = emptyList()
            )
            response.content
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "压缩上下文失败", e)
            onEvent(AgentEvent.ToolCallFinished(callId, "context_compactor", "压缩失败: ${e.message}", true))
            return messages.toList() // 失败则原样返回，交由上层自行承担溢出风险
        }

        FileLogger.i(TAG, "上下文压缩完成，摘要长度：${summaryResponse.length}")
        onEvent(AgentEvent.ToolCallFinished(callId, "context_compactor", summaryResponse, false))

        val compactedId = UUID.randomUUID().toString()
        val compactedMessage = AgentMessage.AssistantMessage(
            id = compactedId,
            content = "【系统提示：早期的对话已被压缩，以下是之前的核心状态摘要】\n$summaryResponse",
            toolCalls = emptyList()
        )

        // 持久化压缩结果到数据库
        if (sessionId != null) {
            try {
                val dbEntities = agentMessageDao.getMessagesBySessionOnce(sessionId
                )
                val firstTailId = tail.firstOrNull { msg -> msg.id.isNotEmpty() }?.id
                val tailEntity = if (firstTailId != null) dbEntities.find { it.id == firstTailId } else null
                val cutoffTimestamp = tailEntity?.timestamp ?: System.currentTimeMillis()

                // 将 head 部分的消息标记为已压缩（不删除，保留数据完整性）
                agentMessageDao.markMessagesCompactedBeforeTimestamp(sessionId, cutoffTimestamp)

                // 插入摘要消息（时间戳放在 head 最后一条和 tail 第一条之间）
                agentMessageDao.insert(
                    AgentMessageEntity(
                        id = compactedId,
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT.name,
                        content = compactedMessage.content,
                        timestamp = cutoffTimestamp - 1
                    )
                )
                FileLogger.i(TAG, "已持久化压缩结果到数据库，会话 $sessionId")
            } catch (e: Exception) {
                FileLogger.e(TAG, "持久化压缩结果失败", e)
            }
        }

        val newMessages = mutableListOf<AgentMessage>()
        newMessages.add(compactedMessage)
        newMessages.addAll(tail)

        return newMessages
    }

    /**
     * 调整拆分索引，确保 tail 不是以 ToolResultMessage 开头。
     *
     * OpenAI API 要求 role: "tool" 消息必须紧接在包含对应 tool_calls 的 assistant 消息之后。
     * 如果 tail 以 ToolResultMessage 开头，压缩后其前面的 assistant 消息（摘要）不含 toolCalls，
     * 该 tool 消息就变成了"孤立"的，API 会报 400 错误。
     *
     * 解决方案：向前回溯，把配对的 AssistantMessage(with toolCalls) 纳入 tail，
     * 确保所有 tool 消息都有配对的 toolCalls。
     */
    private fun adjustSplitIndex(messages: List<AgentMessage>, initialSplitIndex: Int): Int {
        var splitIndex = initialSplitIndex

        // 如果 tail 的第一条消息是 ToolResultMessage，
        // 需要向前找到对应的 AssistantMessage(with toolCalls)
        while (splitIndex > 0 && messages[splitIndex] is AgentMessage.ToolResultMessage) {
            splitIndex--
        }

        // 现在 splitIndex 可能指向一个 AssistantMessage(with toolCalls) 或其他类型消息
        // 如果是含 toolCalls 的 AssistantMessage，它必须和其后的 ToolResultMessage 一起在 tail 中
        if (splitIndex >= 0 && messages[splitIndex] is AgentMessage.AssistantMessage) {
            val assistantMsg = messages[splitIndex] as AgentMessage.AssistantMessage
            if (assistantMsg.toolCalls.isNotEmpty()) {
                // 这个 assistant 和紧随其后的 tool results 必须一起保留在 tail 中
                // splitIndex 已经指向它，无需再调整
                return splitIndex
            }
        }

        // 如果 splitIndex 指向的是一个普通消息（非 tool 相关），直接使用
        return splitIndex
    }
}
