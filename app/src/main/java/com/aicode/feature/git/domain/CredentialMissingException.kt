package com.aicode.feature.git.domain

/**
 * push/pull 时 https 远程缺少匹配凭据。UI 捕获后弹登录框，用户填完凭据后自动重试原操作--
 * 仿 Windows git 无凭据时弹窗登录的体验。
 *
 * @param host 需要凭据的远程主机（小写归一），用于预填弹窗的 host 字段。
 */
class CredentialMissingException(val host: String) : Exception("需要 $host 的登录凭据")
