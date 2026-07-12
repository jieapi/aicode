package com.aicode.feature.agent.domain.tool.search

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolResult
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
        // 较新的桌面 Chrome 版本，搭配下方 sec-ch-ua / sec-fetch-* 请求头以贴近真实浏览器指纹
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
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

                // 从顶层捕获非 HTTP 的连接异常（DNS、超时、SSL 等），直接把具体原因回传给 AI，不做兜底猜测
                val doc = try {
                    fetchDocument(url)
                } catch (e: FetchException) {
                    return@withContext ToolResult.Error(e.detailedMessage(url))
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

    private fun fetchDocument(url: String): Document {
        return try {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                // 真桌面 Chrome 一定会发送的 sec-ch-ua 系列客户端提示
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"132\", \"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"132\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                // Fetch Metadata 请求头，现代浏览器发页面请求时必带
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                // 3xx 重定向默认即跟随，显式声明；限制响应体大小避免超大页面吃满内存
                .followRedirects(true)
                .maxBodySize(5 * 1024 * 1024) // 5MB
                .timeout(15000) // 15秒超时
                .ignoreContentType(true)
                .get()
        } catch (e: org.jsoup.HttpStatusException) {
            FileLogger.e(TAG, "HTTP 状态码异常: ${e.statusCode} - ${e.getUrl()}", e)
            // 把真实状态码和原始异常描述回传给 AI，不做任何模糊化处理
            throw FetchException("HTTP ${e.statusCode}: ${e.message ?: "无状态描述"}")
        } catch (e: org.jsoup.UnsupportedMimeTypeException) {
            FileLogger.e(TAG, "不支持的响应类型: ${e.getMimeType()} - ${e.getUrl()}", e)
            throw FetchException("不支持的响应 MIME 类型: ${e.getMimeType()}")
        } catch (e: java.net.SocketTimeoutException) {
            FileLogger.e(TAG, "请求超时: $url", e)
            throw FetchException("请求超时（15 秒内未响应）")
        } catch (e: java.net.UnknownHostException) {
            FileLogger.e(TAG, "DNS 解析失败: $url", e)
            throw FetchException("无法解析主机名：${url}")
        } catch (e: javax.net.ssl.SSLException) {
            FileLogger.e(TAG, "SSL 握手失败: $url", e)
            throw FetchException("SSL/TLS 握手失败：${e.message ?: "未知原因"}")
        } catch (e: java.io.IOException) {
            FileLogger.e(TAG, "网络 I/O 异常: ${e.message} - $url", e)
            throw FetchException("网络 I/O 异常：${e.message ?: "未知原因"}")
        }
    }

    /** 把抓取过程中的失败包装成携带具体信息的异常，以便回传给 AI 而非模糊提示。 */
    private class FetchException(val detail: String) : RuntimeException(detail) {
        fun detailedMessage(url: String): String = "抓取 $url 失败：$detail"
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
