package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Search
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

internal data class ParsedWebSearchResult(
    val searchId: String,
    val results: List<ParsedWebSearchItem>,
    val warnings: String?,
    val usage: List<ParsedWebSearchUsage>,
    val sessionId: String
)

internal data class ParsedWebSearchItem(
    val url: String,
    val title: String,
    val publishDate: String?,
    val excerpts: List<String>
)

internal data class ParsedWebSearchUsage(
    val name: String,
    val count: String
)

internal fun parseWebSearchResult(content: String): ParsedWebSearchResult? {
    return runCatching {
        val data = extractWebSearchData(content) ?: return null
        val results = data["results"]?.jsonArray?.mapNotNull { parseWebSearchItem(it) }.orEmpty()
        if (results.isEmpty()) return null
        ParsedWebSearchResult(
            searchId = data["search_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            results = results,
            warnings = data["warnings"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" },
            usage = data["usage"]?.jsonArray?.mapNotNull { parseWebSearchUsage(it) }.orEmpty(),
            sessionId = data["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }.getOrNull()
}

private fun extractWebSearchData(content: String): JsonObject? {
    val s = content.withoutToolStatusPrefix()
    val outer = Json.parseToJsonElement(s).jsonObject
    if (outer["status"]?.jsonPrimitive?.contentOrNull !in setOf("success", "partial")) return null
    return when (val data = outer["data"]) {
        is JsonObject -> data
        is JsonPrimitive -> data.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let { Json.parseToJsonElement(it).jsonObject }
        else -> null
    }
}

private fun parseWebSearchItem(element: JsonElement): ParsedWebSearchItem? {
    val obj = element as? JsonObject ?: return null
    val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (url.isBlank() && title.isBlank()) return null
    val excerpts = obj["excerpts"]?.jsonArray?.mapNotNull {
        it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }.orEmpty()
    return ParsedWebSearchItem(
        url = url,
        title = title.ifBlank { url },
        publishDate = obj["publish_date"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
        excerpts = excerpts
    )
}

private fun parseWebSearchUsage(element: JsonElement): ParsedWebSearchUsage? {
    val obj = element as? JsonObject ?: return null
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val count = obj["count"]?.jsonPrimitive?.contentOrNull ?: obj["count"]?.toString().orEmpty()
    if (name.isBlank() && count.isBlank()) return null
    return ParsedWebSearchUsage(name = name.ifBlank { "usage" }, count = count)
}

@Composable
internal fun WebSearchResultCard(result: ParsedWebSearchResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        WebSearchSummary(result)
        result.results.forEachIndexed { index, item ->
            WebSearchResultItem(index = index + 1, item = item)
        }
    }
}

@Composable
private fun WebSearchSummary(result: ParsedWebSearchResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                FeatherIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "网络搜索",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "找到 ${result.results.size} 条结果",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WebSearchResultItem(index: Int, item: ParsedWebSearchItem) {
    var expanded by remember(item.url, item.title) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            FeatherIcons.ExternalLink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.url.toDisplayUrl(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (expanded) {
                if (!item.publishDate.isNullOrBlank()) {
                    WebSearchChip(text = item.publishDate)
                }

                if (item.excerpts.isNotEmpty()) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item.excerpts.take(3).forEach { excerpt ->
                                Text(
                                    text = excerpt,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSearchChip(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
        )
    }
}

private fun String.toDisplayUrl(): String {
    if (isBlank()) return ""
    return runCatching {
        val uri = URI(this)
        val host = uri.host?.removePrefix("www.").orEmpty()
        val path = uri.rawPath.orEmpty().takeIf { it != "/" }.orEmpty()
        "$host$path".ifBlank { this }
    }.getOrElse { this }
}
