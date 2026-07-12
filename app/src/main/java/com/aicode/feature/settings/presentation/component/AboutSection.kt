package com.aicode.feature.settings.presentation.component

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Github
import compose.icons.feathericons.Tag

/**
 * 关于页：顶部应用信息、版本号（点击检查更新）、GitHub 仓库、开源许可证。
 *
 * 纯展示型页面，无持久化。布局与样式与 SettingsScreen 的 MenuRow 保持一致
 * (Card + RoundedCornerShape(Radius.md) + outlineVariant 边框 + onSurfaceVariant 图标)。
 */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 通过 PackageManager 读取 versionName/versionCode（项目未开启 BuildConfig）
    val appInfo = remember {
        runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppVersion(name = info.versionName ?: "unknown", code = code)
        }.getOrDefault(AppVersion("unknown", 0L))
    }

    // 应用图标：用 PackageManager.loadIcon 加载自适应图标，避免 mipmap-anydpi 的 XML
    // 在 Compose painterResource 中不被支持的问题（Only VectorDrawables and rasterized types）。
    val appIcon = remember { loadAppIconBitmap(context) }

    // 检查更新弹窗状态
    var updateDialog by remember { mutableStateOf<UpdateDialogState?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 顶部应用信息卡：左图标 + 右上应用名 + 右下简介（无版本号）
        AboutHeaderCard(appName = stringResource(R.string.app_name), appIcon = appIcon)

        // 版本号卡：可点击自动检查更新
        LinkRow(
            leading = FeatherIcons.Tag,
            title = "版本",
            value = "v${appInfo.name}",
            onClick = {
                if (updateDialog == null) {
                    updateDialog = UpdateDialogState.Checking
                    scope.launch {
                        updateDialog = checkUpdate(appInfo.name)
                    }
                }
            }
        )

        // GitHub 仓库
        LinkRow(
            leading = FeatherIcons.Github,
            title = "GitHub 仓库",
            value = null,
            onClick = { openUrl(context, GITHUB_REPO_URL) }
        )

        // 许可证
        LinkRow(
            leading = FeatherIcons.Book,
            title = "开源许可证",
            value = null,
            onClick = { openUrl(context, LICENSE_URL) }
        )
    }

    // 检查更新结果弹窗
    updateDialog?.let { state ->
        UpdateResultDialog(
            state = state,
            currentVersion = appInfo.name,
            onDismiss = { updateDialog = null },
            onOpenRelease = {
                openUrl(context, RELEASES_URL)
                updateDialog = null
            }
        )
    }
}

/** 顶部信息卡：左 app 图标，右上 app 名，右下一句简介。 */
@Composable
private fun AboutHeaderCard(appName: String, appIcon: androidx.compose.ui.graphics.ImageBitmap?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                // 极端情况下图标加载失败的占位，保持布局占位
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "在 Android 设备上运行的 AI 驱动代码编辑器 · 内置终端 · AI Agent · MCP 协议",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单行卡片：左侧可选图标 + 标题，右侧可选文案 + 右箭头。仿 SettingsScreen 的 MenuRow。
 *
 * @param leading 左侧图标，null 则留出占位空格使标题与下方卡片左对齐于标题起点。
 * @param value 右侧文案，null 则不显示，仅显示右箭头。
 */
@Composable
private fun LinkRow(
    leading: ImageVector?,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Spacing.md))
            } else {
                // 与带图标的行保持左对齐：24dp 图标宽 + md 间距
                Spacer(Modifier.width(24.dp + Spacing.md))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 检查更新结果弹窗。 */
@Composable
private fun UpdateResultDialog(
    state: UpdateDialogState,
    currentVersion: String,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit
) {
    when (state) {
        UpdateDialogState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = { Text("正在向 GitHub 查询最新版本…") },
            confirmButton = {}
        )
        is UpdateDialogState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已经是最新版本") },
            text = { Text("当前版本 v$currentVersion，已是 GitHub 上最新的 Release。") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )
        is UpdateDialogState.NewVersion -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本") },
            text = { Text("当前 v$currentVersion，最新 v${state.latestTag}。点击「前往下载」打开 GitHub Releases 页面获取最新 APK。") },
            confirmButton = { TextButton(onClick = onOpenRelease) { Text("前往下载") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
        )
    }
}

/** 用隐式 Intent 打开浏览器，捕获异常避免崩溃。 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * 拉取 GitHub /repos/{owner}/{repo}/releases/latest，比对最新 tag 与当前 versionName。
 */
private suspend fun checkUpdate(currentVersion: String): UpdateDialogState = withContext(Dispatchers.IO) {
    runCatching {
        val req = okhttp3.Request.Builder()
            .url(GITHUB_LATEST_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        SHARED_CLIENT.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return@use UpdateDialogState.Error("HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            val tag = JsonParser.parseString(body).asJsonObject?.get("tag_name")?.asString
                ?: return@use UpdateDialogState.Error("无法解析最新版本号")
            val latest = parseVersionTag(tag) ?: tag
            if (sameVersion(latest, currentVersion)) {
                UpdateDialogState.UpToDate
            } else {
                UpdateDialogState.NewVersion(latestTag = latest)
            }
        }
    }.getOrElse { UpdateDialogState.Error(it.message ?: "网络错误") }
}

/** GitHub tag 形如 v1.0.0 / 1.0.0 / v1.0.0-rc1，提取出纯版本号。 */
private fun parseVersionTag(tag: String): String? {
    val raw = tag.trim().removePrefix("v")
    // 取第一个非空白段，去掉预发布后缀（如 -rc1）
    val seg = raw.substringBefore(' ').substringBefore('-')
    return seg.ifBlank { null }
}

/** 简单比对两段纯语义版本（x.y.z）。相等即视为同一版本。 */
private fun sameVersion(a: String, b: String): Boolean = a == b

private data class AppVersion(val name: String, val code: Long)

private sealed interface UpdateDialogState {
    data object Checking : UpdateDialogState
    data object UpToDate : UpdateDialogState
    data class NewVersion(val latestTag: String) : UpdateDialogState
    data class Error(val message: String) : UpdateDialogState
}

private val SHARED_CLIENT by lazy { OkHttpClient.Builder().build() }

/**
 * 通过 PackageManager.loadIcon 加载应用图标并转为 ImageBitmap，兼容自适应图标
 * (adaptive icon XML)。解决 painterResource(R.mipmap.ic_launcher) 在 v26+ 设备上
 * 因解析到 mipmap-anydpi-v26/ic_launcher.xml 而抛 IllegalArgumentException 的问题。
 * 失败返回 null（调用方预留占位）。
 */
private fun loadAppIconBitmap(context: android.content.Context): ImageBitmap? {
    return runCatching {
        val pm = context.packageManager
        val drawable: Drawable = pm.getApplicationInfo(context.packageName, 0).loadIcon(pm)
        val sizePx = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            ICON_PX_DP.toFloat(),
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        Canvas(bitmap).also { drawable.draw(it) }
        bitmap.asImageBitmap()
    }.getOrNull()
}

private const val GITHUB_REPO_URL = "https://github.com/jieapi/aicode"
private const val LICENSE_URL = "https://github.com/jieapi/aicode/blob/main/LICENSE"
private const val RELEASES_URL = "https://github.com/jieapi/aicode/releases/latest"
private const val GITHUB_LATEST_API = "https://api.github.com/repos/jieapi/aicode/releases/latest"
private const val ICON_PX_DP = 48
