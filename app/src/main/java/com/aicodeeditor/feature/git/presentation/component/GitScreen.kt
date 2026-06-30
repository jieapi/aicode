package com.aicodeeditor.feature.git.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.git.domain.model.GitBranch
import com.aicodeeditor.feature.git.domain.model.GitCommit
import com.aicodeeditor.feature.git.domain.model.GitFileChange
import com.aicodeeditor.feature.git.domain.model.GitStatus
import com.aicodeeditor.feature.git.domain.model.GitTab
import com.aicodeeditor.feature.git.presentation.GitViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // toast → Snackbar 一次性消费。
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    var showCommitDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Git") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                        Icon(FeatherIcons.RefreshCw, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
}

@Composable
private fun StatusTab(
    status: GitStatus?,
    busy: Boolean,
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
        // 顶部：分支与领先/落后。
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s?.branch ?: "(无分支)",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (s != null && (s.ahead > 0 || s.behind > 0)) {
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = buildString {
                            if (s.ahead > 0) append("↑${s.ahead}")
                            if (s.behind > 0) { if (isNotEmpty()) append(' '); append("↓${s.behind}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 底部操作栏。
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ActionButton("全部暂存", FeatherIcons.Plus, enabled = !busy, onClick = onStageAll, modifier = Modifier.weight(1f))
            ActionButton("提交", FeatherIcons.Check, enabled = !busy && s?.staged?.isNotEmpty() == true, onClick = onCommit, modifier = Modifier.weight(1f))
            ActionButton("拉取", FeatherIcons.DownloadCloud, enabled = !busy, onClick = onPull, modifier = Modifier.weight(1f))
            ActionButton("推送", FeatherIcons.UploadCloud, enabled = !busy, onClick = onPush, modifier = Modifier.weight(1f))
        }

        HorizontalDivider()

        if (clean) {
            EmptyState("工作区干净，无改动")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
private fun BranchesTab(branches: List<GitBranch>) {
    if (branches.isEmpty()) {
        EmptyState("暂无分支")
        return
    }
    // 折叠状态：key = 节点完整路径，value = 是否展开；缺省视为展开。
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val tree = remember(branches) { buildBranchTree(branches) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text = "共 ${branches.size} 个分支",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        }
        tree.forEach { node -> renderBranchNode(node, depth = 0, expanded) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg + (depth * 16).dp, end = Spacing.lg)
            .padding(vertical = Spacing.xs)
            .let { if (isFolder) it.clickable(onClick = onToggle) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开箭头占位（叶子用等宽占位对齐）。
        if (isFolder) {
            Icon(
                imageVector = if (isOpen) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                contentDescription = if (isOpen) "折叠" else "展开",
                modifier = Modifier.size(18.dp),
                tint = androidx.compose.ui.graphics.Color(0xFF424242))
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.xs))
        // 当前分支圆点。
        Text(
            text = if (isCurrent) "●" else " ",
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            imageVector = if (isFolder) {
                if (isOpen) FeatherIcons.Folder else FeatherIcons.Folder
            } else {
                FeatherIcons.GitBranch
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = androidx.compose.ui.graphics.Color(0xFF424242))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = node.segment,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when {
            isFolder -> Text(
                text = "${countBranchLeaves(node)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            node.branch?.remote == true -> StatusChip("远程")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    LazyColumn(Modifier.fillMaxSize()) {
        items(commits, key = { it.hash }) { c ->
            val isExpanded = c.hash in expandedCommits
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCommit(c.hash) }
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isExpanded) FeatherIcons.ChevronDown
                            else FeatherIcons.ChevronRight,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            modifier = Modifier.size(18.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFF424242))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = c.shortHash,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            text = c.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isExpanded) {
                            Spacer(Modifier.width(Spacing.sm))
                            val files = commitFiles[c.hash]
                            val count = files?.size
                            Text(
                                text = when {
                                    loadingCommit == c.hash && files == null -> "加载中…"
                                    count != null -> "$count 个文件"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = c.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = c.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isExpanded) {
                        val files = commitFiles[c.hash]
                        when {
                            loadingCommit == c.hash && files == null -> {
                                Row(
                                    modifier = Modifier.padding(top = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        text = "正在加载改动文件…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            files == null -> Unit
                            files.isEmpty() -> Text(
                                text = "该提交无文件改动",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm)
                            )
                            else -> CommitFileTree(files, modifier = Modifier.padding(top = Spacing.sm))
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 提交改动文件树。按路径分隔符 `/` 分层，文件夹恒展开（与分支树不同——这里只关心快速浏览改动范围）。
 */
@Composable
private fun CommitFileTree(files: List<GitFileChange>, modifier: Modifier = Modifier) {
    val tree = remember(files) { buildFileTree(files) }
    Column(modifier = modifier.fillMaxWidth()) {
        tree.forEach { node -> FileNodeRow(node, depth = 1) }
    }
}

@Composable
private fun FileNodeRow(node: FileTreeNode, depth: Int) {
    val isFolder = node.children.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp, end = 0.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isFolder) FeatherIcons.Folder else FeatherIcons.File,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = androidx.compose.ui.graphics.Color(0xFF424242))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = node.segment,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val f = node.file
        if (f != null) StatusChip(f.statusCode)
    }
    if (isFolder) node.children.forEach { FileNodeRow(it, depth + 1) }
}

/** 文件树节点：中间段为虚拟文件夹（file 可空），叶子段承载 [GitFileChange]。 */
private data class FileTreeNode(
    val segment: String,
    val fullPath: String,
    var file: GitFileChange? = null,
    val children: MutableList<FileTreeNode> = mutableListOf()
)

/** 按 `/` 切分路径构建层级树，同层字典序排序。 */
private fun buildFileTree(files: List<GitFileChange>): List<FileTreeNode> {
    val root = FileTreeNode("", "", null)
    for (f in files.sortedBy { it.path }) {
        val parts = f.path.split('/')
        var cur = root
        val path = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (path.isNotEmpty()) path.append('/')
            path.append(part)
            val isLeaf = i == parts.lastIndex
            val existing = cur.children.find { it.segment == part }
            cur = if (existing == null) {
                FileTreeNode(part, path.toString(), if (isLeaf) f else null).also { cur.children.add(it) }
            } else {
                if (isLeaf) existing.file = f
                existing
            }
        }
    }
    sortFileTree(root)
    return root.children
}

private fun sortFileTree(node: FileTreeNode) {
    node.children.sortBy { it.segment.lowercase() }
    node.children.forEach(::sortFileTree)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
}

@Composable
private fun FileRow(
    file: GitFileChange,
    actionIcon: ImageVector,
    actionDesc: String,
    onAction: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(file.statusCode)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = file.path,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onAction, enabled = enabled) {
            Icon(actionIcon, contentDescription = actionDesc, tint = androidx.compose.ui.graphics.Color(0xFF424242))
        }
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
    modifier: Modifier = Modifier
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
