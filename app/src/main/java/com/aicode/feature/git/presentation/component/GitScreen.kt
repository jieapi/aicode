package com.aicode.feature.git.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.presentation.CredentialViewModel
import com.aicode.feature.credentials.presentation.component.CredentialEditorScreen
import com.aicode.feature.credentials.presentation.component.CredentialListSection
import com.aicode.feature.credentials.presentation.component.GitUserIdentityCard
import com.aicode.feature.git.domain.model.GitBranch
import com.aicode.feature.git.domain.model.GitCommit
import com.aicode.feature.git.domain.model.GitFileChange
import com.aicode.feature.git.domain.model.GitGraph
import com.aicode.feature.git.domain.model.GitGraphRef
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import com.aicode.feature.git.domain.model.GitTag
import com.aicode.feature.git.domain.model.GraphCommit
import com.aicode.feature.git.domain.model.GraphEdge
import com.aicode.feature.git.presentation.GitViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    credentialViewModel: CredentialViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val credState by credentialViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // toast → Snackbar 一次性消费。
    LaunchedEffect(state.toast, credState.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
        credState.toast?.let {
            snackbarHostState.showSnackbar(it)
            credentialViewModel.consumeToast()
        }
    }

    var showCommitDialog by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    // 凭据列表态拦截系统返回键：退回 Git 主视图而非退出整个 Git 页。
    BackHandler(enabled = showCredentials) { showCredentials = false }
    // editingCredential != null -> 编辑现有；editingCredential == null && isAddingCredential -> 新增；否则列表态。
    // 编辑/新增态直接在 [Scaffold] 之外独立渲染全屏 [CredentialEditorScreen]（它自带 Scaffold/TopAppBar/BackHandler），
    // 避免与本页 Scaffold 嵌套产生双层顶栏，返回由其自身 BackHandler 接管。
    var editingCredential by remember { mutableStateOf<GitCredential?>(null) }
    var isAddingCredential by remember { mutableStateOf(false) }

    // 编辑/新增凭据：独立全屏页，不进入下方 GitScreen 的 Scaffold，避免双层顶栏。
    if (editingCredential != null) {
        val editing = editingCredential!!
        CredentialEditorScreen(
            initial = editing,
            onBack = { editingCredential = null },
            onSave = { credentialViewModel.saveCredential(it); editingCredential = null },
            onDelete = { credentialViewModel.deleteCredential(it); editingCredential = null }
        )
        return
    }
    if (isAddingCredential) {
        CredentialEditorScreen(
            initial = null,
            onBack = { isAddingCredential = false },
            onSave = { credentialViewModel.saveCredential(it); isAddingCredential = false },
            onDelete = { /* 新增态无删除 */ }
        )
        return
    }

    // diff 视图：独立全屏页，不进入下方 GitScreen 的 Scaffold，避免双层顶栏。
    val diffData = state.diffData
    if (diffData != null) {
        DiffViewerScreen(
            diffData = diffData,
            onBack = { viewModel.clearDiff() }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (showCredentials) "凭据与署名" else "Git") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        // 凭据列表态回 Git 页，否则退出 Git 页。编辑/新增态由 [CredentialEditorScreen] 自身 BackHandler 处理，不走此顶栏。
                        if (showCredentials) showCredentials = false else onNavigateBack()
                    }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!showCredentials) {
                        IconButton(onClick = { showCredentials = true }) {
                            Icon(FeatherIcons.Key, contentDescription = "凭据与署名")
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                            Icon(FeatherIcons.RefreshCw, contentDescription = "刷新")
                        }
                    } else {
                        // showCredentials 列表态：显示添加凭据。编辑/新增态已 return，渲染顶栏时不会落到此分支。
                        IconButton(onClick = { isAddingCredential = true }) {
                            Icon(FeatherIcons.Plus, contentDescription = "添加凭据")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showCredentials) {
                // 每次进入凭据页重新读署名：用户可能在终端改过项目级/全局署名，避免回显陈旧空值。
                LaunchedEffect(Unit) { credentialViewModel.refreshIdentity() }
                CredentialListSection(
                    credentials = credState.credentials,
                    userName = credState.userName,
                    userEmail = credState.userEmail,
                    globalUserName = credState.globalUserName,
                    repoUrl = credState.repoUrl,
                    onEdit = { editingCredential = it },
                    onToggleDefault = { id, isDefault -> credentialViewModel.setDefault(id, isDefault) },
                    onSaveIdentity = { name, email, repoUrl -> credentialViewModel.saveUserIdentity(name, email, repoUrl) }
                )
                return@Column
            }
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                GitTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    GitTab.STATUS -> "状态"
                                    GitTab.BRANCHES -> "分支"
                                    GitTab.LOG -> "提交"
                                }
                            )
                        }
                    )
                }
            }

            when {
                state.diffLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.sm))
                        Text("正在计算差异...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.notARepo -> NotARepoState(onInit = viewModel::initRepo)
                else -> when (state.tab) {
                    GitTab.STATUS -> StatusTab(
                        status = state.status,
                        busy = state.busy,
                        hasRemote = state.hasRemote,
                        hasIdentity = state.hasIdentity,
                        onStage = viewModel::stage,
                        onUnstage = viewModel::unstage,
                        onStageAll = viewModel::stageAll,
                        onCommit = { showCommitDialog = true },
                        onPull = viewModel::pull,
                        onPush = viewModel::push,
                        onFileDiff = viewModel::loadWorktreeDiff
                    )
                    GitTab.BRANCHES -> BranchesTab(
                        branches = state.branches,
                        tags = state.tags,
                        checkoutLoading = state.checkoutLoading,
                        onCheckout = viewModel::checkoutBranch,
                        onCreateBranch = viewModel::createBranch,
                        onDeleteBranch = viewModel::deleteBranch,
                        onDeleteRemoteBranch = viewModel::deleteRemoteBranch,
                        onRenameBranch = viewModel::renameBranch,
                        onCreateTag = viewModel::createTag,
                        onDeleteTag = viewModel::deleteTag
                    )
                    GitTab.LOG -> LogTab(
                        graph = state.graph,
                        expandedCommits = state.expandedCommits,
                        commitFiles = state.commitFiles,
                        loadingCommit = state.loadingCommit,
                        onToggleCommit = viewModel::toggleCommit,
                        onFileDiff = viewModel::loadCommitFileDiff
                    )
                }
            }
        }
    }

    if (showCommitDialog) {
        CommitDialog(
            onDismiss = { showCommitDialog = false },
            onConfirm = { msg ->
                showCommitDialog = false
                viewModel.commit(msg)
            }
        )
    }
}

