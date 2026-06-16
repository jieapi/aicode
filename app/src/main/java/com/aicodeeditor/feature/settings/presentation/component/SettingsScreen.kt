package com.aicodeeditor.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI 服务商设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingProvider = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务商")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(providers) { provider ->
                    ProviderItem(
                        provider = provider,
                        isActive = activeProvider?.id == provider.id,
                        onActivate = { viewModel.setActiveProvider(provider.id) },
                        onEdit = {
                            editingProvider = provider
                            showEditDialog = true
                        }
                    )
                }
            }
        }

        if (showEditDialog) {
            ProviderEditDialog(
                viewModel = viewModel,
                initialProvider = editingProvider,
                onDismiss = { showEditDialog = false },
                onSave = { provider ->
                    viewModel.saveProvider(provider)
                    showEditDialog = false
                },
                onDelete = { id ->
                    viewModel.deleteProvider(id)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "模型：${provider.effectiveModel} · 共 ${provider.models.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已启用",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.md)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditDialog(
    viewModel: SettingsViewModel,
    initialProvider: AIProviderConfig?,
    onDismiss: () -> Unit,
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

    val fetchState by viewModel.fetchState.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val testing by viewModel.testing.collectAsState()

    // 进入/离开对话框时清理临时状态。
    DisposableEffect(Unit) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
        }
    }

    // 构造当前表单对应的临时配置，供拉取/测试使用。
    fun currentConfig() = AIProviderConfig(
        id = initialProvider?.id ?: "temp",
        name = name,
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultBaseUrl(type) },
        defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "" },
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = selectedModel
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProvider == null) "添加服务商" else "编辑服务商") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
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
                    placeholder = { Text(defaultBaseUrl(type)) },
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
                        onClick = { viewModel.fetchModels(currentConfig()) },
                        enabled = fetchState !is FetchState.Loading
                    ) {
                        if (fetchState is FetchState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Text("拉取")
                    }
                }

                // 拉取结果 / 错误
                when (val fs = fetchState) {
                    is FetchState.Error -> Text(
                        "拉取失败：${fs.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    is FetchState.Success -> {
                        val newOnes = fs.models.filter { it !in models }
                        if (newOnes.isEmpty()) {
                            Text(
                                "已是最新，无新模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "点击添加（${newOnes.size}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowChips(
                                items = newOnes,
                                onClick = { m ->
                                    if (m !in models) models.add(m)
                                    if (selectedModel.isBlank()) selectedModel = m
                                }
                            )
                        }
                    }
                    else -> {}
                }

                // 已添加模型列表
                models.forEach { model ->
                    ModelRow(
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AIProviderConfig(
                            id = initialProvider?.id ?: System.currentTimeMillis().toString(),
                            name = name.ifEmpty { "新服务商" },
                            type = type,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { defaultBaseUrl(type) },
                            defaultModel = selectedModel.ifBlank { models.firstOrNull() ?: "gpt-4o" },
                            isActive = initialProvider?.isActive ?: false,
                            models = models.toList(),
                            selectedModel = selectedModel.ifBlank { models.firstOrNull() ?: "" }
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (initialProvider != null) {
                    TextButton(
                        onClick = { onDelete(initialProvider.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun ModelRow(
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
                // 测试按钮
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
            // 测试结果
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

/** 可点击添加的模型胶囊流式布局（简易换行）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, onClick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onClick(item) },
                label = { Text(item, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    else -> "https://api.openai.com/"
}
