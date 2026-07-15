package com.aicode.feature.credentials.domain.model

import kotlin.random.Random

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

/** 生成新凭据 id：时间戳 + 随机后缀（避免同毫秒冲突），供凭据编辑页与 git 拉取/推送缺凭据弹窗共用。 */
fun newCredentialId(): String = "${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}"

