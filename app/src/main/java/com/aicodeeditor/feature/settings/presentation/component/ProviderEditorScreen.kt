package com.aicodeeditor.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.settings.data.remote.ModelTestResult
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.model.ModelMetadata
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.presentation.FetchState
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

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
    var apiPath by remember { mutableStateOf(initialProvider?.apiPath ?: "/chat/completions") }
    var useResponseApi by remember { mutableStateOf(initialProvider?.useResponseApi ?: false) }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    val providerId = remember { initialProvider?.id ?: System.currentTimeMillis().toString() }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    var showAddModelSheet by remember { mutableStateOf(false) }
    var showFetchDialog by remember { mutableStateOf(false) }
    var fetchDialogKey by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val modelSnapshot = models.toList()

    DisposableEffect(Unit) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
        }
    }

    LaunchedEffect(type, modelSnapshot) {
        viewModel.resolveModelMetadata(type, modelSnapshot)
    }

    fun currentConfig() = AIProviderConfig(
        id = providerId,
        name = name.ifEmpty { "新提供商" },
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
        apiPath = apiPath.ifBlank { "/chat/completions" },
        defaultModel = initialProvider?.defaultModel ?: "",
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = initialProvider?.selectedModel ?: "",
        useResponseApi = useResponseApi
    )

    fun saveCurrent() {
        onSave(currentConfig())
    }

    fun saveAndNavigateBack() {
        saveCurrent()
        onNavigateBack()
    }

    BackHandler {
        saveAndNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initialProvider == null) "添加提供商" else "编辑提供商") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { saveAndNavigateBack() }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    if (initialProvider != null) {
                        IconButton(onClick = { onDelete(initialProvider.id) }) {
                            Icon(
                                FeatherIcons.Trash2,
                                contentDescription = "删除提供商",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = {
                        selectedTab = 1
                        showAddModelSheet = true
                    }) {
                        Icon(FeatherIcons.Plus, contentDescription = "添加模型")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(FeatherIcons.Sliders, contentDescription = "配置") },
                    label = { Text("配置") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(FeatherIcons.Cpu, contentDescription = "模型") },
                    label = { Text("模型") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                            selected = type == ProviderType.GEMINI,
                            onClick = { type = ProviderType.GEMINI },
                            label = { Text("Gemini") }
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

                    OutlinedTextField(
                        value = apiPath,
                        onValueChange = { apiPath = it },
                        label = { Text("API 地址 (如 /chat/completions)") },
                        placeholder = { Text("/chat/completions") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (type == ProviderType.OPENAI) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Response API (新版)")
                            Switch(
                                checked = useResponseApi,
                                onCheckedChange = { useResponseApi = it }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
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
                                fetchDialogKey++
                                showFetchDialog = true
                            }
                        ) {
                            Icon(FeatherIcons.DownloadCloud, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("拉取模型")
                        }
                    }

                    // 已添加模型列表
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        models.forEach { model ->
                            ProviderModelRow(
                                model = model,
                                metadata = modelMetadata[model],
                                testing = model in testing,
                                result = testResults[model],
                                onTest = { viewModel.testModel(currentConfig(), model) },
                                onRemove = {
                                    models.remove(model)
                                    saveCurrent()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddModelSheet) {
        AddModelSheet(
            existingModels = models,
            onAddModel = { model ->
                if (model !in models) {
                    models.add(model)
                    saveCurrent()
                }
            },
            onDismiss = { showAddModelSheet = false }
        )
    }

    // 模型拉取结果弹窗
    if (showFetchDialog) {
        key(fetchDialogKey) {
            FetchModelsDialog(
                fetchState = fetchState,
                modelMetadata = modelMetadata,
                existingModels = models,
                onFetchModels = { viewModel.fetchModels(currentConfig()) },
                onAddModel = { m ->
                    if (m !in models) {
                        models.add(m)
                        saveCurrent()
                    }
                },
                onDismiss = {
                    showFetchDialog = false
                    viewModel.resetFetchState()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelSheet(
    existingModels: List<String>,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modelName by remember { mutableStateOf("") }
    val trimmedModel = modelName.trim()
    val duplicate = existingModels.any { it == trimmedModel }
    val canAdd = trimmedModel.isNotEmpty() && !duplicate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "添加模型",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("模型名称") },
                placeholder = { Text("例如 gpt-4o") },
                singleLine = true,
                isError = duplicate,
                modifier = Modifier.fillMaxWidth()
            )
            if (duplicate) {
                Text(
                    text = "该模型已添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        onAddModel(trimmedModel)
                        onDismiss()
                    }
                ) {
                    Icon(FeatherIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("添加")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FetchModelsDialog(
    fetchState: FetchState,
    modelMetadata: Map<String, ModelMetadata>,
    existingModels: List<String>,
    onFetchModels: () -> Unit,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val collapsedBrands = remember { mutableStateMapOf<String, Boolean>() }
    
    LaunchedEffect(Unit) {
        // Wait for bottom sheet animation to smooth out before firing network request
        delay(300)
        onFetchModels()
    }
    
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.85f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg)
                        .padding(bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("输入模型名称筛选") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    )

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (fetchState) {
                            is FetchState.Loading -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(Spacing.md))
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
                                val newOnes = fetchState.models.filter { it !in existingModels && it.contains(searchQuery, ignoreCase = true) }
                                if (newOnes.isEmpty()) {
                                    Text("没有匹配的模型", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    // 按品牌分组，分类 header 可折叠
                                    val grouped = newOnes.groupBy { m -> modelBrandKey(m) }
                                        .toSortedMap(compareBy { brandDisplayName(it) })

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        grouped.forEach { (brandKey, models) ->
                                            item(key = "header_$brandKey") {
                                                val expanded = collapsedBrands[brandKey] != true
                                                val brandName = brandDisplayName(brandKey)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 44.dp)
                                                        .clickable { collapsedBrands[brandKey] = expanded }
                                                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "$brandName (${models.size})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                                                        contentDescription = if (expanded) "折叠$brandName" else "展开$brandName",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            if (collapsedBrands[brandKey] != true) {
                                                items(models, key = { "${brandKey}_$it" }) { m ->
                                                    FetchModelRow(
                                                        model = m,
                                                        metadata = modelMetadata[m],
                                                        onAdd = { onAddModel(m) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("请稍候…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FetchModelRow(
    model: String,
    metadata: ModelMetadata?,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelLogoIcon(modelName = model, size = 20.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(model, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            ModelMetadataTags(metadata)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(FeatherIcons.Plus, contentDescription = "添加", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ModelMetadataTags(metadata: ModelMetadata?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModelTag(text = "Chat")
        metadata?.let {
            if (it.supportsVision) {
                ModelTag(text = "Image")
            }
            if (it.supportsTools) {
                ModelTag(text = "Tools")
            }
            val input = it.inputTokens?.takeIf { tokens -> tokens > 0 }
                ?: it.contextTokens.takeIf { tokens -> tokens > 0 }
            if (input != null) {
                ModelTag(text = "Input ${formatTokenLimit(input)}")
            }
            it.outputTokens?.takeIf { tokens -> tokens > 0 }?.let { output ->
                ModelTag(text = "Output ${formatTokenLimit(output)}")
            }
        }
    }
}

@Composable
private fun ModelTag(text: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (icon != null && text != null) Spacer(Modifier.width(4.dp))
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ProviderModelRow(
    model: String,
    metadata: ModelMetadata?,
    testing: Boolean,
    result: ModelTestResult?,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModelLogoIcon(modelName = model, size = 24.dp)
            Spacer(Modifier.width(Spacing.md))

            // Center Content (Name & Tags)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                ModelMetadataTags(metadata)
            }

            Spacer(Modifier.width(Spacing.sm))

            // Right Actions
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                    Text("测试", style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    FeatherIcons.X,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Test Result
        result?.let { r ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.sm, start = 32.dp)
            ) {
                Icon(
                    if (r.success) FeatherIcons.Check else FeatherIcons.AlertCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun formatTokenLimit(tokens: Int): String =
    when {
        tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
        tokens >= 1_000_000 -> "${tokens / 1_000_000.0}M".trimDecimal()
        tokens >= 1_000 && tokens % 1_000 == 0 -> "${tokens / 1_000}K"
        tokens >= 1_000 -> "${tokens / 1_000.0}K".trimDecimal()
        else -> tokens.toString()
    }

private fun String.trimDecimal(): String =
    replace(Regex("(\\.\\d)\\d+"), "$1").removeSuffix(".0")

internal fun defaultProviderBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/"
    else -> "https://api.openai.com/"
}
