package com.aicodeeditor.feature.agent.presentation.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.ui.rememberImeBottomInset
import com.aicodeeditor.feature.agent.domain.model.AgentMode
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.tool.PendingToolPermission
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.presentation.component.ModelLogoIcon
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import com.aicodeeditor.feature.workspace.presentation.component.WorkspaceIconButton
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.Check
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Square
import compose.icons.feathericons.X
import java.util.Base64

@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isBusy: Boolean,
    workspaceViewModel: WorkspaceViewModel?,
    hasRunningSessions: () -> Boolean,
    activeProvider: AIProviderConfig?,
    providers: List<AIProviderConfig>,
    onSelectModel: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit,
    pendingAttachments: List<PendingUploadAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    canUploadFiles: Boolean,
    canUploadImages: Boolean,
    onUploadFile: () -> Unit,
    onUploadImage: () -> Unit
) {
    val canSend = (value.isNotBlank() || pendingAttachments.isNotEmpty()) && !isBusy
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = rememberImeBottomInset())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(Radius.lg)
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            PendingAttachmentPreviewList(
                attachments = pendingAttachments,
                onRemoveAttachment = onRemoveAttachment
            )

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp, max = 140.dp),
                placeholder = {
                    Text(
                        "输入消息与 AI 聊天…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = !isBusy,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPlan = currentMode == AgentMode.PLAN
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPlan) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clickable {
                                val nextMode = if (isPlan) AgentMode.BUILD else AgentMode.PLAN
                                onToggleMode(nextMode)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(58.dp)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPlan) "PLAN" else "BUILD",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlan) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                )
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.sm))

                    ModelIconButton(
                        provider = activeProvider,
                        providers = providers,
                        onSelectModel = onSelectModel,
                        onManage = onNavigateToSettings
                    )

                    if (workspaceViewModel != null) {
                        WorkspaceIconButton(
                            viewModel = workspaceViewModel,
                            hasRunningSessions = hasRunningSessions
                        )
                    }

                    UploadIconButton(
                        enabled = canUploadFiles && !isBusy,
                        icon = FeatherIcons.FileText,
                        contentDescription = "上传文件",
                        onClick = onUploadFile
                    )

                    UploadIconButton(
                        enabled = canUploadImages && !isBusy,
                        icon = FeatherIcons.Image,
                        contentDescription = "上传图片",
                        onClick = onUploadImage
                    )
                }
                SendButton(canSend = canSend, isBusy = isBusy, onSend = onSend, onStop = onStop)
            }
        }
    }
}

@Composable
private fun PendingAttachmentPreviewList(
    attachments: List<PendingUploadAttachment>,
    onRemoveAttachment: (Int) -> Unit
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        attachments.forEachIndexed { index, attachment ->
            PendingAttachmentPreviewItem(
                attachment = attachment,
                onRemove = { onRemoveAttachment(index) }
            )
        }
    }
}

