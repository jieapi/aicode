package com.aicode.feature.backup.domain

/**
 * 备份编排器：从各数据源采集快照并加密打包，或反向解密还原。
 *
 * 导出：采集 → 序列化为 JSON → AES-GCM 加密 → 打包成备份文件字节。
 * 导入：解包文件头 → 解密 → 反序列化 → 校验 schemaVersion → 合并写入各数据源。
 */
interface BackupManager {
    /** 生成加密备份文件字节。 */
    suspend fun export(password: CharArray): ByteArray

    /**
     * 解密并还原备份文件。
     * @return 还原统计（各数据段条目数）；口令错误/格式不符/版本过高时返回失败。
     */
    suspend fun import(data: ByteArray, password: CharArray): Result<RestoreStats>
}

data class RestoreStats(
    val providers: Int = 0,
    val gitCredentials: Int = 0,
    val remoteConnections: Int = 0,
    val remoteMounts: Int = 0,
    val chatSessions: Int = 0,
    val agentMessages: Int = 0,
    val todoItems: Int = 0,
    val mcpServers: Int = 0,
    val globalPermissionRules: Int = 0
)
