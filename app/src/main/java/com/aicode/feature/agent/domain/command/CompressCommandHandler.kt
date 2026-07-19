package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /compress —— 手动触发当前会话的上下文压缩，
 * 复用 ContextCompactor 的压缩逻辑。
 */
class CompressCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/compress"
    override val label = "压缩上下文"
    override val description = "手动触发当前会话的上下文压缩"

    override fun execute(context: SlashCommandContext) {
        context.compactCurrentSession()
    }
}
