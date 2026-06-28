package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.permission.PermissionScope
import com.aicodeeditor.feature.agent.domain.permission.ToolPermissionPolicyEngine
import com.aicodeeditor.feature.agent.domain.prompt.SystemPromptProvider
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.provider.AIResponse
import com.aicodeeditor.feature.agent.domain.provider.AIStreamChunk
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.StreamingAgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionManager
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.agent.domain.tool.ToolStreamEvent
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * 阶段三重构 (完全版)：基于不可变状态 (Immutable State) 与 MVI 架构的 Agent 工作流引擎。
 * 通过定义明确的 AgentSessionState, AgentAction 与 AgentSideEffect，
 * 采用 Reducer 来进行状态扭转，将纯函数的业务逻辑与带有副作用的外部环境操作完全解耦。
 */
class StatefulAgentWorkflow @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val aiProviderRepository: AIProviderRepository,
    private val openAIProvider: AIProvider,
    private val anthropicProvider: AIProvider,
    private val promptProvider: SystemPromptProvider,
    private val permissionManager: ToolPermissionManager,
    private val policyEngine: ToolPermissionPolicyEngine,
    private val contextCompactor: ContextCompactor
) : AgentWorkflow {

    private companion object {
        const val TAG = "StatefulAgentWorkflow"
        const val MAX_ITERATIONS = 50
        const val LIVE_TAIL_CHARS = 8_000
        const val PROGRESS_INTERVAL_MS = 100L
    }

    /** 不可变状态树 */
    data class AgentSessionState(
        val messages: List<AgentMessage> = emptyList(),
        val iterations: Int = 0,
        val isFinished: Boolean = false,
        val error: String? = null,
        val pendingToolCalls: List<ToolCall> = emptyList(),
        val executingToolCall: ToolCall? = null
    )

    /** 改变状态的动作 (Action) */
    sealed interface AgentAction {
        data class InitRequest(val initialMessages: List<AgentMessage>) : AgentAction
        data class LlmResponse(val response: AIResponse) : AgentAction
        data class LlmError(val error: String) : AgentAction
        data class PermissionEvaluated(val toolCall: ToolCall, val approved: Boolean, val argsPreview: String) : AgentAction
        data class ToolFinished(val id: String, val toolName: String, val result: String, val isError: Boolean) : AgentAction
    }

    /** 需要在外部环境中执行的副作用 (SideEffect) */
    sealed interface AgentSideEffect {
        object CallLlm : AgentSideEffect
        data class RequestPermission(val toolCall: ToolCall) : AgentSideEffect
        data class ExecuteTool(val toolCall: ToolCall, val argsPreview: String) : AgentSideEffect
    }

    private suspend fun getActiveProvider(sessionId: String?): AIProvider {
        val config = aiProviderRepository.getActiveProviderSync()
            ?: throw IllegalStateException("尚未配置 AI 服务商，请到设置中添加并选择一个")
        if (config.apiKey.isBlank()) throw IllegalStateException("「${config.name}」未填写 API Key")
        if (config.effectiveModel.isBlank()) throw IllegalStateException("「${config.name}」未选择模型")

        val provider = if (config.type == ProviderType.ANTHROPIC) anthropicProvider else openAIProvider
        provider.apiKey = config.apiKey
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.logSessionId = sessionId
        return provider
    }

    /** 核心 Reducer，接收旧状态与 Action，返回新状态以及触发的副作用列表 (纯函数) */
    private fun reduce(
        state: AgentSessionState,
        action: AgentAction
    ): Pair<AgentSessionState, List<AgentSideEffect>> {
        var newState = state
        val effects = mutableListOf<AgentSideEffect>()

        when (action) {
            is AgentAction.InitRequest -> {
                newState = state.copy(messages = action.initialMessages)
                effects.add(AgentSideEffect.CallLlm)
            }
            is AgentAction.LlmResponse -> {
                val assistantMsg = AgentMessage.AssistantMessage(
                    content = action.response.content,
                    toolCalls = action.response.toolCalls
                )
                newState = state.copy(
                    messages = state.messages + assistantMsg,
                    iterations = state.iterations + 1
                )
                
                if (action.response.toolCalls.isEmpty()) {
                    if (action.response.isTruncated) {
                        newState = newState.copy(
                            messages = newState.messages + AgentMessage.UserMessage(content = "你的回复因长度限制被截断了，请从截断处继续。")
                        )
                        effects.add(AgentSideEffect.CallLlm)
                    } else {
                        newState = newState.copy(isFinished = true)
                    }
                } else {
                    val pending = action.response.toolCalls.toList()
                    val nextTool = pending.first()
                    newState = newState.copy(pendingToolCalls = pending.drop(1), executingToolCall = nextTool)
                    effects.add(AgentSideEffect.RequestPermission(nextTool))
                }
            }
            is AgentAction.LlmError -> {
                newState = state.copy(isFinished = true, error = action.error)
            }
            is AgentAction.PermissionEvaluated -> {
                if (action.approved) {
                    effects.add(AgentSideEffect.ExecuteTool(action.toolCall, action.argsPreview))
                } else {
                    val rawResult = ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
                    return reduce(state, AgentAction.ToolFinished(action.toolCall.id, action.toolCall.name, rawResult, true))
                }
            }
            is AgentAction.ToolFinished -> {
                val toolResultMsg = AgentMessage.ToolResultMessage(
                    id = action.id,
                    toolName = action.toolName,
                    result = action.result
                )
                newState = state.copy(
                    messages = state.messages + toolResultMsg,
                    executingToolCall = null
                )
                
                if (newState.pendingToolCalls.isNotEmpty()) {
                    val nextTool = newState.pendingToolCalls.first()
                    newState = newState.copy(pendingToolCalls = newState.pendingToolCalls.drop(1), executingToolCall = nextTool)
                    effects.add(AgentSideEffect.RequestPermission(nextTool))
                } else {
                    effects.add(AgentSideEffect.CallLlm)
                }
            }
        }
        
        // 限制最大迭代次数
        if (!newState.isFinished && newState.iterations >= MAX_ITERATIONS && effects.contains(AgentSideEffect.CallLlm)) {
            newState = newState.copy(isFinished = true, error = "达到最大迭代次数限制 ($MAX_ITERATIONS 次)")
            effects.clear()
        }
        
        return Pair(newState, effects)
    }

    override suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
        val currentTime = java.time.ZonedDateTime.now().format(formatter)
        val enrichedRequest = "$userRequest\n\n[System] 当前本地时间: $currentTime"

        var state = AgentSessionState()
        val actionQueue = ArrayDeque<AgentAction>()
        actionQueue.addLast(AgentAction.InitRequest(context.history + AgentMessage.UserMessage(content = enrichedRequest)))

        val systemPrompt = promptProvider.build(context)
        val aiProvider = getActiveProvider(context.sessionId)

        while (!state.isFinished && actionQueue.isNotEmpty()) {
            val action = actionQueue.removeFirst()
            val (newState, effects) = reduce(state, action)
            state = newState

            for (effect in effects) {
                when (effect) {
                    is AgentSideEffect.CallLlm -> {
                        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, aiProvider)
                        try {
                            val aiResponse = aiProvider.complete(systemPrompt, compactedMessages, tools)
                            actionQueue.addLast(AgentAction.LlmResponse(aiResponse))
                        } catch (e: Exception) {
                            actionQueue.addLast(AgentAction.LlmError("LLM 调用失败: ${e.message}"))
                        }
                    }
                    is AgentSideEffect.RequestPermission -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val argsPreview = JsonObject(effect.toolCall.arguments).toString().take(500)
                        val approved = requestPermissionIfNeeded(tool, effect.toolCall.id, effect.toolCall.arguments, argsPreview)
                        actionQueue.addLast(AgentAction.PermissionEvaluated(effect.toolCall, approved, argsPreview))
                    }
                    is AgentSideEffect.ExecuteTool -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val rawResult = runToolSync(tool, effect.toolCall.name, effect.toolCall.arguments)
                        val isError = tool == null || rawResult.startsWith("Error")
                        actionQueue.addLast(AgentAction.ToolFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError))
                    }
                }
            }
        }

        return WorkflowResult(
            status = if (state.error == null) WorkflowStatus.SUCCESS else WorkflowStatus.FAILED,
            result = extractFinalContent(state),
            changes = emptyList(),
            errors = listOfNotNull(state.error),
            messages = state.messages
        )
    }

    override fun executeEvents(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<AgentEvent> = flow {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
        val currentTime = java.time.ZonedDateTime.now().format(formatter)
        val enrichedRequest = "$userRequest\n\n[System] 当前本地时间: $currentTime"

        var state = AgentSessionState()
        val actionQueue = ArrayDeque<AgentAction>()
        actionQueue.addLast(AgentAction.InitRequest(context.history + AgentMessage.UserMessage(content = enrichedRequest)))

        val systemPrompt = promptProvider.build(context)
        val aiProvider = getActiveProvider(context.sessionId)

        while (!state.isFinished && actionQueue.isNotEmpty()) {
            val action = actionQueue.removeFirst()
            val (newState, effects) = reduce(state, action)
            state = newState

            for (effect in effects) {
                when (effect) {
                    is AgentSideEffect.CallLlm -> {
                        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, aiProvider) { emit(it) }
                        
                        val acc = StringBuilder()
                        val reasoningAcc = StringBuilder()
                        var finalResponse: AIResponse? = null
                        
                        try {
                            aiProvider.completeStream(systemPrompt, compactedMessages, tools).collect { chunk ->
                                when (chunk) {
                                    is AIStreamChunk.TextDelta -> {
                                        acc.append(chunk.text)
                                        emit(AgentEvent.AssistantDelta(acc.toString()))
                                    }
                                    is AIStreamChunk.ReasoningDelta -> {
                                        reasoningAcc.append(chunk.text)
                                        emit(AgentEvent.ReasoningDelta(reasoningAcc.toString()))
                                    }
                                    is AIStreamChunk.Final -> finalResponse = chunk.response
                                }
                            }
                            val aiResponse = finalResponse ?: AIResponse(content = acc.toString())
                            
                            if (aiResponse.content.isNotBlank() || aiResponse.toolCalls.isNotEmpty()) {
                                emit(AgentEvent.AssistantText(aiResponse.content, aiResponse.toolCalls, reasoningAcc.toString()))
                            }
                            actionQueue.addLast(AgentAction.LlmResponse(aiResponse))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            val partial = acc.toString()
                            if (partial.isNotEmpty()) {
                                emit(AgentEvent.AssistantText(partial, emptyList(), reasoningAcc.toString()))
                            }
                            actionQueue.addLast(AgentAction.LlmError("LLM 调用失败: ${e.message}"))
                        }
                    }
                    is AgentSideEffect.RequestPermission -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val argsPreview = JsonObject(effect.toolCall.arguments).toString().take(500)
                        val approved = requestPermissionIfNeeded(tool, effect.toolCall.id, effect.toolCall.arguments, argsPreview)
                        
                        if (!approved) {
                            val rawResult = ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
                            emit(AgentEvent.ToolCallFinished(effect.toolCall.id, effect.toolCall.name, rawResult, true, argsPreview))
                        } else {
                            emit(AgentEvent.ToolCallStarted(effect.toolCall.id, effect.toolCall.name, argsPreview))
                        }
                        actionQueue.addLast(AgentAction.PermissionEvaluated(effect.toolCall, approved, argsPreview))
                    }
                    is AgentSideEffect.ExecuteTool -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val rawResult = if (tool is StreamingAgentTool) {
                            runToolStream(tool, effect.toolCall) { emit(it) }
                        } else {
                            runToolSync(tool, effect.toolCall.name, effect.toolCall.arguments)
                        }
                        val isError = tool == null || rawResult.startsWith("Error")
                        emit(AgentEvent.ToolCallFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError))
                        actionQueue.addLast(AgentAction.ToolFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError))
                    }
                }
            }
        }
        
        emit(AgentEvent.Completed)
    }

    private suspend fun runToolSync(tool: AgentTool?, name: String, arguments: Map<String, kotlinx.serialization.json.JsonElement>): String {
        if (tool == null) return "Error: 工具 $name 不存在"
        return try {
            tool.execute(arguments).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: 工具执行失败: ${e.message}"
        }
    }

    private suspend fun runToolStream(
        tool: StreamingAgentTool, 
        toolCall: ToolCall, 
        onEvent: suspend (AgentEvent) -> Unit
    ): String {
        val live = StringBuilder()
        var lastEmitMs = 0L
        var finalResult: ToolResult? = null
        try {
            tool.executeStream(toolCall.arguments).collect { ev ->
                when (ev) {
                    is ToolStreamEvent.Progress -> {
                        live.append(ev.chunk).append('\n')
                        if (live.length > LIVE_TAIL_CHARS) {
                            live.delete(0, live.length - LIVE_TAIL_CHARS)
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= PROGRESS_INTERVAL_MS) {
                            lastEmitMs = now
                            onEvent(AgentEvent.ToolCallProgress(toolCall.id, toolCall.name, live.toString()))
                        }
                    }
                    is ToolStreamEvent.Completed -> finalResult = ev.result
                }
            }
            return (finalResult ?: ToolResult.Error("流式工具未返回结果")).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Error: 工具执行失败: ${e.message}"
        }
    }

    private suspend fun requestPermissionIfNeeded(
        tool: AgentTool?,
        callId: String,
        arguments: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String
    ): Boolean {
        if (tool == null || tool.permissionPolicy == ToolPermissionPolicy.AUTO_APPROVE) return true

        val eval = policyEngine.evaluate(tool.name, arguments)
        return when (eval.verdict) {
            ToolPermissionPolicyEngine.Verdict.ALLOW -> true
            ToolPermissionPolicyEngine.Verdict.DENY -> false
            ToolPermissionPolicyEngine.Verdict.ASK -> {
                val request = tool.buildPermissionRequest(callId, arguments, argsPreview)
                    .copy(rememberablePatterns = eval.rememberablePatterns)
                when (permissionManager.awaitApproval(request)) {
                    PermissionChoice.REJECT -> false
                    PermissionChoice.ONCE -> true
                    PermissionChoice.ALWAYS -> {
                        if (eval.rememberablePatterns.isNotEmpty()) {
                            policyEngine.remember(tool.name, eval.rememberablePatterns, PermissionScope.PROJECT)
                        }
                        true
                    }
                }
            }
        }
    }

    private fun extractFinalContent(state: AgentSessionState): String {
        for (i in state.messages.indices.reversed()) {
            val msg = state.messages[i]
            if (msg is AgentMessage.AssistantMessage && msg.content.isNotBlank()) {
                return msg.content.trim()
            }
        }
        return ""
    }
}
