package com.aicodeeditor.feature.agent.domain.tool.file

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolCapability
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLConnection
import javax.inject.Inject

class ViewImageTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {
    override val name = "viewImage"
    override val description = "查看本地图片文件。读取图片尺寸并把图片作为下一轮视觉输入提供给模型，适合检查截图、设计稿、图标和生成图。"
    override val capabilities = setOf(ToolCapability.READ_WORKSPACE)
    override val parameters = mapOf(
        "path" to ToolParameter(
            name = "path",
            type = ParameterType.STRING,
            description = "图片路径：/workspace/... 为项目文件；其它绝对路径为容器系统文件；相对路径基于 /workspace。",
            required = true
        ),
        "detail" to ToolParameter(
            name = "detail",
            type = ParameterType.STRING,
            description = "图片细节级别。low 会缩小到较小预览；high 适合一般视觉检查；original 尽量传原图，过大时自动降级为 high。",
            required = false,
            enum = listOf("low", "high", "original")
        )
    )

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (path.isBlank()) {
                return ToolResult.Error("路径参数缺失", "MISSING_PATH")
            }

            val detail = args["detail"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in SUPPORTED_DETAILS }
                ?: "high"

            val file = pathMapper.toHostFile(path)
            FileLogger.d(TAG, "viewImage path=$path -> ${file.absolutePath}, detail=$detail")

            if (!file.exists()) return ToolResult.Error("文件不存在: $path", "FILE_NOT_FOUND")
            if (!file.isFile) return ToolResult.Error("路径不是文件: $path", "NOT_A_FILE")
            if (file.length() <= 0L) return ToolResult.Error("图片文件为空: $path", "EMPTY_FILE")

            val bounds = decodeBounds(file)
                ?: return ToolResult.Error("无法识别图片格式: $path", "UNSUPPORTED_IMAGE")
            val sourceMime = guessMimeType(file)
            if (!sourceMime.startsWith("image/")) {
                return ToolResult.Error("不是支持的图片文件: $path", "UNSUPPORTED_IMAGE")
            }

            val encoded = if (detail == "original" && file.length() <= MAX_ORIGINAL_BYTES && sourceMime in ORIGINAL_MIME_TYPES) {
                EncodedImage(
                    mimeType = sourceMime,
                    base64Data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
                    width = bounds.width,
                    height = bounds.height,
                    detail = "original",
                    encodedBytes = file.length()
                )
            } else {
                encodePreview(file, bounds, detail)
            }

            ToolResult.Success(
                JsonObject(
                    mapOf(
                        "content" to JsonPrimitive(
                            "已加载图片 ${pathMapper.toContainerPath(file.absolutePath)} " +
                                "(${bounds.width}x${bounds.height}, ${sourceMime}, ${file.length()} bytes)，" +
                                "并作为视觉输入附加到下一轮模型上下文。"
                        ),
                        "path" to JsonPrimitive(pathMapper.toContainerPath(file.absolutePath)),
                        "mime_type" to JsonPrimitive(sourceMime),
                        "width" to JsonPrimitive(bounds.width),
                        "height" to JsonPrimitive(bounds.height),
                        "byte_size" to JsonPrimitive(file.length()),
                        "detail" to JsonPrimitive(encoded.detail),
                        "encoded_mime_type" to JsonPrimitive(encoded.mimeType),
                        "encoded_width" to JsonPrimitive(encoded.width),
                        "encoded_height" to JsonPrimitive(encoded.height),
                        "encoded_byte_size" to JsonPrimitive(encoded.encodedBytes),
                        "image" to JsonObject(
                            mapOf(
                                "mime_type" to JsonPrimitive(encoded.mimeType),
                                "base64_data" to JsonPrimitive(encoded.base64Data),
                                "path" to JsonPrimitive(pathMapper.toContainerPath(file.absolutePath))
                            )
                        )
                    )
                )
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "viewImage 异常", e)
            ToolResult.Error(e.message ?: "读取图片失败", "READ_IMAGE_ERROR")
        }
    }

    private fun decodeBounds(file: File): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) ImageBounds(width, height) else null
    }

    private fun encodePreview(file: File, bounds: ImageBounds, detail: String): EncodedImage {
        val maxEdge = if (detail == "low") LOW_MAX_EDGE else HIGH_MAX_EDGE
        val targetBytes = if (detail == "low") LOW_TARGET_BYTES else HIGH_TARGET_BYTES
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.width, bounds.height, maxEdge)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalArgumentException("无法解码图片: ${file.name}")

        try {
            val scaled = scaleToMaxEdge(decoded, maxEdge)
            try {
                val encoded = compressJpeg(scaled, targetBytes)
                return EncodedImage(
                    mimeType = "image/jpeg",
                    base64Data = Base64.encodeToString(encoded, Base64.NO_WRAP),
                    width = scaled.width,
                    height = scaled.height,
                    detail = detail,
                    encodedBytes = encoded.size.toLong()
                )
            } finally {
                if (scaled !== decoded) scaled.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sample >= maxEdge && halfHeight / sample >= maxEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compressJpeg(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var best = ByteArray(0)
        for (quality in JPEG_QUALITIES) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            best = bytes
            if (bytes.size <= targetBytes) break
        }
        return best
    }

    private fun guessMimeType(file: File): String {
        val extMime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> null
        }
        return extMime ?: URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
    }

    private data class ImageBounds(val width: Int, val height: Int)

    private data class EncodedImage(
        val mimeType: String,
        val base64Data: String,
        val width: Int,
        val height: Int,
        val detail: String,
        val encodedBytes: Long
    )

    private companion object {
        const val TAG = "ImageTools"
        const val LOW_MAX_EDGE = 512
        const val HIGH_MAX_EDGE = 1024
        const val LOW_TARGET_BYTES = 96 * 1024
        const val HIGH_TARGET_BYTES = 512 * 1024
        const val MAX_ORIGINAL_BYTES = 4 * 1024 * 1024
        val JPEG_QUALITIES = listOf(86, 78, 70, 62, 54)
        val SUPPORTED_DETAILS = setOf("low", "high", "original")
        val ORIGINAL_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
