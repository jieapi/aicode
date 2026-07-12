package com.aicode.feature.agent.domain.workflow

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentImage
import com.aicode.feature.agent.domain.model.AgentMessage
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.model.WorkflowResult
import com.aicode.feature.agent.domain.model.WorkflowStatus
import com.aicode.feature.agent.domain.permission.PermissionChoice
import com.aicode.feature.agent.domain.permission.PermissionScope
import com.aicode.feature.agent.domain.permission.ToolPermissionPolicyEngine
import com.aicode.feature.agent.domain.prompt.SystemPromptProvider
import com.aicode.feature.agent.domain.provider.AIProvider
import com.aicode.feature.agent.domain.provider.AIResponse
import com.aicode.feature.agent.domain.provider.AIStreamChunk
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.StreamingAgentTool
import com.aicode.feature.agent.domain.tool.ToolCall
import com.aicode.feature.agent.domain.tool.mode.PlanApprovalChoice
import com.aicode.feature.agent.domain.tool.mode.PlanApprovalManager
import com.aicode.feature.agent.domain.tool.ToolPermissionManager
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolRegistry
import com.aicode.feature.agent.domain.tool.ToolResult
import com.aicode.feature.agent.domain.tool.ToolOutputStore
import com.aicode.feature.agent.domain.tool.ToolStreamEvent
import com.aicode.feature.agent.domain.tool.toTransportString
import com.aicode.feature.settings.data.remote.ModelMetadataService
import com.aicode.feature.settings.data.repository.VisionModelSettingsRepository
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 阶段三重构 (完全版)：基于不可变状态 (Immutable State) 与 MVI 架构的 Agent 工作流引擎。
 * 通过定义明确的 AgentSessionState, AgentAction 与 AgentSideEffect，
 * 采用 Reducer 来进行状态扭转，将纯函数的业务逻辑与带有副作用的外部环境操作完全解耦。
 */
