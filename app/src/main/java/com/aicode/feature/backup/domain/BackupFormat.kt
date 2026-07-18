package com.aicode.feature.backup.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 备份文件二进制格式：`[魔数 4B][格式版本 1B][盐 16B][IV 12B][密文]`。
 *
 * 魔数用于导入时快速识别本格式；格式版本用于未来不兼容变更。盐与 IV 随每次导出随机生成。
 */
object BackupFormat {
    /** 文件头魔数（ASCII "ACKP" = AiCode BacKuP）。 */
    const val MAGIC: Int = 0x41434B50

    const val SALT_LEN = 16
    const val IV_LEN = 12
    const val HEADER_LEN = 4 + 1 + SALT_LEN + IV_LEN // 33

    /** 把快照序列化字节加密打包成完整备份文件字节。 */
    fun pack(formatVersion: Int, salt: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(salt.size == SALT_LEN && iv.size == IV_LEN)
        return ByteBuffer.allocate(HEADER_LEN + ciphertext.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(MAGIC)
            put(formatVersion.toByte())
            put(salt)
            put(iv)
            put(ciphertext)
        }.array()
    }

    /** 解析备份文件头，返回 (格式版本, 盐, IV, 密文起始偏移)；非本格式返回 null。 */
    fun unpack(data: ByteArray): Header? {
        if (data.size < HEADER_LEN) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        if (buf.int != MAGIC) return null
        val formatVersion = buf.get().toInt() and 0xFF
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        return Header(formatVersion, salt, iv, HEADER_LEN)
    }

    data class Header(val formatVersion: Int, val salt: ByteArray, val iv: ByteArray, val ciphertextOffset: Int) {
        override fun equals(other: Any?) = other is Header && formatVersion == other.formatVersion
        override fun hashCode() = formatVersion
    }
}