@Composable
private fun StatusTab(
    status: GitStatus?,
    busy: Boolean,
    hasRemote: Boolean,
    hasIdentity: Boolean,
    onStage: (String) -> Unit,
    onUnstage: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onFileDiff: (String) -> Unit
) {
    val s = status
    val clean = s == null || (s.staged.isEmpty() && s.unstaged.isEmpty() && s.untracked.isEmpty())

    Column(Modifier.fillMaxSize()) {
        StatusOverview(status = s, clean = clean)
        StatusActionsBar(
            busy = busy,
            hasStagedChanges = s?.staged?.isNotEmpty() == true,
            hasRemote = hasRemote,
            hasIdentity = hasIdentity,
            onStageAll = onStageAll,
            onCommit = onCommit,
            onPull = onPull,
            onPush = onPush
        )

        HorizontalDivider()

        if (clean) {
            EmptyState("工作区干净，无改动")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xl)
            ) {
                if (s!!.staged.isNotEmpty()) {
                    item { SectionHeader("已暂存 (${s.staged.size})") }
                    items(s.staged, key = { "s-${it.path}" }) { f ->
                        FileRow(f, actionIcon = FeatherIcons.Minus, actionDesc = "取消暂存", onAction = { onUnstage(f.path) }, enabled = !busy)
                    }
                }
                if (s.unstaged.isNotEmpty()) {
                    item { SectionHeader("已修改 (${s.unstaged.size})") }
                    items(s.unstaged, key = { "u-${it.path}" }) { f ->
                        FileRow(f, actionIcon = FeatherIcons.Plus, actionDesc = "暂存", onAction = { onStage(f.path) }, enabled = !busy, onClick = { onFileDiff(f.path) })
                    }
                }
                if (s.untracked.isNotEmpty()) {
                    item { SectionHeader("未跟踪 (${s.untracked.size})") }
                    items(s.untracked, key = { it }) { path ->
                        FileRow(
                            file = GitFileChange(path, "?", staged = false),
                            actionIcon = FeatherIcons.Plus,
                            actionDesc = "暂存",
                            onAction = { onStage(path) },
                            enabled = !busy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOverview(status: GitStatus?, clean: Boolean) {
    val staged = status?.staged?.size ?: 0
    val modified = status?.unstaged?.size ?: 0
    val untracked = status?.untracked?.size ?: 0

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            FeatherIcons.GitBranch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (clean) "工作区干净" else "工作区有改动",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status?.branch ?: "(无分支)",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status != null && (status.ahead > 0 || status.behind > 0)) {
                    Spacer(Modifier.width(Spacing.sm))
                    SyncPill(ahead = status.ahead, behind = status.behind)
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusMetric("已暂存", staged, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatusMetric("已修改", modified, Color(0xFFD97706), Modifier.weight(1f))
                StatusMetric("未跟踪", untracked, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SyncPill(ahead: Int, behind: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.pill)
    ) {
        Text(
            text = buildString {
                if (ahead > 0) append("↑$ahead")
                if (behind > 0) {
                    if (isNotEmpty()) append("  ")
                    append("↓$behind")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun StatusMetric(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.sm),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusActionsBar(
    busy: Boolean,
    hasStagedChanges: Boolean,
    hasRemote: Boolean,
    hasIdentity: Boolean,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit
) {
    val canCommit = !busy && hasStagedChanges && hasIdentity
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ActionButton("提交更改", FeatherIcons.Check, prominent = true, enabled = canCommit, onClick = onCommit, modifier = Modifier.fillMaxWidth())
                if (!hasIdentity) {
                    Text(
                        "未配置署名，无法提交。请在「凭据」页填写 user.name / user.email。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton("暂存全部", FeatherIcons.Plus, enabled = !busy, onClick = onStageAll, modifier = Modifier.weight(1f))
                    ActionButton("拉取", FeatherIcons.DownloadCloud, enabled = !busy && hasRemote, onClick = onPull, modifier = Modifier.weight(1f))
                    ActionButton("推送", FeatherIcons.UploadCloud, enabled = !busy && hasRemote, onClick = onPush, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton("提交更改", FeatherIcons.Check, prominent = true, enabled = canCommit, onClick = onCommit, modifier = Modifier.weight(1.4f))
                    ActionButton("暂存全部", FeatherIcons.Plus, enabled = !busy, onClick = onStageAll, modifier = Modifier.weight(1f))
                    ActionButton("拉取", FeatherIcons.DownloadCloud, enabled = !busy && hasRemote, onClick = onPull, modifier = Modifier.weight(1f))
                    ActionButton("推送", FeatherIcons.UploadCloud, enabled = !busy && hasRemote, onClick = onPush, modifier = Modifier.weight(1f))
                }
                if (!hasIdentity) {
                    Text(
                        "未配置署名，无法提交。请在「凭据」页填写 user.name / user.email。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BranchesTab(
    branches: List<GitBranch>,
    tags: List<GitTag>,
    checkoutLoading: String?,
    onCheckout: (String, Boolean) -> Unit,
    onCreateBranch: (String, String?, Boolean) -> Unit,
    onDeleteBranch: (String) -> Unit,
    onDeleteRemoteBranch: (String) -> Unit,
    onRenameBranch: (String, String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit
) {
    if (branches.isEmpty() && tags.isEmpty()) {
        EmptyState("暂无分支")
        return
    }
    val currentBranch = branches.firstOrNull { it.current }?.name ?: "未检出分支"
    val localBranches = branches.filter { !it.remote }
    val remoteBranches = branches.filter { it.remote }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isExpanded(key: String): Boolean = expanded[key] ?: true

    var pendingCheckout by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    pendingCheckout?.let { (ref, isRemote) ->
        val isTag = tags.any { it.name == ref }
        AlertDialog(
            onDismissRequest = { pendingCheckout = null },
            title = { Text("切换分支") },
            text = {
                Text(
                    if (isTag) "切换到标签 $ref 将进入 detached HEAD 状态——不处于任何分支上，新提交不属于任何分支。确定继续？"
                    else if (isRemote) "从远程分支 $ref 创建本地跟踪分支并切换？"
                    else "确定切换到 $ref？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCheckout = null
                    onCheckout(ref, isRemote)
                }) { Text("切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCheckout = null }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog) {
        val localBranchNames = localBranches.map { it.name }
        var newName by remember { mutableStateOf("") }
        var startPoint by remember { mutableStateOf(currentBranch) }
        var checkout by remember { mutableStateOf(true) }
        var expanded by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showCreateDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "新建分支",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("分支名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = startPoint,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("基准分支") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        localBranchNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { startPoint = name; expanded = false }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("创建并切换")
                    Switch(checked = checkout, onCheckedChange = { checkout = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        onClick = {
                            onCreateBranch(newName.trim(), startPoint, checkout)
                            showCreateDialog = false
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("创建") }
                }
            }
        }
    }

    pendingDelete?.let { (name, isRemote) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (isRemote) "删除远程分支" else "删除分支") },
            text = {
                Text(
                    if (isRemote) "删除远程分支 $name？该操作不可撤销。"
                    else "删除本地分支 $name？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    if (isRemote) onDeleteRemoteBranch(name) else onDeleteBranch(name)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    pendingRename?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("重命名分支") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将 $oldName 重命名为：")
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新分支名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank() && trimmed != oldName) {
                            onRenameBranch(oldName, trimmed)
                            pendingRename = null
                        }
                    },
                    enabled = newName.trim().isNotBlank() && newName.trim() != oldName
                ) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("取消") }
            }
        )
    }

    if (showCreateTagDialog) {
        var tagName by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCreateTagDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "新建标签",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "从当前 HEAD（$currentBranch）创建轻量标签",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("标签名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showCreateTagDialog = false }) { Text("取消") }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        onClick = {
                            val trimmed = tagName.trim()
                            if (trimmed.isNotBlank()) {
                                onCreateTag(trimmed)
                                showCreateTagDialog = false
                            }
                        },
                        enabled = tagName.trim().isNotBlank()
                    ) { Text("创建") }
                }
            }
        }
    }

    pendingDeleteTag?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("删除标签") },
            text = { Text("删除本地标签 $name？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteTag = null
                    onDeleteTag(name)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") }
            }
        )
    }

    val localTree = remember(localBranches) { buildBranchTree(localBranches) }
    val remoteTree = remember(remoteBranches) { buildBranchTree(remoteBranches) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl)
    ) {
        item { BranchesOverview(currentBranch, localBranches.size, remoteBranches.size, tags.size) }
        item {
            RefSectionHeader(
                title = "HEAD",
                isExpanded = isExpanded("head"),
                onToggle = { expanded["head"] = !isExpanded("head") }
            )
        }
        if (isExpanded("head")) {
            item {
                RefRow(
                    name = currentBranch,
                    subtitle = "当前检出",
                    icon = FeatherIcons.GitCommit,
                    isCurrent = true
                )
            }
        }
        if (localBranches.isNotEmpty()) {
            item {
                RefSectionHeader(
                    title = "Local (${localBranches.size})",
                    isExpanded = isExpanded("local"),
                    onToggle = { expanded["local"] = !isExpanded("local") },
                    onAdd = { showCreateDialog = true }
                )
            }
            if (isExpanded("local")) {
                renderBranchTree(
                    localTree,
                    depth = 1,
                    expanded,
                    isRemote = false,
                    checkoutLoading = checkoutLoading,
                    onCheckout = { ref, remote -> pendingCheckout = ref to remote },
                    onRenameBranch = { pendingRename = it },
                    onDeleteBranch = { pendingDelete = it to false },
                    onDeleteRemoteBranch = {}
                )
            }
        }
        if (remoteBranches.isNotEmpty()) {
            item {
                RefSectionHeader(
                    title = "Remote (${remoteBranches.size})",
                    isExpanded = isExpanded("remote"),
                    onToggle = { expanded["remote"] = !isExpanded("remote") }
                )
            }
            if (isExpanded("remote")) {
                renderBranchTree(
                    remoteTree,
                    depth = 1,
                    expanded,
                    isRemote = true,
                    checkoutLoading = checkoutLoading,
                    onCheckout = { ref, remote -> pendingCheckout = ref to remote },
                    onRenameBranch = {},
                    onDeleteBranch = {},
                    onDeleteRemoteBranch = { pendingDelete = it to true }
                )
            }
        }
        if (tags.isNotEmpty()) {
            item {
                RefSectionHeader(
                    title = "Tags (${tags.size})",
                    isExpanded = isExpanded("tags"),
                    onToggle = { expanded["tags"] = !isExpanded("tags") },
                    onAdd = { showCreateTagDialog = true }
                )
            }
            if (isExpanded("tags")) {
                tags.forEach { t ->
                    item(key = "tag-${t.name}") {
                        RefRow(
                            name = t.name,
                            subtitle = t.shortHash,
                            icon = FeatherIcons.Tag,
                            isCurrent = false,
                            isLoading = checkoutLoading == t.name,
                            actions = listOf(
                                RefAction.Switch(onClick = { pendingCheckout = t.name to false }),
                                RefAction.Delete(onClick = { pendingDeleteTag = t.name })
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchesOverview(
    currentBranch: String,
    localCount: Int,
    remoteCount: Int,
    tagCount: Int
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            FeatherIcons.GitBranch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前分支",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentBranch,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusMetric("本地", localCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatusMetric("远程", remoteCount, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatusMetric("标签", tagCount, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RefSectionHeader(
    title: String,
    isExpanded: Boolean,
    indent: Int = 0,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = Spacing.lg + (indent * 16).dp, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
            contentDescription = if (isExpanded) "折叠" else "展开",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = "新增",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 分支/标签行可执行的操作项，用于长按弹出的操作菜单。
 */
private sealed class RefAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean,
    val onClick: () -> Unit
) {
    class Switch(onClick: () -> Unit) : RefAction("切换", FeatherIcons.GitCommit, false, onClick)
    class Rename(onClick: () -> Unit) : RefAction("重命名", FeatherIcons.Edit2, false, onClick)
    class Delete(onClick: () -> Unit) : RefAction("删除", FeatherIcons.Trash2, true, onClick)
}

@Composable
private fun RefRow(
    name: String,
    subtitle: String?,
    icon: ImageVector,
    isCurrent: Boolean,
    isLoading: Boolean = false,
    indent: Int = 0,
    actions: List<RefAction> = emptyList()
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isLoading || actions.isEmpty()) it
                else it.clickable { menuExpanded = true }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg + (indent * 16).dp, end = Spacing.lg)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (menuExpanded) {
        RefActionSheet(
            refName = name,
            actions = actions,
            onDismiss = { menuExpanded = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefActionSheet(
    refName: String,
    actions: List<RefAction>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = refName,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            actions.forEach { action ->
                val tint = if (action.isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                Surface(
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = tint
                        )
                        Spacer(Modifier.width(Spacing.lg))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分支树节点：中间段为虚拟文件夹（branch 可能为空），叶子段承载 [GitBranch]。
 */
private data class BranchNode(
    val segment: String,
    val fullPath: String,
    var branch: GitBranch? = null,
    val children: MutableList<BranchNode> = mutableListOf()
)

/**
 * 按 `/` 切分分支名构建层级树。同层按段名字典序排序。
 */
private fun buildBranchTree(branches: List<GitBranch>): List<BranchNode> {
    val root = BranchNode("", "", null)
    for (b in branches.sortedBy { it.name }) {
        val parts = b.name.split('/')
        var cur = root
        val path = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (path.isNotEmpty()) path.append('/')
            path.append(part)
            val isLeaf = i == parts.lastIndex
            val existing = cur.children.find { it.segment == part }
            cur = if (existing == null) {
                BranchNode(part, path.toString(), if (isLeaf) b else null).also { cur.children.add(it) }
            } else {
                if (isLeaf) existing.branch = b
                existing
            }
        }
    }
    sortBranchTree(root)
    return root.children
}

private fun sortBranchTree(node: BranchNode) {
    node.children.sortBy { it.segment.lowercase() }
    node.children.forEach(::sortBranchTree)
}

/**
 * 递归向 [LazyListScope] 注入分支树节点。文件夹节点可折叠。
 */
private fun LazyListScope.renderBranchTree(
    nodes: List<BranchNode>,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    isRemote: Boolean,
    checkoutLoading: String?,
    onCheckout: (String, Boolean) -> Unit,
    onRenameBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit,
    onDeleteRemoteBranch: (String) -> Unit
) {
    for (node in nodes) {
        val isFolder = node.children.isNotEmpty()
        val isOpen = expanded[node.fullPath] ?: true
        item(key = "node-${node.fullPath}") {
            if (isFolder) {
                RefSectionHeader(
                    title = node.segment,
                    isExpanded = isOpen,
                    indent = depth,
                    onToggle = { expanded[node.fullPath] = !isOpen }
                )
            } else {
                node.branch?.let { b ->
                    val actions = if (isRemote) {
                        listOf(
                            RefAction.Switch(onClick = { onCheckout(b.name, true) }),
                            RefAction.Delete(onClick = { onDeleteRemoteBranch(b.name) })
                        )
                    } else {
                        buildList {
                            if (!b.current) add(RefAction.Switch(onClick = { onCheckout(b.name, false) }))
                            add(RefAction.Rename(onClick = { onRenameBranch(b.name) }))
                            if (!b.current) add(RefAction.Delete(onClick = { onDeleteBranch(b.name) }))
                        }
                    }
                    RefRow(
                        name = node.segment,
                        subtitle = if (b.current) "当前检出" else null,
                        icon = if (isRemote) FeatherIcons.Cloud else FeatherIcons.GitBranch,
                        isCurrent = b.current,
                        isLoading = checkoutLoading == b.name,
                        indent = depth,
                        actions = actions
                    )
                }
            }
        }
        if (isFolder && isOpen) {
            renderBranchTree(node.children, depth + 1, expanded, isRemote, checkoutLoading, onCheckout, onRenameBranch, onDeleteBranch, onDeleteRemoteBranch)
        }
    }
}

@Composable
private fun LogTab(
    graph: GitGraph,
    expandedCommits: Set<String>,
    commitFiles: Map<String, List<GitFileChange>>,
    loadingCommit: String?,
    onToggleCommit: (String) -> Unit,
    onFileDiff: (String, String) -> Unit
) {
    val commits = graph.commits
    if (commits.isEmpty()) {
        EmptyState("暂无提交记录")
        return
    }
    // 泳道调色板：按列号循环取色，分支越多颜色越丰富。
    val laneColors = rememberLaneColors(graph.maxLane + 1)
    // 每个提交到其父提交的边列表（按提交索引分组），供 Canvas 绘制连线。
    val edgesByCommit = remember(graph) { groupEdgesByCommit(graph) }
    // Canvas 宽度：每个泳道一列，加左右内边距。
    val laneWidth = 26.dp
    val canvasWidth = laneWidth * (graph.maxLane + 1) + Spacing.sm * 2
    // 每行高度，用于计算连线纵向跨度（节点居中）。
    val rowHeight = 72.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl)
    ) {
        item { LogOverview(commits = commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message) }, expandedCount = expandedCommits.size) }
        item { SectionHeader("提交记录 (${commits.size})") }
        commits.forEachIndexed { index, c ->
            val isExpanded = c.hash in expandedCommits
            item(key = "commit-${c.hash}") {
                GraphCommitRow(
                    commit = c,
                    lane = graph.lanes[c.hash] ?: 0,
                    edges = edgesByCommit[index].orEmpty(),
                    activeLanes = graph.activeLanes[c.hash].orEmpty(),
                    laneColors = laneColors,
                    canvasWidth = canvasWidth,
                    laneWidth = laneWidth,
                    rowHeight = rowHeight,
                    refs = graph.refs[c.hash].orEmpty(),
                    isExpanded = isExpanded,
                    onToggle = { onToggleCommit(c.hash) }
                )
            }
            if (isExpanded) {
                val files = commitFiles[c.hash]
                when {
                    loadingCommit == c.hash && files == null -> {
                        item(key = "loading-${c.hash}") { LoadingFilesRow(canvasWidth) }
                    }
                    files == null -> Unit
                    files.isEmpty() -> {
                        item(key = "empty-${c.hash}") { EmptyCommitFilesRow(canvasWidth) }
                    }
                    else -> {
                        item(key = "summary-${c.hash}") {
                            CommitFilesSummary(files.size, canvasWidth)
                        }
                        items(
                            items = files,
                            key = { f -> "file-${c.hash}-${f.statusCode}-${f.path}" }
                        ) { file ->
                            CommitFileRow(file, indent = canvasWidth + Spacing.sm, onClick = { onFileDiff(c.hash, file.path) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogOverview(commits: List<GitCommit>, expandedCount: Int) {
    val latest = commits.first()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            FeatherIcons.File,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "最新提交",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = latest.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusMetric("最近提交", commits.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatusMetric("已展开", expandedCount, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                DateMetric(latest.date, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DateMetric(date: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.sm),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
            Text(
                text = "最新日期",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GraphCommitRow(
    commit: GraphCommit,
    lane: Int,
    edges: List<GraphEdge>,
    activeLanes: List<Int>,
    laneColors: List<Color>,
    canvasWidth: androidx.compose.ui.unit.Dp,
    laneWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    refs: List<GitGraphRef>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val isMerge = commit.isMerge
    val nodeColor = laneColors.getOrElse(lane) { Color.Gray }
    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowHeight)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧拓扑图区域：Canvas 绘制节点 + 上下连线。
            GraphCanvas(
                edges = edges,
                activeLanes = activeLanes,
                lane = lane,
                isMerge = isMerge,
                laneColors = laneColors,
                canvasWidth = canvasWidth,
                laneWidth = laneWidth,
                rowHeight = rowHeight
            )
            // 右侧提交信息。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.lg)
            ) {
                // 引用标签行（分支/标签 pill）。
                if (refs.isNotEmpty()) {
                    RefPills(refs = refs)
                    Spacer(Modifier.height(Spacing.xs))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = if (isExpanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = commit.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = nodeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(Radius.pill)
                            ) {
                                Text(
                                    text = commit.shortHash,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = nodeColor,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = commit.author,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = commit.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 拓扑图 Canvas：绘制当前提交的节点圆点 + 分支连线。
 *
 * 绘制顺序（后绘制覆盖先绘制，保证节点压在线上）：
 * 1. 贯穿竖线：[activeLanes] 中每个泳道画一条贯穿全行（0→height）的竖线。这些是穿过本行
 *    仍活跃的分支主线（含本提交所在列），保证分支支线在中间行不断裂。
 * 2. 出边下半段：本提交到父提交的跨列边，从本行中心画贝塞尔曲线到底部目标列，表示分叉/合并。
 *    同列边已被贯穿竖线覆盖，不重复画。
 * 3. 入边上半段：指向本提交的跨列边（来自上方提交的合并支线），从顶部源列画曲线到本行中心。
 * 4. 节点圆点：合并提交画环形双圈，普通提交画实心圆。
 */
@Composable
private fun GraphCanvas(
    edges: List<GraphEdge>,
    activeLanes: List<Int>,
    lane: Int,
    isMerge: Boolean,
    laneColors: List<Color>,
    canvasWidth: androidx.compose.ui.unit.Dp,
    laneWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    val nodeColor = laneColors.getOrElse(lane) { Color.Gray }
    Canvas(modifier = Modifier.size(width = canvasWidth, height = rowHeight)) {
        val lanePx = laneWidth.toPx()
        val padPx = Spacing.sm.toPx()
        val centerX = lane * lanePx + lanePx / 2f + padPx
        val centerY = size.height / 2f
        val stroke = 2.5.dp.toPx()

        // 1. 贯穿竖线：所有活跃泳道画全行竖线，保证分支主线连续。
        for (activeLane in activeLanes) {
            val color = laneColors.getOrElse(activeLane) { Color.Gray }
            val x = activeLane * lanePx + lanePx / 2f + padPx
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // 2. 跨列连线：无论是本提交向父分叉（第一父），还是合并父支线（第二父及以后），
        //    在自上而下的渲染顺序中，父节点都在下方。因此所有跨列边都从本行中心，
        //    画贝塞尔曲线到底部目标列。同列边已被贯穿竖线覆盖，不重复画。
        for (edge in edges) {
            if (edge.fromLane == edge.toLane) continue
            val color = laneColors.getOrElse(edge.lane) { Color.Gray }
            val fromX = edge.fromLane * lanePx + lanePx / 2f + padPx
            val toX = edge.toLane * lanePx + lanePx / 2f + padPx
            val midY = size.height * 0.75f
            val path = Path().apply {
                moveTo(fromX, centerY)
                cubicTo(
                    fromX, midY,
                    toX, midY,
                    toX, size.height
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 3. 节点圆点：合并提交画环形双圈，普通提交画实心圆。
        val nodeRadius = if (isMerge) 7.dp.toPx() else 5.dp.toPx()
        if (isMerge) {
            drawCircle(
                color = nodeColor,
                radius = nodeRadius,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = nodeColor,
                radius = nodeRadius / 2f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        } else {
            drawCircle(
                color = nodeColor,
                radius = nodeRadius,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}

/** 提交引用标签行：当前分支高亮 primary，其余用各分支色。 */
@Composable
private fun RefPills(refs: List<GitGraphRef>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        refs.forEach { ref ->
            val isCurrent = ref.isCurrent
            val bg = if (isCurrent) MaterialTheme.colorScheme.primary
                else if (ref.isBranch) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            val fg = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                else if (ref.isBranch) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onTertiaryContainer
            Surface(
                color = bg,
                shape = RoundedCornerShape(Radius.xs)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (ref.isRemote) FeatherIcons.Cloud else if (ref.isBranch) FeatherIcons.GitBranch else FeatherIcons.Tag,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = ref.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 泳道调色板：按列数生成一组区分度高的颜色，循环复用。
 * 颜色取自常见 IDE 分支配色，深浅适中以适配明暗主题。
 */
@Composable
private fun rememberLaneColors(count: Int): List<Color> {
    val palette = remember {
        listOf(
            Color(0xFF2563EB), // 蓝
            Color(0xFF16A34A), // 绿
            Color(0xFFD97706), // 琥珀
            Color(0xFF9333EA), // 紫
            Color(0xFF0891B2), // 青
            Color(0xFFDC2626), // 红
            Color(0xFF7C3AED), // 靛
            Color(0xFFCA8A04)  // 金
        )
    }
    return remember(count) {
        (0 until count).map { palette[it % palette.size] }
    }
}

/**
 * 把 [GitGraph.edges] 按来源提交索引分组。[GitRepository.computeLanes] 按提交顺序为每个提交
 * 生成 `parents.size` 条边（根提交 0 条），故 edges 是扁平有序的，按父数累积分组即可重建对应。
 * 返回 `Map<提交索引, List<GraphEdge>>`，供 [GraphCommitRow] 绘制本行连线。
 */
private fun groupEdgesByCommit(graph: GitGraph): Map<Int, List<GraphEdge>> {
    val result = mutableMapOf<Int, List<GraphEdge>>()
    var edgeIdx = 0
    graph.commits.forEachIndexed { commitIdx, commit ->
        // 根提交无边；非根提交有 parents.size 条边。
        val n = if (commit.parents.isEmpty()) 0 else commit.parents.size
        val list = mutableListOf<GraphEdge>()
        repeat(n) {
            if (edgeIdx < graph.edges.size) {
                list.add(graph.edges[edgeIdx++])
            }
        }
        result[commitIdx] = list
    }
    return result
}

@Composable
private fun CommitFilesSummary(count: Int, indent: androidx.compose.ui.unit.Dp) {
    Text(
        text = "$count 个文件改动",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = indent, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun EmptyCommitFilesRow(indent: androidx.compose.ui.unit.Dp) {
    Text(
        text = "该提交无文件改动",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = indent, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
    )
}

@Composable
private fun LoadingFilesRow(indent: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = indent, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "正在加载改动文件...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CommitFileRow(file: GitFileChange, indent: androidx.compose.ui.unit.Dp, onClick: () -> Unit = {}) {
    val fileName = file.path.substringAfterLast('/')
    val directory = file.path.substringBeforeLast('/', missingDelimiterValue = "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = indent, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(file.statusCode)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (directory.isNotEmpty()) {
                    Text(
                        text = directory,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = indent + 44.dp)
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FileRow(
    file: GitFileChange,
    actionIcon: ImageVector,
    actionDesc: String,
    onAction: () -> Unit,
    enabled: Boolean,
    onClick: (() -> Unit)? = null
) {
    val fileName = file.path.substringAfterLast('/')
    val directory = file.path.substringBeforeLast('/', missingDelimiterValue = "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(file.statusCode)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (directory.isNotEmpty()) {
                    Text(
                        text = directory,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            IconButton(onClick = onAction, enabled = enabled) {
                Icon(
                    actionIcon,
                    contentDescription = actionDesc,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 60.dp)
        )
    }
}

/**
 * 状态码 → 配色（容器色 + 前景色）。
 *
 * 与多数 Git 客户端约定一致：新增=绿、修改=琥珀、删除=红、重命名/复制=蓝、未跟踪=灰、
 * 冲突=紫红、类型变更=青。仅取首字符判定，porcelain 的 X/Y 两列统一映射。
 */
private fun statusColor(code: String): Pair<Color, Color> = when (code.firstOrNull()) {
    'A' -> Color(0xFF16A34A) to Color(0xFFFFFFFF)            // 新增
    'M' -> Color(0xFFD97706) to Color(0xFFFFFFFF)            // 修改
    'D' -> Color(0xFFDC2626) to Color(0xFFFFFFFF)            // 删除
    'R', 'C' -> Color(0xFF2563EB) to Color(0xFFFFFFFF)       // 重命名/复制
    '?' -> Color(0xFF94A3B8) to Color(0xFFFFFFFF)            // 未跟踪
    'U' -> Color(0xFF9333EA) to Color(0xFFFFFFFF)            // 冲突
    'T' -> Color(0xFF0891B2) to Color(0xFFFFFFFF)            // 类型变更
    else -> Color(0xFF64748B) to Color(0xFFFFFFFF)           // 兜底
}

@Composable
private fun StatusChip(text: String) {
    val (bg, fg) = statusColor(text)
    Surface(
        color = bg,
        shape = RoundedCornerShape(Radius.xs),
        modifier = Modifier.size(width = 28.dp, height = 22.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.take(2),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = fg
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    prominent: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (prominent) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(Radius.sm),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(Radius.sm),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = Spacing.sm)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 非仓库态：文案 + 「初始化 Git 仓库」按钮（跑 `git init`，成功后自动刷新进仓库态）。 */
@Composable
private fun NotARepoState(onInit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "当前工作区不是 Git 仓库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "初始化后会在此创建 .git，之后可暂存、提交、关联远程。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onInit) {
                Icon(FeatherIcons.GitBranch, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("初始化 Git 仓库")
            }
        }
    }
}

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交") },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("提交信息") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (message.isNotBlank()) onConfirm(message.trim()) },
                enabled = message.isNotBlank()
            ) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
