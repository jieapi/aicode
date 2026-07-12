package com.aicode.feature.agent.domain.tool.question

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 ask_user_question 工具的 park-and-resume 流程。
 *
 * 镜像 [com.aicode.feature.agent.domain.tool.ToolPermissionManager] 的设计：
 * 工具侧调用 [awaitAnswer] 挂起；UI 侧观察 [pendingQuestion] 渲染面板；
 * 用户选择后调用 [resolve] 唤醒工具侧。
 *
 * 同一时刻只允许一个待决问题（mutex 串行化），与权限弹窗互不干扰。
 */
@Singleton
class AskUserQuestionManager @Inject constructor() {

    private val requestMutex = Mutex()

    private val _pendingQuestion = MutableStateFlow<PendingUserQuestion?>(null)

    /** UI 观察此 StateFlow 来决定是否显示问题面板。 */
    val pendingQuestion: StateFlow<PendingUserQuestion?> = _pendingQuestion.asStateFlow()

    private var currentId: String? = null
    private var currentDecision: CompletableDeferred<UserQuestionAnswer>? = null

    /**
     * 工具侧调用：发起一个问题请求，挂起等待用户回答。
     * 同一时刻只允许一个请求（由 [requestMutex] 保证串行）。
     */
    suspend fun awaitAnswer(question: PendingUserQuestion): UserQuestionAnswer = requestMutex.withLock {
        val decision = CompletableDeferred<UserQuestionAnswer>()
        currentId = question.id
        currentDecision = decision
        _pendingQuestion.value = question

        try {
            decision.await()
        } finally {
            if (currentId == question.id) {
                currentId = null
                currentDecision = null
                _pendingQuestion.value = null
            }
        }
    }

    /**
     * UI 侧调用：用户完成选择后，将答案回传，唤醒挂起的 [awaitAnswer]。
     */
    @Synchronized
    fun resolve(id: String, answer: UserQuestionAnswer) {
        if (currentId != id) return
        _pendingQuestion.value = null
        currentDecision?.complete(answer)
    }
}
