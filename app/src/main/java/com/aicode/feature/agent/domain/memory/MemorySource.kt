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

    companion object {
        /**
         * 将模型传入的记忆名归一化为安全文件名片段。
         *
         * 记忆名来自大模型工具参数（自由文本），不经处理直接拼到 "$name.md" 会引入路径穿越
         * （如 "../x" 会写到 memoryRoot 之外）、含分隔符的非法文件名等问题。
         * 这里只保留「字母/数字/连字符/下划线」 Unicode 字符，其余替换为 '-'，并去首尾连字符；
         * 结果只含合法文件名字符、不含路径分隔符，且对同一输入确定性可逆，保证
         * 「写出去的文件名 ↔ MemoryParser.parse 回读的 name」一致。
         */
        fun sanitizeName(raw: String): String {
            val cleaned = raw.map { ch ->
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
            }.joinToString("").trim('-')
            // 全非法字符时回退一个占位名，避免空文件名
            return if (cleaned.isBlank()) "memory" else cleaned
        }

        /**
         * 解析 memory 名对应的磁盘文件，并校验解析后的 canonical 路径仍落在 [root] 之内，
         * 杜绝 sanitize 漏网导致的越界写入。
         */
        fun resolveMemoryFile(root: File, name: String): File {
            val safe = sanitizeName(name)
            return File(root, "$safe.md")
        }
    }
}