class StatefulAgentWorkflow @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val aiProviderRepository: AIProviderRepository,
    @param:javax.inject.Named("OpenAIProvider") private val openAIProvider: AIProvider,
    @param:javax.inject.Named("AnthropicProvider") private val anthropicProvider: AIProvider,
    @param:javax.inject.Named("GeminiProvider") private val geminiProvider: AIProvider,
    private val promptProvider: SystemPromptProvider,
    private val permissionManager: ToolPermissionManager,
    private val policyEngine: ToolPermissionPolicyEngine,
    private val contextCompactor: ContextCompactor,
    private val planApprovalManager: PlanApprovalManager,
    private val toolOutputStore: ToolOutputStore,
    private val modelMetadataService: ModelMetadataService,
    private val visionModelSettingsRepository: VisionModelSettingsRepository
) : AgentWorkflow {

    private companion object {
        const val TAG = "StatefulAgentWorkflow"
        const val MAX_ITERATIONS = 50
        const val LIVE_TAIL_CHARS = 4_000
        const val PROGRESS_INTERVAL_MS = 250L
        const val USER_REJECTED_CODE = "USER_REJECTED"
    }

    /** 不可变状态树 */
    data class AgentSessionState(
        val messages: List<AgentMessage> = emptyList(),
        val iterations: Int = 0,
        val isFinished: Boolean = false,
        val error: String? = null,
        val pendingToolCalls: List<ToolCall> = emptyList(),
        val executingToolCall: ToolCall? = null,
        /** 标记下一轮 CallLlm 用于识图——若当前聊天模型不支持 vision 则临时切到识图专用模型发送。 */
        val pendingVisionRound: Boolean = false
    )

    /** 改变状态的动作 (Action) */
    sealed interface AgentAction {
        data class InitRequest(val initialMessages: List<AgentMessage>) : AgentAction
        data class LlmResponse(val response: AIResponse) : AgentAction
        data class LlmError(val error: String) : AgentAction
        data class PermissionEvaluated(
            val toolCall: ToolCall,
            val approved: Boolean,
            val argsPreview: String,
            val denyReason: String = "用户拒绝执行该工具",
            val errorCode: String = "USER_REJECTED"
        ) : AgentAction
        data class ToolFinished(
            val id: String,
            val toolName: String,
            val result: String,
            val isError: Boolean,
            val images: List<AgentImage> = emptyList()
        ) : AgentAction
    }

    private data class PermissionCheckResult(
        val approved: Boolean,
        val denyReason: String = "用户拒绝执行该工具",
        val errorCode: String = "USER_REJECTED"
    )

    private data class ToolRunResult(
        val raw: String,
        val isError: Boolean,
        val images: List<AgentImage> = emptyList()
    )

    /** 需要在外部环境中执行的副作用 (SideEffect) */
    sealed interface AgentSideEffect {
        object CallLlm : AgentSideEffect
        data class RequestPermission(val toolCall: ToolCall) : AgentSideEffect
        data class ExecuteTool(val toolCall: ToolCall, val argsPreview: String) : AgentSideEffect
    }

    private suspend fun getActiveProvider(sessionId: String?): AIProvider {
        val config = aiProviderRepository.getActiveProviderSync()
            ?: throw IllegalStateException("尚未配置 AI 提供商，请到设置中添加并选择一个")
        if (config.apiKey.isBlank()) throw IllegalStateException("「${config.name}」未填写 API Key")
        if (config.effectiveModel.isBlank()) throw IllegalStateException("「${config.name}」未选择模型")
        return getProviderFor(config, sessionId)
    }

    /**
     * 据 [config] 选定对应的 provider 单例实例并填入其连接字段后返回。
     * 复用于「当前聊天模型」与「识图专用模型」两条路径。
     *
     * 注意：三个 provider 实例是注入的单例且字段可变（apiKey/baseUrl/apiPath/useResponseApi/model/logSessionId），
     * 调用方在用本方法切换到不同于当前的 provider 后，必须在发送请求的 try/finally 里保存并恢复所有可变字段，
     * 以免污染后续轮次——见 effect 执行层识图轮的 save/Restore。
     */
    private fun getProviderFor(config: AIProviderConfig, sessionId: String?): AIProvider {
        val provider = when (config.type) {
            ProviderType.ANTHROPIC -> anthropicProvider
            ProviderType.GEMINI -> geminiProvider
            else -> openAIProvider
        }
        provider.apiKey = config.apiKey
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.apiPath = if (config.apiPath.isNotBlank()) config.apiPath else provider.apiPath
        provider.useResponseApi = config.useResponseApi
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
                    toolCalls = action.response.toolCalls,
                    reasoning = action.response.reasoning ?: ""
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
                    val rawResult = ToolResult.Error(action.denyReason, action.errorCode).toTransportString()
                    if (action.errorCode == USER_REJECTED_CODE) {
                        newState = state.copy(
                            messages = state.messages + AgentMessage.ToolResultMessage(
                                id = action.toolCall.id,
                                toolName = action.toolCall.name,
                                result = rawResult
                            ),
                            pendingToolCalls = emptyList(),
                            executingToolCall = null,
                            isFinished = true
                        )
                        return newState to emptyList()
                    }
                    return reduce(state, AgentAction.ToolFinished(action.toolCall.id, action.toolCall.name, rawResult, true))
                }
            }
            is AgentAction.ToolFinished -> {
                val toolResultMsg = AgentMessage.ToolResultMessage(
                    id = action.id,
                    toolName = action.toolName,
                    result = action.result,
                    images = action.images
                )
                val appendedMessages = if (action.images.isEmpty()) {
                    listOf(toolResultMsg)
                } else {
                    listOf(
                        toolResultMsg,
                        AgentMessage.UserMessage(
                            content = "已附加 ${action.toolName} 读取的图片，供下一轮视觉分析使用。",
                            images = action.images
                        )
                    )
                }
                newState = state.copy(
                    messages = state.messages + appendedMessages,
                    executingToolCall = null
                )
                
                if (newState.pendingToolCalls.isNotEmpty()) {
                    val nextTool = newState.pendingToolCalls.first()
                    newState = newState.copy(pendingToolCalls = newState.pendingToolCalls.drop(1), executingToolCall = nextTool)
                    effects.add(AgentSideEffect.RequestPermission(nextTool))
                } else {
                    // 本批工具已全部执行完，即将发给 LLM；若本次 viewImage 产出了图片，标记下一轮为识图轮。
                    if (action.images.isNotEmpty()) {
                        newState = newState.copy(pendingVisionRound = true)
                    }
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

        var currentContext = context
        var state = AgentSessionState()
        var currentTools = tools
        val actionQueue = ArrayDeque<AgentAction>()
        actionQueue.addLast(
            AgentAction.InitRequest(
                currentContext.history + AgentMessage.UserMessage(
                    content = enrichedRequest,
                    images = currentContext.inputImages
                )
            )
        )

        var systemPrompt = promptProvider.build(currentContext)
        val aiProvider = getActiveProvider(currentContext.sessionId)

        while (!state.isFinished && actionQueue.isNotEmpty()) {
            val action = actionQueue.removeFirst()
            val (newState, effects) = reduce(state, action)
            state = newState

            for (effect in effects) {
                when (effect) {
                    is AgentSideEffect.CallLlm -> {
                        // 识图轮：若当前聊天模型无 vision，临时切到识图专用模型发送，发完恢复三单例字段。
                        val visionRound = if (state.pendingVisionRound) resolveVisionFallbackProvider() else null
                        val providerInUse = visionRound?.provider ?: aiProvider
                        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, providerInUse, context.sessionId)
                        if (compactedMessages !== state.messages) {
                            state = state.copy(messages = compactedMessages)
                        }
                        try {
                            val aiResponse = providerInUse.complete(systemPrompt, compactedMessages, currentTools)
                            actionQueue.addLast(AgentAction.LlmResponse(aiResponse))
                        } catch (e: Exception) {
                            actionQueue.addLast(AgentAction.LlmError("LLM 调用失败: ${e.message}"))
                        } finally {
                            visionRound?.restore()
                            if (state.pendingVisionRound) state = state.copy(pendingVisionRound = false)
                        }
                    }
                    is AgentSideEffect.RequestPermission -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val argsPreview = JsonObject(effect.toolCall.arguments).toString().take(500)
                        val checkResult = requestPermissionIfNeeded(tool, effect.toolCall.id, effect.toolCall.arguments, argsPreview, currentContext.mode)
                        actionQueue.addLast(AgentAction.PermissionEvaluated(effect.toolCall, checkResult.approved, argsPreview, checkResult.denyReason, checkResult.errorCode))
                    }
                    is AgentSideEffect.ExecuteTool -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        var runResult = runToolSync(tool, effect.toolCall, currentContext)
                        var rawResult = runResult.raw
                        var isError = runResult.isError
                        val (newCtx, updated) = checkAndUpdateMode(effect.toolCall, isError, currentContext)
                        if (updated) {
                            val reason = (effect.toolCall.arguments["reason"] as? JsonPrimitive)?.content?.trim()
                                ?: effect.toolCall.arguments["reason"]?.toString()?.replace("\"", "")?.trim()
                                ?: ""

                            // PLAN→BUILD 时挂起 workflow，等待用户在计划审查面板批准后才继续
                            if (newCtx.mode == AgentMode.BUILD) {
                                val choice = planApprovalManager.awaitApproval(reason, currentContext.sessionId)
                                if (choice == PlanApprovalChoice.APPROVE) {
                                    currentContext = newCtx
                                    systemPrompt = promptProvider.build(currentContext)
                                } else {
                                    // 用户选择继续反馈，回滚到 PLAN 模式，修正工具结果让 AI 知道切换被取消
                                    currentContext = currentContext.copy(mode = AgentMode.PLAN)
                                    systemPrompt = promptProvider.build(currentContext)
                                    rawResult = ToolResult.Error("用户拒绝了模式切换请求，请继续在 PLAN 模式下完善方案，待用户认可后再次申请切换。", "MODE_SWITCH_REJECTED").toTransportString()
                                    isError = true
                                }
                            } else {
                                currentContext = newCtx
                                systemPrompt = promptProvider.build(currentContext)
                            }
                        }
                        actionQueue.addLast(AgentAction.ToolFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError, runResult.images))
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

        var currentContext = context
        var state = AgentSessionState()
        var currentTools = tools
        val actionQueue = ArrayDeque<AgentAction>()
        actionQueue.addLast(
            AgentAction.InitRequest(
                currentContext.history + AgentMessage.UserMessage(
                    content = enrichedRequest,
                    images = currentContext.inputImages
                )
            )
        )

        var systemPrompt = promptProvider.build(currentContext)
        val aiProvider = getActiveProvider(currentContext.sessionId)

        while (!state.isFinished && actionQueue.isNotEmpty()) {
            val action = actionQueue.removeFirst()
            val (newState, effects) = reduce(state, action)
            state = newState

            for (effect in effects) {
                when (effect) {
                    is AgentSideEffect.CallLlm -> {
                        // 识图轮：若当前聊天模型无 vision，临时切到识图专用模型发送，发完恢复三单例字段。
                        val visionRound = if (state.pendingVisionRound) resolveVisionFallbackProvider() else null
                        val providerInUse = visionRound?.provider ?: aiProvider
                        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, providerInUse, context.sessionId) { emit(it) }
                        if (compactedMessages !== state.messages) {
                            state = state.copy(messages = compactedMessages)
                        }

                        val acc = StringBuilder()
                        val reasoningAcc = StringBuilder()
                        var finalResponse: AIResponse? = null

                        try {
                            providerInUse.completeStream(systemPrompt, compactedMessages, currentTools).collect { chunk ->
                                when (chunk) {
                                    is AIStreamChunk.TextDelta -> {
                                        acc.append(chunk.text)
                                        emit(AgentEvent.AssistantDelta(acc.toString()))
                                    }
                                    is AIStreamChunk.ReasoningDelta -> {
                                        reasoningAcc.append(chunk.text)
                                        emit(AgentEvent.ReasoningDelta(reasoningAcc.toString()))
                                    }
                                    is AIStreamChunk.Retrying -> emit(AgentEvent.Retrying(chunk.attempt, chunk.maxRetries))
                                    is AIStreamChunk.Final -> finalResponse = chunk.response
                                }
                            }
                            val aiResponse = finalResponse ?: AIResponse(content = acc.toString())
                            // 将本轮 reasoning 附加到 AIResponse，以便 reduce 时存入 AssistantMessage 并在下一轮回传
                            val responseWithReasoning = if (reasoningAcc.isNotEmpty()) {
                                aiResponse.copy(reasoning = reasoningAcc.toString())
                            } else aiResponse

                            if (aiResponse.content.isNotBlank() || aiResponse.toolCalls.isNotEmpty()) {
                                emit(AgentEvent.AssistantText(aiResponse.content, aiResponse.toolCalls, reasoningAcc.toString()))
                            }
                            actionQueue.addLast(AgentAction.LlmResponse(responseWithReasoning))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            val partial = acc.toString()
                            val reasoning = reasoningAcc.toString()
                            // 流式被中断时也要落库已收到的思考：否则下方 finally 会清空流式思考气泡，
                            // 而落库的接力消息又没产生，表现为「思考显示后凭空消失且无报错」。
                            // 有正文或有思考其一即落库；两者皆空则不写空消息。
                            if (partial.isNotEmpty() || reasoning.isNotBlank()) {
                                emit(AgentEvent.AssistantText(partial, emptyList(), reasoning))
                            }
                            actionQueue.addLast(AgentAction.LlmError("LLM 调用失败: ${e.message}"))
                        } finally {
                            visionRound?.restore()
                            if (state.pendingVisionRound) state = state.copy(pendingVisionRound = false)
                        }
                    }
                    is AgentSideEffect.RequestPermission -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        val argsPreview = JsonObject(effect.toolCall.arguments).toString().take(500)
                        val checkResult = requestPermissionIfNeeded(tool, effect.toolCall.id, effect.toolCall.arguments, argsPreview, currentContext.mode)

                        if (!checkResult.approved) {
                            val rawResult = ToolResult.Error(checkResult.denyReason, checkResult.errorCode).toTransportString()
                            emit(AgentEvent.ToolCallFinished(effect.toolCall.id, effect.toolCall.name, rawResult, true, argsPreview))
                        } else {
                            emit(AgentEvent.ToolCallStarted(effect.toolCall.id, effect.toolCall.name, argsPreview))
                        }
                        actionQueue.addLast(AgentAction.PermissionEvaluated(effect.toolCall, checkResult.approved, argsPreview, checkResult.denyReason, checkResult.errorCode))
                    }
                    is AgentSideEffect.ExecuteTool -> {
                        val tool = toolRegistry.getTool(effect.toolCall.name)
                        var runResult = if (tool is StreamingAgentTool) {
                            runToolStream(tool, effect.toolCall) { emit(it) }
                        } else {
                            // 同步兜底
                            runToolSync(tool, effect.toolCall, currentContext)
                        }
                        var rawResult = runResult.raw
                        var isError = runResult.isError
                        val (newCtx, updated) = checkAndUpdateMode(effect.toolCall, isError, currentContext)
                        if (updated) {
                            val reason = (effect.toolCall.arguments["reason"] as? JsonPrimitive)?.content?.trim()
                                ?: effect.toolCall.arguments["reason"]?.toString()?.replace("\"", "")?.trim()
                                ?: ""
                            emit(AgentEvent.ModeChanged(newCtx.mode, reason))

                            // PLAN→BUILD 时挂起 workflow，等待用户在计划审查面板批准后才继续
                            if (newCtx.mode == AgentMode.BUILD) {
                                val choice = planApprovalManager.awaitApproval(reason, currentContext.sessionId)
                                if (choice == PlanApprovalChoice.APPROVE) {
                                    currentContext = newCtx
                                    systemPrompt = promptProvider.build(currentContext)
                                } else {
                                    // 用户选择继续反馈，回滚到 PLAN 模式，修正工具结果让 AI 知道切换被取消
                                    currentContext = currentContext.copy(mode = AgentMode.PLAN)
                                    systemPrompt = promptProvider.build(currentContext)
                                    rawResult = ToolResult.Error("用户拒绝了模式切换请求，请继续在 PLAN 模式下完善方案，待用户认可后再次申请切换。", "MODE_SWITCH_REJECTED").toTransportString()
                                    isError = true
                                }
                            } else {
                                currentContext = newCtx
                                systemPrompt = promptProvider.build(currentContext)
                            }
                        }
                        emit(AgentEvent.ToolCallFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError))
                        actionQueue.addLast(AgentAction.ToolFinished(effect.toolCall.id, effect.toolCall.name, rawResult, isError, runResult.images))
                    }
                }
            }
        }
        
        state.error?.let { emit(AgentEvent.Failed(it)) }
        emit(AgentEvent.Completed)
    }

    private suspend fun runToolSync(tool: AgentTool?, toolCall: ToolCall, context: AgentContext): ToolRunResult {
        val name = toolCall.name
        if (tool == null) {
            return ToolRunResult(ToolResult.Error("工具 $name 不存在", "TOOL_NOT_FOUND").toTransportString(), true)
        }
        return try {
            if (name == "viewImage" && !activeModelSupportsVision() && !visionFallbackReady()) {
                return ToolRunResult(
                    ToolResult.Error(
                        "当前聊天模型不支持图片输入，且未配置支持 Vision 的识图专用模型。请在「设置 → 识图模型」中指定一个支持 Vision 的模型后再查看图片。",
                        "MODEL_VISION_UNSUPPORTED"
                    ).toTransportString(),
                    true
                )
            }
            val result = tool.executeWithContext(toolCall.arguments, context)
            val images = if (name == "viewImage") extractInlineImages(result) else emptyList()
            val transportResult = if (images.isNotEmpty()) stripInlineImages(result) else result
            val processed = toolOutputStore.process(name, toolCall.id, transportResult)
            ToolRunResult(processed.toTransportString(), processed is ToolResult.Error, images)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolRunResult(ToolResult.Error("工具执行失败: ${e.message}", "TOOL_EXECUTION_FAILED").toTransportString(), true)
        }
    }

    private suspend fun activeModelSupportsVision(): Boolean {
        val config = aiProviderRepository.getActiveProviderSync() ?: return false
        val metadata = modelMetadataService.resolve(config.type, config.effectiveModel)
        return metadata.supportsVision
    }

    /**
     * 识图专用兜底模型是否可用：已配置 providerId 且指向的 provider/model 存在、有 apiKey、且其
     * ModelMetadata.supportsVision 为真。识图轮仅当 [activeModelSupportsVision] 为 false 时才回退到它。
     * 未配置（providerId 空）即视为「跟随聊天模型」，不构成兜底 → 返回 false。
     */
    private suspend fun visionFallbackReady(): Boolean {
        val providerId = visionModelSettingsRepository.getVisionProviderId().trim()
        if (providerId.isEmpty()) return false
        val model = visionModelSettingsRepository.getVisionModel().trim()
        if (model.isEmpty()) return false
        val config = aiProviderRepository.getProviderById(providerId) ?: return false
        if (!config.isEnabled) return false
        if (config.apiKey.isBlank()) return false
        val metadata = modelMetadataService.resolve(config.type, model)
        return metadata.supportsVision
    }

    /** 一次 provider 单例可变字段的快照，用于识图轮临切后恢复。 */
    private data class ProviderSnapshot(
        val provider: AIProvider,
        val apiKey: String,
        val baseUrl: String,
        val apiPath: String,
        val useResponseApi: Boolean,
        val model: String,
        val logSessionId: String?
    )

    private fun snapshotProvider(provider: AIProvider) = ProviderSnapshot(
        provider = provider,
        apiKey = provider.apiKey,
        baseUrl = provider.baseUrl,
        apiPath = provider.apiPath,
        useResponseApi = provider.useResponseApi,
        model = provider.model,
        logSessionId = provider.logSessionId
    )

    private fun restoreProvider(snap: ProviderSnapshot) {
        snap.provider.apiKey = snap.apiKey
        snap.provider.baseUrl = snap.baseUrl
        snap.provider.apiPath = snap.apiPath
        snap.provider.useResponseApi = snap.useResponseApi
        snap.provider.model = snap.model
        snap.provider.logSessionId = snap.logSessionId
    }

    /**
     * 识图轮专用 provider 解析。仅当当前聊天模型不支持 vision、且专用模型已配置且可用时返回
     * 已填好连接字段、已对原单例做好快照的专用 provider；否则返回 null（表示无需切换、沿用 aiProvider）。
     * 调用方必须在发送完请求的 finally 里调用 [restoreProvider] 用 [VisionRoundEnv.snapshot] 恢复。
     */
    private suspend fun resolveVisionFallbackProvider(): VisionRoundEnv? {
        if (activeModelSupportsVision()) return null // 当前聊天模型就有原生能力，直接用之
        if (!visionFallbackReady()) return null       // 无可用兜底，仍沿用 aiProvider（守卫已先行拦截并报错）
        val providerId = visionModelSettingsRepository.getVisionProviderId().trim()
        val model = visionModelSettingsRepository.getVisionModel().trim()
        val config = aiProviderRepository.getProviderById(providerId)
            ?: error("识图专用模型配置丢失")
        if (config.apiKey.isBlank()) error("识图专用模型「${config.name}」未填写 API Key")
        if (model.isBlank()) error("识图专用模型未指定模型")
        // 切到专用 provider 实例（getProviderFor 会改写其单例字段），先对三个单例都做快照，finally 全量恢复——最稳。
        val snapshots = listOf(snapshotProvider(anthropicProvider), snapshotProvider(openAIProvider), snapshotProvider(geminiProvider))
        val provider = getProviderFor(config.copy(selectedModel = model), null)
        return VisionRoundEnv(provider = provider, snapshots = snapshots)
    }

    /** 识图轮 provider 切换上下文：持有专用 provider 实例与三个单例的快照。inner 以便调用外层 [restoreProvider]。 */
    private inner class VisionRoundEnv(val provider: AIProvider, val snapshots: List<ProviderSnapshot>) {
        /** 恢复三个单例的可变字段。 */
        fun restore() = snapshots.forEach { restoreProvider(it) }
    }

    private suspend fun runToolStream(
        tool: StreamingAgentTool, 
        toolCall: ToolCall, 
        onEvent: suspend (AgentEvent) -> Unit
    ): ToolRunResult {
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
            val result = finalResult ?: ToolResult.Error("流式工具未返回结果", "MISSING_STREAM_RESULT")
            val processed = toolOutputStore.process(toolCall.name, toolCall.id, result)
            return ToolRunResult(processed.toTransportString(), processed is ToolResult.Error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ToolRunResult(ToolResult.Error("工具执行失败: ${e.message}", "TOOL_EXECUTION_FAILED").toTransportString(), true)
        }
    }

    private fun checkAndUpdateMode(toolCall: ToolCall, isError: Boolean, currentContext: AgentContext): Pair<AgentContext, Boolean> {
        if (toolCall.name == "switchMode" && !isError) {
            val targetModeStr = (toolCall.arguments["mode"] as? JsonPrimitive)?.content?.trim()?.uppercase()
                ?: toolCall.arguments["mode"]?.toString()?.replace("\"", "")?.trim()?.uppercase()
            if (targetModeStr != null) {
                runCatching { AgentMode.valueOf(targetModeStr) }.getOrNull()?.let { newMode ->
                    if (currentContext.mode != newMode) {
                        return currentContext.copy(mode = newMode) to true
                    }
                }
            }
        }
        return currentContext to false
    }

    private fun extractInlineImages(result: ToolResult): List<AgentImage> {
        val data = (result as? ToolResult.Success)?.data as? JsonObject ?: return emptyList()
        val image = data["image"] as? JsonObject ?: return emptyList()
        val mimeType = image["mime_type"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val base64Data = image["base64_data"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val path = image["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!mimeType.startsWith("image/") || base64Data.isBlank()) return emptyList()
        return listOf(AgentImage(mimeType = mimeType, base64Data = base64Data, path = path))
    }

    private fun stripInlineImages(result: ToolResult): ToolResult {
        val success = result as? ToolResult.Success ?: return result
        val data = success.data as? JsonObject ?: return result
        val image = data["image"] as? JsonObject ?: return result
        val strippedImage = image.toMutableMap().apply {
            remove("base64_data")
            this["base64_omitted"] = JsonPrimitive(true)
            this["note"] = JsonPrimitive("图片数据已作为视觉输入附加，未写入文本工具结果。")
        }
        val strippedData = data.toMutableMap().apply {
            this["image"] = JsonObject(strippedImage)
            this["image_attached"] = JsonPrimitive(true)
        }
        return ToolResult.Success(JsonObject(strippedData))
    }

    private suspend fun requestPermissionIfNeeded(
        tool: AgentTool?,
        callId: String,
        arguments: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String,
        mode: com.aicode.feature.agent.domain.model.AgentMode
    ): PermissionCheckResult {
        if (tool == null) {
            return PermissionCheckResult(true)
        }

        val eval = policyEngine.evaluate(tool, tool.name, arguments, mode)
        if (eval.verdict == ToolPermissionPolicyEngine.Verdict.DENY) {
            val reason = eval.denyReason ?: "该工具被项目安全规则策略禁止执行"
            val code = if (mode == com.aicode.feature.agent.domain.model.AgentMode.PLAN) "PLAN_MODE_REJECTED" else "SYSTEM_DENIED"
            return PermissionCheckResult(false, reason, code)
        }

        if (tool.permissionPolicy == ToolPermissionPolicy.AUTO_APPROVE) {
            return PermissionCheckResult(true)
        }

        return when (eval.verdict) {
            ToolPermissionPolicyEngine.Verdict.ALLOW -> PermissionCheckResult(true)
            ToolPermissionPolicyEngine.Verdict.DENY -> PermissionCheckResult(false)
            ToolPermissionPolicyEngine.Verdict.ASK -> {
                val request = tool.buildPermissionRequest(callId, arguments, argsPreview)
                    .copy(
                        rememberablePatterns = eval.rememberablePatterns,
                        rememberDisabledReason = eval.rememberDisabledReason
                    )
                when (permissionManager.awaitApproval(request)) {
                    PermissionChoice.REJECT -> PermissionCheckResult(false, "用户拒绝执行该工具", "USER_REJECTED")
                    PermissionChoice.ONCE -> PermissionCheckResult(true)
                    PermissionChoice.ALWAYS -> {
                        if (eval.rememberablePatterns.isNotEmpty()) {
                            policyEngine.remember(tool.name, eval.rememberablePatterns, PermissionScope.PROJECT)
                        }
                        PermissionCheckResult(true)
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
