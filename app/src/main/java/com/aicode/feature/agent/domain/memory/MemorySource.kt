package com.aicode.feature.agent.domain.memory

import java.io.File

/**
 * 记忆数据源抽象接口，支持全局和项目级。
 */
interface MemorySource {
    /** 扫描并返回该数据源下当前所有合法的 Memory。 */
    fun listMemories(): List<Memory>

    /** 读取指定 memory 的完整指令正文；不存在或解析失败时返回 null。 */
    fun loadContent(name: String): String?

    /** 保存一条记忆（创建或覆盖） */
    fun saveMemory(name: String, description: String, content: String): Boolean

    /** 删除一条记忆 */
    fun deleteMemory(name: String): Boolean
}
