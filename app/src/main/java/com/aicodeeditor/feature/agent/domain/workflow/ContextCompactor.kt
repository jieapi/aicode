package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.CONTEXT_COMPACTION_MARKER
import com.aicodeeditor.feature.agent.domain.model.CONTEXT_SUMMARY_LEGACY_PREFIX
import com.aicodeeditor.feature.agent.domain.model.id
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.settings.data.remote.ModelMetadataService
import com.aicodeeditor.feature.settings.domain.model.ModelContextPolicy
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextCompactor @Inject constructor(
    private val agentMessageDao: AgentMessageDao,
    private val modelMetadataService: ModelMetadataService
) {

    private companion object {
        const val TAG = "ContextCompactor"

        const val TOOL_OUTPUT_MAX_CHARS = 2_000
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
        val totalTokens = estimateTokens(messages)
        val metadata = modelMetadataService.resolve(inferProviderType(aiProvider), aiProvider.model)
        val usableTokens = ModelContextPolicy.usableInputTokens(metadata)
        if (totalTokens < usableTokens || messages.size <= 2) {
            return messages.toList()
        }

        FileLogger.i(TAG, "上下文约 $totalTokens tokens，超过可用窗口 $usableTokens/${metadata.contextTokens}，触发压缩机制。")
        onEvent(AgentEvent.CompactionStarted(totalTokens))

        // 拆分 Head（需要压缩的老数据）和 Tail（保留的新数据）
        var splitIndex = selectTailStartIndex(messages, usableTokens)
        if (splitIndex <= 0) {
            onEvent(AgentEvent.CompactionFinished)
            return messages.toList()
        }

        // 确保 tail 的第一条消息不是孤立的 ToolResultMessage：
        // 如果 tail 以 ToolResultMessage 开头，需要向前回溯到其配对的 AssistantMessage(with toolCalls)，
        // 否则压缩后摘要 assistant 消息不含 toolCalls，导致 tool 消息变成孤立的，API 报 400。
        splitIndex = adjustSplitIndex(messages, splitIndex)

        val head = messages.subList(0, splitIndex)
        val tail = messages.subList(splitIndex, messages.size)
        val previousSummary = extractPreviousSummary(messages)
        val headForSummary = removeCompactionPairs(head)

        val headContent = headForSummary.joinToString("\n\n") { msg ->
            when (msg) {
                is AgentMessage.UserMessage -> "[USER]:\n${msg.content}"
                is AgentMessage.AssistantMessage -> {
                    val base = "[ASSISTANT]:\n${msg.content}"
                    if (msg.toolCalls.isNotEmpty()) {
                        val toolCallsDesc = msg.toolCalls.joinToString(", ") { "${it.name}(${it.arguments})" }
                        "$base\n[TOOL_CALLS]: $toolCallsDesc"
                    } else base
                }
                is AgentMessage.ToolResultMessage -> "[TOOL_RESULT (${msg.toolName})]:\n${msg.result.truncateForSummary()}"
            }
        }

        val summaryPrompt = buildSummaryPrompt(previousSummary, headContent)

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
            onEvent(AgentEvent.CompactionFinished)
            return messages.toList() // 失败则原样返回，交由上层自行承担溢出风险
        }

        FileLogger.i(TAG, "上下文压缩完成，摘要长度：${summaryResponse.length}")

        val markerId = UUID.randomUUID().toString()
        val compactedId = UUID.randomUUID().toString()
        val markerMessage = AgentMessage.UserMessage(
            id = markerId,
            content = CONTEXT_COMPACTION_MARKER
        )
        val compactedMessage = AgentMessage.AssistantMessage(
            id = compactedId,
            content = summaryResponse,
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

                // 插入内部 compaction user marker + assistant summary，时间戳放在 head 和 tail 之间。
                agentMessageDao.insert(
                    AgentMessageEntity(
                        id = markerId,
                        sessionId = sessionId,
                        role = MessageRole.USER.name,
                        content = CONTEXT_COMPACTION_MARKER,
                        timestamp = cutoffTimestamp - 2,
                        isCompactionMarker = true
                    )
                )
                agentMessageDao.insert(
                    AgentMessageEntity(
                        id = compactedId,
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT.name,
                        content = compactedMessage.content,
                        timestamp = cutoffTimestamp - 1,
                        isContextSummary = true
                    )
                )
                FileLogger.i(TAG, "已持久化压缩结果到数据库，会话 $sessionId")
            } catch (e: Exception) {
                FileLogger.e(TAG, "持久化压缩结果失败", e)
            }
        }
        onEvent(AgentEvent.CompactionFinished)

        val newMessages = mutableListOf<AgentMessage>()
        newMessages.add(markerMessage)
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

    private fun selectTailStartIndex(messages: List<AgentMessage>, usableTokens: Int): Int {
        val budget = ModelContextPolicy.preserveRecentTokens(usableTokens)
        var total = 0
        var splitIndex = messages.size

        for (index in messages.indices.reversed()) {
            val next = estimateTokens(messages[index])
            if (total + next > budget && splitIndex < messages.size) break
            total += next
            splitIndex = index
        }

        return splitIndex
    }

    private fun estimateTokens(messages: List<AgentMessage>): Int =
        messages.sumOf { estimateTokens(it) }

    private fun estimateTokens(message: AgentMessage): Int {
        val chars = when (message) {
            is AgentMessage.UserMessage -> message.content.length
            is AgentMessage.AssistantMessage -> {
                message.content.length + message.reasoning.length +
                    message.toolCalls.sumOf { it.name.length + it.arguments.toString().length }
            }
            is AgentMessage.ToolResultMessage -> message.toolName.length + message.result.length
        }
        return ModelContextPolicy.estimateTokens(chars)
    }

    private fun inferProviderType(aiProvider: AIProvider): ProviderType {
        val className = aiProvider::class.simpleName.orEmpty()
        return when {
            "Anthropic" in className -> ProviderType.ANTHROPIC
            "Gemini" in className -> ProviderType.GEMINI
            else -> ProviderType.OPENAI
        }
    }

    private fun buildSummaryPrompt(previousSummary: String?, headContent: String): String {
        val instruction = if (previousSummary.isNullOrBlank()) {
            "请根据下面的对话历史创建一个新的锚定摘要。"
        } else {
            """
                请根据下面的新对话历史更新已有锚定摘要。
                保留仍然正确的信息，移除过时信息，并合并新事实。

                <previous-summary>
                $previousSummary
                </previous-summary>
            """.trimIndent()
        }

        return """
            你是一个高度专业的上下文压缩引擎。$instruction

            必须严格输出以下 Markdown 结构，保持标题顺序不变：
            ## Goal
            - [一句话概括用户目标]

            ## Constraints & Preferences
            - [用户约束、偏好、规格，或 "(none)"]

            ## Progress
            ### Done
            - [已完成工作，或 "(none)"]

            ### In Progress
            - [正在进行的工作，或 "(none)"]

            ### Blocked
            - [阻塞问题，或 "(none)"]

            ## Key Decisions
            - [关键决定及原因，或 "(none)"]

            ## Next Steps
            - [接下来按顺序执行的步骤，或 "(none)"]

            ## Critical Context
            - [重要技术事实、错误、开放问题，或 "(none)"]

            ## Relevant Files
            - [文件或目录路径：相关原因，或 "(none)"]

            规则：
            - 每个章节都必须保留。
            - 使用简短 bullet，不写客套话。
            - 保留精确文件路径、命令、错误文本和标识符。
            - 不要提到“摘要过程”或“上下文已压缩”。

            对话历史：
            $headContent
        """.trimIndent()
    }

    private fun extractPreviousSummary(messages: List<AgentMessage>): String? {
        for (index in messages.indices.reversed()) {
            val current = messages[index]
            val next = messages.getOrNull(index + 1)
            if (
                current is AgentMessage.UserMessage &&
                current.content == CONTEXT_COMPACTION_MARKER &&
                next is AgentMessage.AssistantMessage
            ) {
                return next.content.cleanSummary()
            }
            if (
                current is AgentMessage.AssistantMessage &&
                current.content.startsWith(CONTEXT_SUMMARY_LEGACY_PREFIX)
            ) {
                return current.content.cleanSummary()
            }
        }
        return null
    }

    private fun removeCompactionPairs(messages: List<AgentMessage>): List<AgentMessage> {
        val result = mutableListOf<AgentMessage>()
        var index = 0
        while (index < messages.size) {
            val current = messages[index]
            val next = messages.getOrNull(index + 1)
            if (
                current is AgentMessage.UserMessage &&
                current.content == CONTEXT_COMPACTION_MARKER &&
                next is AgentMessage.AssistantMessage
            ) {
                index += 2
                continue
            }
            if (
                current is AgentMessage.AssistantMessage &&
                current.content.startsWith(CONTEXT_SUMMARY_LEGACY_PREFIX)
            ) {
                index++
                continue
            }
            result.add(current)
            index++
        }
        return result
    }

    private fun String.cleanSummary(): String =
        removePrefix(CONTEXT_SUMMARY_LEGACY_PREFIX).trimStart()

    private fun String.truncateForSummary(): String {
        if (length <= TOOL_OUTPUT_MAX_CHARS) return this
        return take(TOOL_OUTPUT_MAX_CHARS) + "\n[Tool output truncated for compaction]"
    }
}
