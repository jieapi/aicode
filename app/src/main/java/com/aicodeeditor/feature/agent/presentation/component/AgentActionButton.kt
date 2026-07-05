package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing

internal enum class AgentActionTone {
    Neutral,
    Success,
    Danger
}

@Composable
internal fun AgentActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: AgentActionTone = AgentActionTone.Neutral
) {
    val shape = RoundedCornerShape(Radius.sm)
    val success = Color(0xFF15803D)
    val (container, content, border) = when (tone) {
        AgentActionTone.Success -> Triple(
            if (enabled) success else MaterialTheme.colorScheme.surfaceVariant,
            if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            if (enabled) success else MaterialTheme.colorScheme.outlineVariant
        )
        AgentActionTone.Danger -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        )
        AgentActionTone.Neutral -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant
        )
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
