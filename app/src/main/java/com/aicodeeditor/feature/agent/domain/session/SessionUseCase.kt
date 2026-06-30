package com.aicodeeditor.feature.agent.domain.session

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.dao.ChatSessionDao
import com.aicodeeditor.feature.agent.data.local.entity.ChatSessionEntity
import com.aicodeeditor.feature.agent.presentation.MessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionUseCase @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val agentMessageDao: AgentMessageDao
) {
    companion object {
        private const val TAG = "SessionUseCase"
        const val TITLE_MAX = 20
        const val PENDING_TOOL_MARKER = "⏳"
        const val INTERRUPTED_TOOL_TEXT = "⏹ 执行被中断（应用已关闭）"
    }

    /** 冷启动收尾：上次进程被杀时若有工具正在执行，其 ⏳ 占位行会永久显示「执行中」。 */
    suspend fun initColdStartCleanup() {
        runCatching {
            val n = agentMessageDao.markPendingToolsInterrupted(
                toolRole = MessageRole.TOOL.name,
                pendingPrefix = "$PENDING_TOOL_MARKER%",
                interruptedContent = INTERRUPTED_TOOL_TEXT
            )
            if (n > 0) FileLogger.i(TAG, "冷启动收尾 $n 条残留「执行中」工具行为已中断")
        }.onFailure { FileLogger.e(TAG, "回收残留执行中工具行失败", it) }
    }

    fun newSessionEntity(workspacePath: String): ChatSessionEntity {
        val now = System.currentTimeMillis()
        return ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "新会话",
            workspacePath = workspacePath,
            createdAt = now,
            updatedAt = now
        )
    }

    fun deriveTitle(request: String): String {
        val clean = request.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= TITLE_MAX) clean.ifBlank { "新对话" }
        else clean.take(TITLE_MAX) + "…"
    }

    /** 删除会话，返回需要清理的状态 id 集合，由 ViewModel 执行状态清理。 */
    suspend fun deleteSession(id: String): String {
        agentMessageDao.deleteBySession(id)
        chatSessionDao.delete(id)
        return id
    }

    suspend fun getFirstSessionOfWorkspace(workspacePath: String): ChatSessionEntity? {
        return chatSessionDao.getAllSessionsByWorkspaceOnce(workspacePath).firstOrNull()
    }

    suspend fun upsertSession(entity: ChatSessionEntity) {
        chatSessionDao.upsert(entity)
    }

    suspend fun updateTitle(sessionId: String, title: String) {
        chatSessionDao.updateTitle(sessionId, title)
    }

    suspend fun touch(sessionId: String, timestamp: Long) {
        chatSessionDao.touch(sessionId, timestamp)
    }

    suspend fun getSessionById(id: String): ChatSessionEntity? {
        return chatSessionDao.getById(id)
    }

    suspend fun updateMode(sessionId: String, mode: String) {
        val s = chatSessionDao.getById(sessionId) ?: return
        chatSessionDao.upsert(s.copy(mode = mode))
    }

    suspend fun isSessionEmpty(sessionId: String): Boolean {
        return agentMessageDao.getMessagesBySessionOnce(sessionId).isEmpty()
    }
}
