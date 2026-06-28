package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextCompactor @Inject constructor() {

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
     * @return 压缩后的新列表（如果没有触发压缩则返回原列表的副本）
     */
    suspend fun compactIfNeeded(
        messages: List<AgentMessage>,
        aiProvider: AIProvider,
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
        // 我们保留最新的 MIN_TAIL_MESSAGES 条消息，以及任何最近的未完成的 ToolCall。
        // 简单起见，这里直接截断前一半（向上取整）
        val splitIndex = messages.size - MIN_TAIL_MESSAGES
        if (splitIndex <= 0) {
            onEvent(AgentEvent.ToolCallFinished(callId, "context_compactor", "不满足截断条件", false))
            return messages.toList()
        }

        val head = messages.subList(0, splitIndex)
        val tail = messages.subList(splitIndex, messages.size)

        val headContent = head.joinToString("\n\n") { msg ->
            when (msg) {
                is AgentMessage.UserMessage -> "[USER]:\n${msg.content}"
                is AgentMessage.AssistantMessage -> "[ASSISTANT]:\n${msg.content}"
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

        val compactedMessage = AgentMessage.AssistantMessage(
            content = "【系统提示：早期的对话已被压缩，以下是之前的核心状态摘要】\n$summaryResponse",
            toolCalls = emptyList()
        )

        val newMessages = mutableListOf<AgentMessage>()
        newMessages.add(compactedMessage)
        newMessages.addAll(tail)

        return newMessages
    }
}
