package com.aicodeeditor.feature.agent.domain.skill

import com.aicodeeditor.core.util.FileLogger
import org.yaml.snakeyaml.Yaml
import java.io.File

object SkillParser {
    private const val TAG = "SkillParser"
    private const val MAX_DESC_CHARS = 500

    /**
     * 解析一个 skill 目录；无 SKILL.md 或无 name 时视为非法，返回 null。
     */
    fun parse(dir: File): Skill? {
        val file = File(dir, "SKILL.md")
        val text = try {
            if (!file.isFile || !file.canRead()) return null
            file.readText()
        } catch (e: Exception) {
            FileLogger.w(TAG, "读取 SKILL.md 失败: ${file.absolutePath}", e)
            return null
        }

        val (frontmatter, body) = splitAndParseFrontmatter(text)
        
        // name 优先取 frontmatter，缺省回退到目录名
        val name = frontmatter["name"]?.toString()?.takeIf { it.isNotBlank() } ?: dir.name
        val description = (frontmatter["description"]?.toString() ?: "").take(MAX_DESC_CHARS)
        
        val requiredTools = try {
            val toolsRaw = frontmatter["required_tools"]
            if (toolsRaw is List<*>) {
                toolsRaw.filterIsInstance<String>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "解析 required_tools 失败: ${file.absolutePath}", e)
            emptyList()
        }

        return Skill(
            name = name,
            description = description,
            requiredTools = requiredTools,
            dir = dir,
            instructions = body.trim()
        )
    }

    /**
     * 利用 SnakeYAML 切分并解析 YAML frontmatter。
     * @return (frontmatter 键值对, 正文)
     */
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
