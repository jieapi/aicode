package com.aicode.feature.agent.domain.memory

import com.aicode.core.util.FileLogger
import org.yaml.snakeyaml.Yaml
import java.io.File

object MemoryParser {
    private const val TAG = "MemoryParser"
    private const val MAX_DESC_CHARS = 500

    fun parse(file: File, scope: MemoryScope): Memory? {
        val text = try {
            if (!file.isFile || !file.canRead()) return null
            file.readText()
        } catch (e: Exception) {
            FileLogger.w(TAG, "读取 Memory 文件失败: ${file.absolutePath}", e)
            return null
        }

        val (frontmatter, body) = splitAndParseFrontmatter(text)

        val name = frontmatter["name"]?.toString()?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val description = (frontmatter["description"]?.toString() ?: "").take(MAX_DESC_CHARS)

        return Memory(
            name = name,
            description = description,
            scope = scope,
            file = file,
            content = body.trim()
        )
    }

    fun format(name: String, description: String, content: String): String {
        val safeName = yamlScalar(name)
        val safeDesc = yamlScalar(description)
        return "---\nname: $safeName\ndescription: $safeDesc\n---\n$content"
    }

    /** 把任意字符串转成安全的 YAML 标量，避免冒号/引号/换行破坏 frontmatter。 */
    private fun yamlScalar(value: String): String {
        val needsQuote = value.contains(':') || value.contains('#') ||
            value.contains('"') || value.contains('\'') ||
            value.startsWith('-') || value.startsWith(' ') || value.endsWith(' ') ||
            value.contains('\n') || value.isBlank()
        return if (needsQuote) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }
    }

    private fun splitAndParseFrontmatter(text: String): Pair<Map<String, Any>, String> {
        val normalized = text.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return emptyMap<String, Any>() to normalized

        val end = normalized.indexOf("\n---", startIndex = 3)
        if (end < 0) return emptyMap<String, Any>() to normalized

        val block = normalized.substring(4, end)
        val rest = normalized.substring(end + 4).removePrefix("\n")

        val map = try {
            val yaml = Yaml()
            val loaded = yaml.load<Map<String, Any>>(block)
            loaded ?: emptyMap()
        } catch (e: Exception) {
            FileLogger.w(TAG, "解析 YAML 失败", e)
            emptyMap()
        }

        return map to rest
    }
}
