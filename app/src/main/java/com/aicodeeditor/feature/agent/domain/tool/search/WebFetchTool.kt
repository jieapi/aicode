package com.aicodeeditor.feature.agent.domain.tool.search

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolCapability
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

class WebFetchTool @Inject constructor() : AgentTool() {

    private companion object {
        const val TAG = "WebFetchTool"
        const val USER_AGENT_BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val USER_AGENT_FALLBACK = "aicode/1.0"
        const val MAX_LENGTH = 100_000 // 限制最大提取字符数，防止撑爆上下文
    }

    override val name = "webfetch"
    override val description = "抓取指定 HTTP/HTTPS 网页内容。支持提取网页正文为纯文本或返回原始 HTML 结构。"
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "url" to ToolParameter(
            name = "url",
            type = ParameterType.STRING,
            description = "需要抓取的网页完整 URL (必须以 http:// 或 https:// 开头)",
            required = true
        ),
        "format" to ToolParameter(
            name = "format",
            type = ParameterType.STRING,
            description = "返回格式: 'text' (默认，去除了广告、脚本和样式，仅保留正文) 或 'html' (原始 HTML 源码)",
            enum = listOf("text", "html"),
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少 url 参数")
        val format = args["format"]?.jsonPrimitive?.contentOrNull ?: "text"

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.Error("URL 必须以 http:// 或 https:// 开头")
        }

        return withContext(Dispatchers.IO) {
            try {
                FileLogger.i(TAG, "正在抓取网页: $url, format=$format")
                
                // 第一次尝试用 Chrome UA
                var doc = fetchDocument(url, USER_AGENT_BROWSER)
                
                // 简单的 Cloudflare / 防爬 403 兜底重试机制
                if (doc == null) {
                    FileLogger.i(TAG, "网页抓取疑似被拦截，尝试使用备用身份...")
                    doc = fetchDocument(url, USER_AGENT_FALLBACK)
                }

                if (doc == null) {
                    return@withContext ToolResult.Error("抓取失败：服务器拒绝连接或返回错误 (可能是高强度反爬拦截)。")
                }

                // 按要求提取
                val resultText = when (format) {
                    "html" -> doc.outerHtml()
                    else -> extractCleanText(doc)
                }
                
                val finalOutput = if (resultText.length > MAX_LENGTH) {
                    resultText.take(MAX_LENGTH) + "\n\n[网页内容超长，已截断...]"
                } else {
                    resultText
                }

                ToolResult.Success(kotlinx.serialization.json.JsonPrimitive(finalOutput))
            } catch (e: Exception) {
                FileLogger.e(TAG, "抓取网页时发生异常", e)
                ToolResult.Error("抓取失败: ${e.message}")
            }
        }
    }

    private fun fetchDocument(url: String, userAgent: String): Document? {
        return try {
            Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(15000) // 15秒超时
                .followRedirects(true)
                .ignoreContentType(true)
                .get()
        } catch (e: org.jsoup.HttpStatusException) {
            FileLogger.e(TAG, "HTTP 状态码异常: ${e.statusCode} - $url")
            null
        } catch (e: Exception) {
            FileLogger.e(TAG, "网络连接异常: ${e.message} - $url")
            null
        }
    }

    /**
     * 提取干净正文，并在段落之间保留换行符。
     */
    private fun extractCleanText(doc: Document): String {
        // 移除多余的不可见内容
        doc.select("script, style, iframe, nav, footer, header, noscript, .ad, .advertisement").remove()
        
        // 为了避免 Jsoup 的 .text() 把所有行挤在一起，给块级元素加上换行符
        doc.select("p, h1, h2, h3, h4, h5, h6, li, div, br").append("\\n")
        
        val rawText = doc.body()?.text() ?: ""
        
        // 还原换行符，并清理多余的空行
        return rawText.replace("\\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
