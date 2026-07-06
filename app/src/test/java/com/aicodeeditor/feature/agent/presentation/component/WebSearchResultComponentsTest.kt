package com.aicodeeditor.feature.agent.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WebSearchResultComponentsTest {

    @Test
    fun parseWebSearchResult_readsToolDataObject() {
        val payload = """
            {
              "search_id": "search_123",
              "results": [
                {
                  "url": "https://example.com/news/article",
                  "title": "Example title",
                  "publish_date": "2026-07-06",
                  "excerpts": ["First excerpt", "Second excerpt"]
                }
              ],
              "warnings": null,
              "usage": [{"name": "web_search", "count": 1}],
              "session_id": "session_abc"
            }
        """.trimIndent()
        val transport = """
            {
              "status": "success",
              "data": $payload
            }
        """.trimIndent()

        val parsed = parseWebSearchResult(transport)

        assertNotNull(parsed)
        assertEquals("search_123", parsed!!.searchId)
        assertEquals("session_abc", parsed.sessionId)
        assertEquals(1, parsed.results.size)
        assertEquals("Example title", parsed.results.first().title)
        assertEquals("2026-07-06", parsed.results.first().publishDate)
        assertEquals("web_search", parsed.usage.first().name)
        assertEquals("1", parsed.usage.first().count)
    }

    @Test
    fun parseWebSearchResult_readsToolDataJsonString() {
        val payload = """
            {
              "search_id": "search_456",
              "results": [
                {
                  "url": "https://example.com/docs",
                  "title": "Docs",
                  "publish_date": null,
                  "excerpts": ["Reference excerpt"]
                }
              ],
              "warnings": "unknown",
              "usage": [],
              "session_id": "session_def"
            }
        """.trimIndent()
        val transport = """
            {
              "status": "success",
              "data": ${payload.toJsonStringLiteral()}
            }
        """.trimIndent()

        val parsed = parseWebSearchResult(transport)

        assertNotNull(parsed)
        assertEquals("search_456", parsed!!.searchId)
        assertEquals("Docs", parsed.results.first().title)
        assertEquals("unknown", parsed.warnings)
    }

    private fun String.toJsonStringLiteral(): String {
        return buildString {
            append('"')
            this@toJsonStringLiteral.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
