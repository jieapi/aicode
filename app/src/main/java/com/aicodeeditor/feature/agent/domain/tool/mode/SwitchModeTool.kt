package com.aicodeeditor.feature.agent.domain.tool.mode

import com.aicodeeditor.feature.agent.data.local.dao.ChatSessionDao
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMode
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.PendingToolPermission
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 让 AI 可以主动申请切换当前会话的模式（PLAN / BUILD）。
 */
class SwitchModeTool @Inject constructor(
    private val chatSessionDao: ChatSessionDao
) : AgentTool() {

    override val name = "switch_mode"
    override val description = "切换当前会话的模式。如果你当前处于 BUILD（构建）模式并认为你需要进入 PLAN（计划）模式来构思复杂逻辑，或者当前在 PLAN 模式下计划已经完成需要进入 BUILD 模式修改代码时，调用此工具主动申请切换。切换前需要用户授权。"
    override val permissionPolicy = ToolPermissionPolicy.ASK

    override val parameters: Map<String, ToolParameter> = mapOf(
        "mode" to ToolParameter(
            name = "mode",
            type = ParameterType.STRING,
            description = "目标模式，必须是 'PLAN' 或 'BUILD'",
            required = true,
            enum = listOf("PLAN", "BUILD")
        ),
        "reason" to ToolParameter(
            name = "reason",
            type = ParameterType.STRING,
            description = "切换模式的理由，将展示给用户",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return ToolResult.Error("SwitchModeTool requires context", "MISSING_CONTEXT")
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val targetModeStr = args["mode"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
            ?: return ToolResult.Error("缺少必需参数: mode", "MISSING_MODE")
            
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数: reason", "MISSING_REASON")

        val targetMode = try {
            AgentMode.valueOf(targetModeStr)
        } catch (e: Exception) {
            return ToolResult.Error("无效的模式: $targetModeStr，只能是 PLAN 或 BUILD", "INVALID_MODE")
        }

        if (context.mode == targetMode) {
            return ToolResult.Success(JsonPrimitive("当前已处于 ${targetMode.name} 模式，无需切换。"))
        }

        val sessionId = context.sessionId
        if (sessionId == null) {
            return ToolResult.Error("未关联会话 ID，无法切换模式", "NO_SESSION")
        }

        val sessionEntity = chatSessionDao.getById(sessionId)
            ?: return ToolResult.Error("找不到会话记录", "SESSION_NOT_FOUND")

        // 切换模式并保存到数据库。UI 层通过 flow 监听，会自动更新外观与后续流程的上下文
        chatSessionDao.upsert(sessionEntity.copy(mode = targetMode.name))

        return ToolResult.Success(JsonPrimitive("成功切换至 ${targetMode.name} 模式。"))
    }

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "无理由"
        
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "模式切换申请",
            summary = "AI 申请切换为 $mode 模式",
            details = "目标模式：$mode\n\n申请理由：$reason",
            argsPreview = argsPreview,
            rememberablePatterns = emptyList()
        )
    }
}
