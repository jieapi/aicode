package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /status —— 查看当前会话状态（token 用量、模型、模式等），
 * 结果以 Markdown 表格作为 AI 气泡输出。
 */
class StatusCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/status"
    override val label = "会话状态"
    override val description = "查看当前会话的 token 用量、模型、模式等信息"

    override fun execute(context: SlashCommandContext) {
        context.showSessionStatus()
    }
}
