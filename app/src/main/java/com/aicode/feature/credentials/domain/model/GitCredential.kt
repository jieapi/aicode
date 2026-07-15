package com.aicode.feature.credentials.domain.model

/** 一条 Git 远程仓库凭据的领域模型。无 Android 依赖。 */
data class GitCredential(
    val id: String,
    val host: String,
    val username: String,
    val token: String,
    val label: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
