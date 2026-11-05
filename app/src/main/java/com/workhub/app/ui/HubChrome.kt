package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workhub.app.R

internal val TouchTarget = 48.dp

@Composable
internal fun StatusBadge(status: String) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val (bg, fg) = when (normalizeStatus(status)) {
        "IN_PROGRESS" -> Royal to Color.White
        "DONE" -> DoneGreen to Color.White
        "CANCELLED" ->
            (if (dark) Color(0xFF3F3F46) else Color(0xFFE4E4E7)) to
                (if (dark) Color(0xFFE2E8F0) else Color(0xFF18181B))
        else ->
            (if (dark) Color(0xFF3F3F46) else Color(0xFFE4E4E7)) to
                (if (dark) Color(0xFFF8FAFC) else Color(0xFF18181B))
    }
    Text(
        statusLabel(status),
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = fg,
        maxLines = 1,
    )
}

@Composable
internal fun PriorityChip(priority: String) {
    val key = normalizePriority(priority, isNew = false)
    if (key == "NONE") return
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (key) {
        "URGENT", "HIGH" -> scheme.errorContainer to scheme.onErrorContainer
        "LOW" -> scheme.surfaceVariant to scheme.onSurfaceVariant
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Text(
        priorityLabel(priority),
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
    )
}

@Composable
internal fun InitialsAvatar(name: String, size: Dp = 24.dp) {
    val hue = remember(name) { ((name.hashCode().toLong() and 0x7fff_ffff) % 12) * 30f }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = Color.hsl(hue, 0.32f, if (dark) 0.28f else 0.86f)
    val fg = Color.hsl(hue, 0.40f, if (dark) 0.88f else 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
internal fun CommentCount(count: Int) {
    if (count <= 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = stringResource(R.string.comment_count, count),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EntityRailCard(
    task: Boolean,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val wash = if (task) taskWash() else noteWash()
    val rail = if (task) taskRail() else NoteRail
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(wash),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(rail),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (task) stringResource(R.string.chat_shared_task) else stringResource(R.string.chat_shared_note),
                style = MaterialTheme.typography.labelSmall,
                color = rail,
                fontWeight = FontWeight.Medium,
            )
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun EmptyHint(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.CheckCircle,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (onAction != null && actionLabel != null) {
            SoftPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeRow(
    enableComplete: Boolean,
    enableDelete: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!enableComplete) return@rememberSwipeToDismissBoxState false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onComplete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!enableDelete) return@rememberSwipeToDismissBoxState false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enableComplete,
        enableDismissFromEndToStart = enableDelete,
        backgroundContent = {
            val towardEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = when (state.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeDone
                SwipeToDismissBoxValue.EndToStart -> SwipeDelete
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (towardEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (towardEnd) Icons.Outlined.CheckCircle else Icons.Outlined.Delete,
                    contentDescription = stringResource(if (towardEnd) R.string.swipe_complete else R.string.swipe_delete),
                    tint = Color.White,
                )
            }
        },
        content = content,
    )
}

@Composable
internal fun notesEmptyIcon(): ImageVector = Icons.Outlined.NoteAdd