@Composable
private fun PendingAttachmentPreviewItem(
    attachment: PendingUploadAttachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.size(76.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (attachment.image != null) {
                ImageThumbnail(
                    attachment = attachment,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                FileAttachmentPreview(attachment = attachment)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        FeatherIcons.X,
                        contentDescription = "移除附件",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(
    attachment: PendingUploadAttachment,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val base64Data = attachment.image?.base64Data.orEmpty()
    val bitmap = remember(base64Data) {
        runCatching {
            val bytes = Base64.getDecoder().decode(base64Data)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 180, 180)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        if (bitmap != null) {
            ComposeImage(
                bitmap = bitmap,
                contentDescription = attachment.fileName.ifBlank { "图片预览" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    FeatherIcons.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FileAttachmentPreview(attachment: PendingUploadAttachment) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            FeatherIcons.FileText,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatBytes(attachment.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var sampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}

@Composable
internal fun UploadIconButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

/**
 * 输入区下行的模型切换图标按钮
 */
@Composable
internal fun ModelIconButton(
    provider: AIProviderConfig?,
    providers: List<AIProviderConfig>,
    onSelectModel: (String, String) -> Unit,
    onManage: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }) {
        ModelLogoIcon(modelName = provider?.effectiveModel.orEmpty(), size = 22.dp)
    }

    if (showSheet) {
        ModelSheet(
            providers = providers,
            currentProviderId = provider?.id ?: "",
            currentModel = provider?.effectiveModel ?: "",
            onSelect = { pId, model ->
                onSelectModel(pId, model)
                showSheet = false
            },
            onManage = {
                onManage()
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSheet(
    providers: List<AIProviderConfig>,
    currentProviderId: String,
    currentModel: String,
    onSelect: (String, String) -> Unit,
    onManage: () -> Unit,
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
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManage) {
                    Icon(FeatherIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("管理")
                }
            }

            if (providers.all { it.models.isEmpty() }) {
                Text(
                    "暂无可用模型，请在「管理」中添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    providers.forEach { p ->
                        if (p.models.isNotEmpty()) {
                            item(key = "header_${p.id}") {
                                Row(
                                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(p.models, key = { "${p.id}_$it" }) { model ->
                                val selected = p.id == currentProviderId && model == currentModel
                                ModelRow(
                                    name = model,
                                    selected = selected,
                                    onClick = { onSelect(p.id, model) }
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
internal fun ModelRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelLogoIcon(modelName = name, size = 22.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                FeatherIcons.Check,
                contentDescription = "当前",
                tint = Brand.IconGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun SendButton(canSend: Boolean, isBusy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val clickable = isBusy || canSend
    val bg = if (clickable) {
        Modifier.background(if (isBusy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    }
    val iconTint = if (clickable) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(Spacing.xs)
            .size(40.dp)
            .clip(CircleShape)
            .then(bg)
            .clickable(enabled = clickable, onClick = if (isBusy) onStop else onSend),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            Icon(
                FeatherIcons.Square,
                contentDescription = "停止",
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                FeatherIcons.ArrowUp,
                contentDescription = "发送",
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun ToolPermissionPanel(
    request: PendingToolPermission,
    onChoice: (PermissionChoice) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = request.toolName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            SelectionContainer {
                Column(
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = request.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (request.details.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = request.details,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val canRemember = request.rememberablePatterns.isNotEmpty()
            val rememberLabel = when {
                !canRemember -> request.rememberDisabledReason ?: "含命令替换/管道/重定向，无法记住，仅可单次放行"
                request.rememberablePatterns == listOf("*") -> "「始终允许」将在本项目记住：该工具的全部调用"
                else -> "「始终允许」将在本项目记住：" + request.rememberablePatterns.joinToString("、")
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = rememberLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AgentActionButton(
                    text = "拒绝",
                    onClick = { onChoice(PermissionChoice.REJECT) },
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Danger
                )
                AgentActionButton(
                    text = "始终允许",
                    onClick = { onChoice(PermissionChoice.ALWAYS) },
                    modifier = Modifier.weight(1f),
                    enabled = canRemember,
                    tone = AgentActionTone.Neutral
                )
                AgentActionButton(
                    text = "允许",
                    onClick = { onChoice(PermissionChoice.ONCE) },
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Success
                )
            }
        }
    }
}

@Composable
internal fun StatusBanner(state: com.aicodeeditor.feature.agent.presentation.AgentUIState) {
    androidx.compose.animation.AnimatedVisibility(
        visible = state is com.aicodeeditor.feature.agent.presentation.AgentUIState.Error || state is com.aicodeeditor.feature.agent.presentation.AgentUIState.Applied,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        when (state) {
            is com.aicodeeditor.feature.agent.presentation.AgentUIState.Error -> InfoBanner(
                text = state.message,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                icon = FeatherIcons.AlertCircle
            )

            is com.aicodeeditor.feature.agent.presentation.AgentUIState.Applied -> InfoBanner(
                text = "代码变更已应用",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = FeatherIcons.Check
            )

            else -> {}
        }
    }
}

@Composable
internal fun InfoBanner(
    text: String,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = container,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Brand.IconGray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ChangePreviewPanel(
    changes: List<com.aicodeeditor.feature.agent.domain.model.CodeChange>,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                "预览代码变更 · ${changes.size} 处",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))

            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(changes) { change -> ChangeItem(change) }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AgentActionButton(
                    text = "拒绝",
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Danger
                )
                AgentActionButton(
                    text = "应用",
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Success
                )
            }
        }
    }
}

@Composable
fun ChangeItem(change: com.aicodeeditor.feature.agent.domain.model.CodeChange) {
    val accent = when (change.type) {
        com.aicodeeditor.feature.agent.domain.model.ChangeType.CREATE -> MaterialTheme.colorScheme.tertiary
        com.aicodeeditor.feature.agent.domain.model.ChangeType.DELETE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (change.type) {
                com.aicodeeditor.feature.agent.domain.model.ChangeType.CREATE -> "+"
                com.aicodeeditor.feature.agent.domain.model.ChangeType.DELETE -> "−"
                com.aicodeeditor.feature.agent.domain.model.ChangeType.REPLACE -> "~"
                else -> "→"
            },
            modifier = Modifier.width(20.dp),
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${change.filePath.substringAfterLast('/')} · L${change.startLine}-${change.endLine}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 计划审查面板：AI 从 PLAN 模式切回 BUILD 时弹出，展示计划摘要供用户批准或继续反馈。
 * 风格与 ToolPermissionPanel / AskUserQuestionPanel 一致。
 */
@Composable
internal fun PlanApprovalPanel(
    state: com.aicodeeditor.feature.agent.domain.tool.mode.PlanApprovalRequest,
    onApprove: () -> Unit,
    onRefine: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "计划已完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.reason.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AgentActionButton(
                    text = "继续反馈",
                    onClick = onRefine,
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Neutral
                )
                AgentActionButton(
                    text = "批准并实施",
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    tone = AgentActionTone.Success
                )
            }
        }
    }
}
