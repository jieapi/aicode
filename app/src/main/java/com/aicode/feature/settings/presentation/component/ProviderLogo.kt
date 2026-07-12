package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Brand
import com.aicode.feature.settings.domain.model.AIProviderConfig
import compose.icons.FeatherIcons
import compose.icons.feathericons.Cloud
import compose.icons.feathericons.Cpu

/**
 * 根据 provider 的 baseUrl 与 name 匹配品牌 logo drawable 资源。
 * 顺序从具体到通用，保证子集（如 openrouter / siliconflow）优先命中。
 */
fun providerLogoRes(provider: AIProviderConfig?): Int? {
    if (provider == null) return null
    val target = "${provider.baseUrl} ${provider.name}".lowercase()
    return when {
        target.contains("doubao") || target.contains("豆包") || target.contains("volces") || target.contains("ark.cn-beijing") -> R.drawable.logo_doubao
        target.contains("moonshot") || target.contains("kimi") -> R.drawable.logo_moonshot
        target.contains("zhipu") || target.contains("智谱") || target.contains("bigmodel") || target.contains("glm") -> R.drawable.logo_zhipu
        target.contains("qwen") || target.contains("通义") || target.contains("dashscope") -> R.drawable.logo_qwen
        target.contains("deepseek") -> R.drawable.logo_deepseek
        target.contains("grok") || target.contains("xai") || target.contains("x-ai") -> R.drawable.logo_grok
        target.contains("anthropic") || target.contains("claude") -> R.drawable.logo_anthropic
        target.contains("gemini") || target.contains("generativelanguage") -> R.drawable.logo_gemini
        target.contains("openai") -> R.drawable.logo_openai
        else -> null
    }
}

/**
 * 根据模型名称推断所属品牌分类 key（小写英文标识）。
 * 与 providerLogoRes / modelLogoRes 匹配优先级一致，确保 "zhipu" 不会先命中 "glm" 等。
 * 返回的品牌 key 可用于分组和 logo 查找。
 */
fun modelBrandKey(modelName: String): String {
    val target = modelName.lowercase()
    return when {
        target.contains("doubao") || target.contains("豆包") -> "doubao"
        target.contains("moonshot") || target.contains("kimi") -> "moonshot"
        target.contains("zhipu") || target.contains("智谱") || target.contains("bigmodel") || target.contains("glm") -> "zhipu"
        target.contains("qwen") || target.contains("通义") -> "qwen"
        target.contains("deepseek") || target.contains("deep-seek") -> "deepseek"
        target.contains("grok") || target.contains("xai") -> "grok"
        target.contains("claude") || target.contains("anthropic") -> "anthropic"
        target.contains("gemini") || target.contains("gemma") -> "gemini"
        target.contains("gpt") || target.contains("o1") || target.contains("o3") || target.contains("o4") || target.contains("openai") || target.contains("chatgpt") || target.contains("dall-e") -> "openai"
        else -> {
            // 回退：从模型名称前缀提取（如 "meta-llama/..." → "meta"）
            val parts = modelName.split("/", "-", limit = 2)
            if (parts.size > 1 && parts[0].length >= 2) parts[0].lowercase() else "other"
        }
    }
}

/** 品牌 key → 用户可见的显示名称 */
fun brandDisplayName(key: String): String = when (key) {
    "doubao" -> "豆包"
    "moonshot" -> "Moonshot"
    "zhipu" -> "智谱"
    "qwen" -> "通义千问"
    "deepseek" -> "DeepSeek"
    "grok" -> "Grok"
    "anthropic" -> "Anthropic"
    "gemini" -> "Gemini"
    "openai" -> "OpenAI"
    "other" -> "其他"
    else -> key.replaceFirstChar { it.uppercase() }
}

/** 品牌 key → 对应 logo drawable 资源，无匹配时返回 null */
fun brandLogoRes(key: String): Int? = when (key) {
    "doubao" -> R.drawable.logo_doubao
    "moonshot" -> R.drawable.logo_moonshot
    "zhipu" -> R.drawable.logo_zhipu
    "qwen" -> R.drawable.logo_qwen
    "deepseek" -> R.drawable.logo_deepseek
    "grok" -> R.drawable.logo_grok
    "anthropic" -> R.drawable.logo_anthropic
    "gemini" -> R.drawable.logo_gemini
    "openai" -> R.drawable.logo_openai
    else -> null
}

private fun shouldTintModelLogo(key: String): Boolean = key == "grok" || key == "openai"

@Composable
private fun modelLogoTint(): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDarkTheme) Color.White else Color.Black
}

/**
 * 渲染品牌 logo 图标；若未匹配或 provider 为 null，则显示默认 Cpu 图标。
 */
@Composable
fun ProviderLogoIcon(
    provider: AIProviderConfig?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val res = providerLogoRes(provider)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = provider?.name ?: "AI Provider",
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = FeatherIcons.Cloud,
            contentDescription = "AI 提供商",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size)
        )
    }
}

/**
 * 根据模型名称渲染品牌 logo 图标；若未匹配则显示默认 Cpu 图标。
 */
@Composable
fun ModelLogoIcon(
    modelName: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val key = modelBrandKey(modelName)
    val res = brandLogoRes(key)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = modelName,
            colorFilter = if (shouldTintModelLogo(key)) ColorFilter.tint(modelLogoTint()) else null,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = FeatherIcons.Cpu,
            contentDescription = "模型图标",
            tint = Brand.IconGray,
            modifier = modifier.size(size)
        )
    }
}

/**
 * 根据品牌 key 渲染品牌 logo 图标；无匹配时显示默认 Cpu 图标。
 * 用于分类 header 等已知品牌 key 的场景。
 */
@Composable
fun BrandLogoIcon(
    brandKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val res = brandLogoRes(brandKey)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = brandDisplayName(brandKey),
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = FeatherIcons.Cpu,
            contentDescription = brandDisplayName(brandKey),
            tint = Brand.IconGray,
            modifier = modifier.size(size)
        )
    }
}
