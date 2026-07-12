package com.aicode.feature.agent.domain.tool

import com.aicode.feature.agent.domain.permission.PermissionChoice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ToolPermissionManager @Inject constructor() {
    private val requestMutex = Mutex()
    private val _pendingRequest = MutableStateFlow<PendingToolPermission?>(null)
    val pendingRequest: StateFlow<PendingToolPermission?> = _pendingRequest.asStateFlow()

    private var currentId: String? = null
    private var currentDecision: CompletableDeferred<PermissionChoice>? = null

    /** 挂起等待用户在弹窗中的选择（拒绝/本次/始终）。同一时刻只允许一个待决请求（mutex 串行化）。 */
    suspend fun awaitApproval(request: PendingToolPermission): PermissionChoice = requestMutex.withLock {
        val decision = CompletableDeferred<PermissionChoice>()
        currentId = request.id
        currentDecision = decision
        _pendingRequest.value = request

        try {
            decision.await()
        } finally {
            if (currentId == request.id) {
                currentId = null
                currentDecision = null
                _pendingRequest.value = null
            }
        }
    }

    /** UI 回传用户选择，唤醒挂起的 [awaitApproval]。 */
    @Synchronized
    fun resolve(id: String, choice: PermissionChoice) {
        if (currentId != id) return
        _pendingRequest.value = null
        currentDecision?.complete(choice)
    }
}
