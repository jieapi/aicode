package com.aicode.feature.git.presentation.component

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
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import com.aicode.feature.git.domain.model.GitTag
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
                        onPush = viewModel::push
                    )
                    GitTab.BRANCHES -> BranchesTab(
                        branches = state.branches,
                        tags = state.tags,
                        checkoutLoading = state.checkoutLoading,
                        onCheckout = viewModel::checkoutBranch,
                        onCreateBranch = viewModel::createBranch,
                        onDeleteBranch = viewModel::deleteBranch,
                        onDeleteRemoteBranch = viewModel::deleteRemoteBranch
                    )
                    GitTab.LOG -> LogTab(
                        commits = state.commits,
                        expandedCommits = state.expandedCommits,
                        commitFiles = state.commitFiles,
                        loadingCommit = state.loadingCommit,
                        onToggleCommit = viewModel::toggleCommit
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
    onPush: () -> Unit
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
                        FileRow(f, actionIcon = FeatherIcons.Plus, actionDesc = "暂存", onAction = { onStage(f.path) }, enabled = !busy)
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
    onDeleteRemoteBranch: (String) -> Unit
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

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建分支") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateBranch(newName.trim(), startPoint, checkout)
                        showCreateDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
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
                    isCurrent = true,
                    canCheckout = false
                )
            }
        }
        if (localBranches.isNotEmpty()) {
            item {
                RefSectionHeader(
                    title = "Local (${localBranches.size})",
                    isExpanded = isExpanded("local"),
                    onToggle = { expanded["local"] = !isExpanded("local") },
                    onLongClick = { showCreateDialog = true }
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
                    onToggle = { expanded["tags"] = !isExpanded("tags") }
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
                            canCheckout = true,
                            isLoading = checkoutLoading == t.name,
                            onClick = { pendingCheckout = t.name to false }
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
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onLongClick)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RefRow(
    name: String,
    subtitle: String?,
    icon: ImageVector,
    isCurrent: Boolean,
    canCheckout: Boolean,
    isLoading: Boolean = false,
    indent: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isLoading) it
                else if (onLongClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else if (canCheckout) it.clickable(onClick = onClick)
                else it
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
                    RefRow(
                        name = node.segment,
                        subtitle = if (b.current) "当前检出" else null,
                        icon = if (isRemote) FeatherIcons.Cloud else FeatherIcons.GitBranch,
                        isCurrent = b.current,
                        canCheckout = !b.current || isRemote,
                        isLoading = checkoutLoading == b.name,
                        indent = depth,
                        onClick = { onCheckout(b.name, isRemote) },
                        onLongClick = if (isRemote) {
                            { onDeleteRemoteBranch(b.name) }
                        } else if (!b.current) {
                            { onDeleteBranch(b.name) }
                        } else null
                    )
                }
            }
        }
        if (isFolder && isOpen) {
            renderBranchTree(node.children, depth + 1, expanded, isRemote, checkoutLoading, onCheckout, onDeleteBranch, onDeleteRemoteBranch)
        }
    }
}

@Composable
private fun LogTab(
    commits: List<GitCommit>,
    expandedCommits: Set<String>,
    commitFiles: Map<String, List<GitFileChange>>,
    loadingCommit: String?,
    onToggleCommit: (String) -> Unit
) {
    if (commits.isEmpty()) {
        EmptyState("暂无提交记录")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl)
    ) {
        item { LogOverview(commits = commits, expandedCount = expandedCommits.size) }
        item { SectionHeader("提交记录 (${commits.size})") }
        commits.forEach { c ->
            val isExpanded = c.hash in expandedCommits
            item(key = "commit-${c.hash}") {
                CommitRow(
                    commit = c,
                    isExpanded = isExpanded,
                    onToggle = { onToggleCommit(c.hash) }
                )
            }
            if (isExpanded) {
                val files = commitFiles[c.hash]
                when {
                    loadingCommit == c.hash && files == null -> {
                        item(key = "loading-${c.hash}") { LoadingFilesRow() }
                    }
                    files == null -> Unit
                    files.isEmpty() -> {
                        item(key = "empty-${c.hash}") { EmptyCommitFilesRow() }
                    }
                    else -> {
                        item(key = "summary-${c.hash}") {
                            CommitFilesSummary(files.size)
                        }
                        items(
                            items = files,
                            key = { f -> "file-${c.hash}-${f.statusCode}-${f.path}" }
                        ) { file ->
                            CommitFileRow(file)
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
private fun CommitRow(
    commit: GitCommit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (isExpanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = commit.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radius.pill)
                        ) {
                            Text(
                                text = commit.shortHash,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    }
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CommitFilesSummary(count: Int) {
    Text(
        text = "$count 个文件改动",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun EmptyCommitFilesRow() {
    Text(
        text = "该提交无文件改动",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
    )
}

@Composable
private fun LoadingFilesRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
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
private fun CommitFileRow(file: GitFileChange) {
    val fileName = file.path.substringAfterLast('/')
    val directory = file.path.substringBeforeLast('/', missingDelimiterValue = "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
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
            modifier = Modifier.padding(start = 104.dp)
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
    enabled: Boolean
) {
    val fileName = file.path.substringAfterLast('/')
    val directory = file.path.substringBeforeLast('/', missingDelimiterValue = "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
