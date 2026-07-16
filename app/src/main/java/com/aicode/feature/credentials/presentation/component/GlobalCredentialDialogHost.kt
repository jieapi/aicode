package com.aicode.feature.credentials.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.feature.credentials.data.CredentialRequestBridge

/**
 * 全局凭据弹窗宿主：订阅 [CredentialRequestBridge.request]，非 null 即渲染 [CredentialPromptDialog]。
 *
 * 挂在应用根 Composable（覆盖所有页面），使命令行 git 缺凭据（AI Bash / 交互终端）触发弹窗时，
 * 用户当前在聊天页/终端页/Git 页都能弹——凭据检测由容器内自定义 credential helper 统一兜底，
 * 与用户所在页面无关。用户确认 → [CredentialRequestBridge.respond]、取消 → [CredentialRequestBridge.cancel]。
 */
@Composable
fun GlobalCredentialDialogHost(bridge: CredentialRequestBridge) {
    val request by bridge.request.collectAsStateWithLifecycle()
    request?.let { req ->
        CredentialPromptDialog(
            host = req.host,
            onConfirm = { username, token ->
                bridge.respond(req.requestId, req.host, username, token)
            },
            onDismiss = { bridge.cancel(req.requestId) }
        )
    }
}
