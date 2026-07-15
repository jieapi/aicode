package com.aicode.feature.git.presentation.component

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
    var editingCredential by remember { mutableStateOf<GitCredential?>(null) }
    var isAddingCredential by remember { mutableStateOf(false) }

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
                        // 子页优先回上一级：编辑/新增 -> 凭据列表 -> 退出 Git 页
                        when {
                            editingCredential != null -> editingCredential = null
                            isAddingCredential -> isAddingCredential = false
                            showCredentials -> showCredentials = false
                            else -> onNavigateBack()
                        }
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
                    } else if (editingCredential == null && !isAddingCredential) {
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
            // 凭据编辑/新增子页（全屏覆盖 Git tab 内容）
            val editing = editingCredential
            if (editing != null) {
                CredentialEditorScreen(
                    initial = editing,
                    onBack = { editingCredential = null },
                    onSave = { credentialViewModel.saveCredential(it) },
                    onDelete = { credentialViewModel.deleteCredential(it) }
                )
                return@Column
            }
            if (isAddingCredential) {
                CredentialEditorScreen(
                    initial = null,
                    onBack = { isAddingCredential = false },
                    onSave = { credentialViewModel.saveCredential(it); isAddingCredential = false },
                    onDelete = { /* 新增态无删除 */ }
                )
                return@Column
            }
            if (showCredentials) {
                // 每次进入凭据页重新读署名：用户可能在终端改过项目级/全局署名，避免回显陈旧空值。
                LaunchedEffect(Unit) { credentialViewModel.refreshIdentity() }
                CredentialListSection(
                    credentials = credState.credentials,
                    userName = credState.userName,
                    userEmail = credState.userEmail,
                    globalUserName = credState.globalUserName,
                    onEdit = { editingCredential = it },
                    onToggleDefault = { id, isDefault -> credentialViewModel.setDefault(id, isDefault) },
                    onSaveIdentity = { name, email -> credentialViewModel.saveUserIdentity(name, email) }
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
                state.notARepo -> EmptyState("当前工作区不是 Git 仓库")
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
                    GitTab.BRANCHES -> BranchesTab(state.branches)
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

    // push/pull 缺凭据时：VM 置位 pendingCredentialHost，此处弹登录框，填完保存落盘 + 自动重试原操作。
    state.pendingCredentialHost?.let { host ->
        CredentialPromptDialog(
            host = host,
            onConfirm = { username, token ->
                viewModel.saveCredentialAndRetry(host, username, token)
            },
            onDismiss = { viewModel.cancelPendingCredential() }
        )
    }
}

/**
 * 拉取/推送缺 https 凭据时的登录弹窗。host 只读预填（来自当前 remote），用户填 username/token；
 * 填完确认后 VM 存 Room + 落盘到容器持久挂载 + 自动重试原操作。仿 Win git 无凭据弹窗登录体验。
 * 新存的凭据自动设为该 host 默认（与 findForHost 的 isDefault 优先语义一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialPromptDialog(
    host: String,
    onConfirm: (username: String, token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    val canSave = username.trim().isNotBlank() && token.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要 $host 的登录凭据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { /* host 来自 remote，只读 */ },
                    label = { Text("远程主机") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("访问令牌 Token / PAT") },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (tokenVisible) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "填完即保存并自动重试。该凭据同时会保存到容器内 git，终端与 AI 执行裸 git 命令时也可直接使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSave) onConfirm(username.trim(), token) },
                enabled = canSave
            ) { Text("保存并重试") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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

@Composable
private fun BranchesTab(branches: List<GitBranch>) {
    if (branches.isEmpty()) {
        EmptyState("暂无分支")
        return
    }
    // 折叠状态：key = 节点完整路径，value = 是否展开；缺省视为展开。
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val tree = remember(branches) { buildBranchTree(branches) }
    val currentBranch = branches.firstOrNull { it.current }?.name ?: "未检出分支"
    val localCount = branches.count { !it.remote }
    val remoteCount = branches.count { it.remote }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl)
    ) {
        item { BranchesOverview(currentBranch, localCount, remoteCount) }
        item { SectionHeader("分支列表 (${branches.size})") }
        tree.forEach { node -> renderBranchNode(node, depth = 0, expanded) }
    }
}

@Composable
private fun BranchesOverview(
    currentBranch: String,
    localCount: Int,
    remoteCount: Int
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
                StatusMetric("本地分支", localCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatusMetric("远程分支", remoteCount, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatusMetric("全部", localCount + remoteCount, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
    }
}

/**
 * 递归向 [LazyListScope] 注入节点行。文件夹节点可折叠，展开时递归注入子节点。
 */
private fun LazyListScope.renderBranchNode(
    node: BranchNode,
    depth: Int,
    expanded: MutableMap<String, Boolean>
) {
    val isFolder = node.children.isNotEmpty()
    val isOpen = expanded[node.fullPath] ?: true
    item(key = "node-${node.fullPath}") {
        BranchRow(node, depth, isFolder, isOpen) {
            expanded[node.fullPath] = !isOpen
        }
    }
    if (isFolder && isOpen) {
        node.children.forEach { child -> renderBranchNode(child, depth + 1, expanded) }
    }
}

@Composable
private fun BranchRow(
    node: BranchNode,
    depth: Int,
    isFolder: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit
) {
    val isCurrent = node.branch?.current == true
    val contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg + (depth * 16).dp, end = Spacing.lg)
                .height(48.dp)
                .let { if (isFolder) it.clickable(onClick = onToggle) else it },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFolder) {
                Icon(
                    imageVector = if (isOpen) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                    contentDescription = if (isOpen) "折叠" else "展开",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = if (isFolder) FeatherIcons.Folder else FeatherIcons.GitBranch,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.segment,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrent) {
                    Text(
                        text = "当前检出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            when {
                isFolder -> BranchPill("${countBranchLeaves(node)}")
                node.branch?.remote == true -> BranchPill("远程")
                isCurrent -> BranchPill("当前")
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun BranchPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.pill)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            maxLines = 1
        )
    }
}

/**
 * 分支树的节点：中间段为虚拟文件夹（branch 可能为空），叶子段承载 [GitBranch]。
 * 同名既做文件夹又做叶子（如 `feature` 与 `feature/foo` 并存）时，该节点同时持有 branch 与 children。
 */
private data class BranchNode(
    val segment: String,
    val fullPath: String,
    var branch: GitBranch? = null,
    val children: MutableList<BranchNode> = mutableListOf()
)

/**
 * 按 `/` 切分分支名构建层级树。`origin/feature/x` → origin ▸ feature ▸ x，
 * 本地与远程分支天然分桶到各自顶层目录下。同层按段名字典序排序。
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

/** 该子树下的实际分支数（含自身若同时是叶子）。 */
private fun countBranchLeaves(node: BranchNode): Int {
    val self = if (node.branch != null) 1 else 0
    return if (node.children.isEmpty()) self else node.children.sumOf(::countBranchLeaves) + self
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
                        BranchPill(commit.shortHash)
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
