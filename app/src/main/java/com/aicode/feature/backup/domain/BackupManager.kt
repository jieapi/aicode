package com.aicode.feature.backup.domain

/**
 * 备份编排器：从各数据源采集快照并打包，或反向解包还原。
 *
 * 导出：按 [BackupOptions] 采集 → 序列化为 JSON → tar.gz 压缩 → 口令非空则 AES-GCM 加密。
 * 导入：口令非空则先解密 → 解 tar.gz → 反序列化 → 校验 schemaVersion → 合并写入各数据源。
 */
interface BackupManager {
    /** 生成备份文件字节。password 为 null 或空时不加密，输出明文 tar.gz。 */
    suspend fun export(password: CharArray?, options: BackupOptions): ByteArray

    /**
     * 解包并还原备份文件。
     * @param password 备份未加密时传 null 或空；加密文件必须提供正确口令。
     * @return 还原统计（各数据段条目数）；口令错误/格式不符/版本过高时返回失败。
     */
    suspend fun import(data: ByteArray, password: CharArray?): Result<RestoreStats>
}

/** 导出数据范围选项；未勾选的段在快照中保持空值，导入时跳过。 */
data class BackupOptions(
    val providers: Boolean = true,
    val gitCredentials: Boolean = true,
    val remoteConnections: Boolean = true,
    val chatHistory: Boolean = true,
    val mcpServers: Boolean = true,
    val permissionRules: Boolean = true,
    val appSettings: Boolean = true
)

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
