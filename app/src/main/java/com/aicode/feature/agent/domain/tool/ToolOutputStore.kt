package com.aicode.feature.agent.domain.tool

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.ContainerInstaller
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class StoredToolOutput(
    val preview: String,
    val truncated: Boolean,
    val totalChars: Long,
    val outputPath: String? = null,
    val storageError: String? = null
)

@Singleton
class ToolOutputStore @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "ToolOutputStore"
        const val AICODE_ROOT = "/root/.aicode"
        const val OUTPUT_DIR = "tool-output"
        const val HEAD_CHARS = 20_000
        const val TAIL_CHARS = 20_000
        const val MAX_INLINE_CHARS = HEAD_CHARS + TAIL_CHARS
        val LARGE_TEXT_FIELDS = listOf("output", "content", "text", "stdout", "stderr", "body", "result")
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }

    private val json = Json { encodeDefaults = true }

    fun process(toolName: String, callId: String, result: ToolResult): ToolResult {
        return when (result) {
            is ToolResult.Success -> ToolResult.Success(processElement(toolName, callId, result.data))
            is ToolResult.Partial -> ToolResult.Partial(processElement(toolName, callId, result.data), result.message)
            is ToolResult.Error -> result
        }
    }

    fun boundText(toolName: String, callId: String, text: String): StoredToolOutput {
        if (text.length <= MAX_INLINE_CHARS) {
            return StoredToolOutput(
                preview = text,
                truncated = false,
                totalChars = text.length.toLong()
            )
        }

        val writeResult = writeFullOutput(toolName, callId, text)
        val preview = buildPreview(text, writeResult.outputPath)
        return StoredToolOutput(
            preview = preview,
            truncated = true,
            totalChars = text.length.toLong(),
            outputPath = writeResult.outputPath,
            storageError = writeResult.storageError
        )
    }

    private fun processElement(toolName: String, callId: String, element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive
        if (primitive?.isString == true) {
            val stored = boundText(toolName, callId, primitive.content)
            return if (stored.truncated) stored.toJsonObject("output") else element
        }

        val obj = element as? JsonObject
        if (obj != null) {
            val largeField = LARGE_TEXT_FIELDS
                .mapNotNull { key ->
                    val field = obj[key] as? JsonPrimitive
                    val value = if (field?.isString == true) field.content else null
                    if (value != null && value.length > MAX_INLINE_CHARS) key to value else null
                }
                .maxByOrNull { it.second.length }

            if (largeField != null) {
                val stored = boundText(toolName, callId, largeField.second)
                val updated = obj.toMutableMap()
                updated[largeField.first] = JsonPrimitive(stored.preview)
                addMetadata(updated, stored)
                return JsonObject(updated)
            }
        }

        val serialized = json.encodeToString(element)
        if (serialized.length <= MAX_INLINE_CHARS) return element

        val stored = boundText(toolName, callId, serialized)
        return stored.toJsonObject("content")
    }

    private fun addMetadata(target: MutableMap<String, JsonElement>, stored: StoredToolOutput) {
        target["output_truncated"] = JsonPrimitive(stored.truncated)
        target["output_total_chars"] = JsonPrimitive(stored.totalChars)
        stored.outputPath?.let { target["output_path"] = JsonPrimitive(it) }
        stored.storageError?.let { target["output_storage_error"] = JsonPrimitive(it) }
    }

    private fun StoredToolOutput.toJsonObject(primaryField: String): JsonObject {
        val data = mutableMapOf<String, JsonElement>(
            primaryField to JsonPrimitive(preview),
            "output_truncated" to JsonPrimitive(truncated),
            "output_total_chars" to JsonPrimitive(totalChars)
        )
        outputPath?.let { data["output_path"] = JsonPrimitive(it) }
        storageError?.let { data["output_storage_error"] = JsonPrimitive(it) }
        return JsonObject(data)
    }

    private fun buildPreview(text: String, outputPath: String?): String {
        val omitted = text.length - HEAD_CHARS - TAIL_CHARS
        val storageHint = if (outputPath != null) {
            "完整内容已保存到 $outputPath"
        } else {
            "完整内容保存失败"
        }
        return buildString {
            append(text.take(HEAD_CHARS))
            append("\n\n...[输出过长，已省略中间 ")
            append(omitted)
            append(" 个字符；")
            append(storageHint)
            append("]...\n\n")
            append(text.takeLast(TAIL_CHARS))
        }
    }

    private fun writeFullOutput(toolName: String, callId: String, text: String): StoredPathResult {
        return try {
            val dir = File(containerInstaller.aicodeDir, OUTPUT_DIR).apply { mkdirs() }
            val file = uniqueOutputFile(dir, toolName, callId)
            file.writeText(text, Charsets.UTF_8)
            val path = "$AICODE_ROOT/$OUTPUT_DIR/${file.name}"
            FileLogger.i(TAG, "工具输出已保存: $path (${text.length} chars)")
            StoredPathResult(outputPath = path)
        } catch (e: Exception) {
            FileLogger.w(TAG, "保存工具输出失败: ${e.message}", e)
            StoredPathResult(storageError = e.message ?: "保存工具输出失败")
        }
    }

    private fun uniqueOutputFile(dir: File, toolName: String, callId: String): File {
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
        val baseName = buildString {
            append(timestamp)
            append('-')
            append(sanitize(toolName).ifBlank { "tool" })
            val id = sanitize(callId).take(12)
            if (id.isNotBlank()) {
                append('-')
                append(id)
            }
        }

        var candidate = File(dir, "$baseName.log")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName-$index.log")
            index++
        }
        return candidate
    }

    private fun sanitize(value: String): String {
        return value.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
        }.joinToString("").trim('-')
    }

    private data class StoredPathResult(
        val outputPath: String? = null,
        val storageError: String? = null
    )
}
