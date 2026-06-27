package com.aicodeeditor.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.settings.data.remote.ModelTestResult
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.presentation.FetchState
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderEditorScreen(
    viewModel: SettingsViewModel,
    initialProvider: AIProviderConfig?,
    onNavigateBack: () -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    var selectedModel by remember { mutableStateOf(initialProvider?.effectiveModel ?: "") }
    var newModel by remember { mutableStateOf("") }
    var showFetchDialog by remember { mutableStateOf(false) }

    val fetchState by viewModel.fetchState.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val testing by viewModel.testing.collectAsState()

    DisposableEffect(Unit) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
        }
    }

    fun currentConfig() = AIProviderConfig(
        id = initialProvider?.id ?: "temp",
        name = name,
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
        defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "" },
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = selectedModel
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initialProvider == null) "添加服务商" else "编辑服务商") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (initialProvider != null) {
                        TextButton(
                            onClick = { onDelete(initialProvider.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("删除")
                        }
                    }
                    TextButton(onClick = {
                        onSave(
                            AIProviderConfig(
                                id = initialProvider?.id ?: System.currentTimeMillis().toString(),
                                name = name.ifEmpty { "新服务商" },
                                type = type,
                                apiKey = apiKey,
                                baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
                                defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "" },
                                isActive = initialProvider?.isActive ?: false,
                                models = models.toList(),
                                selectedModel = selectedModel.ifBlank { models.firstOrNull() ?: "" }
                            )
                        )
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(
                    selected = type == ProviderType.OPENAI,
                    onClick = { type = ProviderType.OPENAI },
                    label = { Text("OpenAI") }
                )
                FilterChip(
                    selected = type == ProviderType.ANTHROPIC,
                    onClick = { type = ProviderType.ANTHROPIC },
                    label = { Text("Anthropic") }
                )
                FilterChip(
                    selected = type == ProviderType.CUSTOM,
                    onClick = { type = ProviderType.CUSTOM },
                    label = { Text("自定义") }
                )
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(defaultProviderBaseUrl(type)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            // ── 模型管理区 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型（${models.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        viewModel.fetchModels(currentConfig())
                        showFetchDialog = true
                    }
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("拉取模型")
                }
            }

            // 已添加模型列表
            models.forEach { model ->
                ProviderModelRow(
                    model = model,
                    isSelected = model == selectedModel,
                    testing = model in testing,
                    result = testResults[model],
                    onSelect = { selectedModel = model },
                    onTest = { viewModel.testModel(currentConfig(), model) },
                    onRemove = {
                        models.remove(model)
                        if (selectedModel == model) selectedModel = models.firstOrNull() ?: ""
                    }
                )
            }

            // 手动添加
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = newModel,
                    onValueChange = { newModel = it },
                    label = { Text("手动添加模型") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val m = newModel.trim()
                        if (m.isNotEmpty() && m !in models) {
                            models.add(m)
                            if (selectedModel.isBlank()) selectedModel = m
                        }
                        newModel = ""
                    },
                    enabled = newModel.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
        }
    }

    // 模型拉取结果弹窗
    if (showFetchDialog) {
        FetchModelsDialog(
            fetchState = fetchState,
            existingModels = models,
            onAddModel = { m ->
                if (m !in models) models.add(m)
                if (selectedModel.isBlank()) selectedModel = m
            },
            onDismiss = {
                showFetchDialog = false
                viewModel.resetFetchState()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FetchModelsDialog(
    fetchState: FetchState,
    existingModels: List<String>,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拉取模型") },
        text = {
            when (fetchState) {
                is FetchState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在拉取…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is FetchState.Error -> {
                    Text(
                        "拉取失败：${fetchState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is FetchState.Success -> {
                    val newOnes = fetchState.models.filter { it !in existingModels }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (newOnes.isEmpty()) {
                            Text("已是最新，无新模型", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "点击模型名称即可添加（${newOnes.size} 个新模型）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                newOnes.forEach { m ->
                                    AssistChip(
                                        onClick = { onAddModel(m) },
                                        label = { Text(m, style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text("请稍候…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
internal fun ProviderModelRow(
    model: String,
    isSelected: Boolean,
    testing: Boolean,
    result: ModelTestResult?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "当前选中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    model,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                        Text("测试", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            result?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (r.success) Icons.Default.Check else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun defaultProviderBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    else -> "https://api.openai.com/"
}
