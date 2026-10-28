package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workhub.app.R
import com.workhub.shared.CommentDto
import com.workhub.shared.MemberDto
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditor(
    initial: TaskDto?,
    members: List<MemberDto>,
    listId: String?,
    currentUserId: String?,
    allTasks: List<TaskDto>,
    comments: List<CommentDto>,
    subtasks: List<TaskDto>,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaskWrite, (String) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onComment: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    defaultDueAt: Long? = null,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var status by remember(initial?.id) { mutableStateOf(normalizeStatus(initial?.status)) }
    var priority by remember(initial?.id) { mutableStateOf(normalizePriority(initial?.priority, initial == null)) }
    var assigneeIds by remember(initial?.id) {
        mutableStateOf(initial?.assigneeIds.orEmpty().ifEmpty { listOfNotNull(initial?.assigneeId) }.toSet())
    }
    var startAt by remember(initial?.id) { mutableStateOf(initial?.startAt) }
    var dueAt by remember(initial?.id, defaultDueAt) {
        mutableStateOf(initial?.dueAt ?: defaultDueAt)
    }
    var linkMode by remember(initial?.id) {
        mutableStateOf(
            when {
                initial?.parentId != null -> "PARENT"
                subtasks.isNotEmpty() -> "CHILDREN"
                else -> "NONE"
            },
        )
    }
    var parentPick by remember(initial?.id) { mutableStateOf(initial?.parentId.orEmpty()) }
    var childPicks by remember(initial?.id) { mutableStateOf(subtasks.map { it.id }.toSet()) }
    var removedChildren by remember(initial?.id) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(initial?.id, subtasks.map { it.id }) {
        childPicks = (childPicks + subtasks.map { it.id }) - removedChildren
    }
    var comment by remember(initial?.id) { mutableStateOf("") }
    var childTitle by remember(initial?.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var statusMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var assigneeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(initial == null) }
    val titleError = stringResource(R.string.task_title_required)
    val orderError = stringResource(R.string.task_date_order)
    val parentError = stringResource(R.string.task_parent_required)
    val statuses = listOf("BACKLOG", "TODO", "IN_PROGRESS", "DONE", "CANCELLED")
    val priorities = listOf("URGENT", "HIGH", "MEDIUM", "LOW", "NONE")
    val parentChoices = allTasks.filter { task -> task.id != initial?.id && task.parentId == null }
    val childChoices = allTasks.filter { task ->
        task.id != initial?.id && task.subtaskCount == 0 && (task.parentId == null || task.parentId == initial?.id)
    }
    val selectedMembers = members.filter { it.id in assigneeIds }
    val rangeLabel = formatDateRange(startAt, dueAt)
    val dialogTitle = when {
        initial == null -> stringResource(R.string.task_new)
        canSave -> stringResource(R.string.task_edit)
        else -> stringResource(R.string.task_view)
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val hideKeyboard = rememberHideKeyboard()

    fun submit() {
        if (saving) return
        val start = startAt
        val end = dueAt
        if (start != null && end != null && end < start) {
            formError = orderError
            return
        }
        if (title.isBlank()) {
            formError = titleError
            return
        }
        if (linkMode == "PARENT" && parentPick.isBlank()) {
            formError = parentError
            showDetails = true
            return
        }
        formError = null
        saving = true
        onSave(
            TaskWrite(
                title = title.trim(),
                description = description.trim(),
                status = status,
                assigneeIds = assigneeIds.toList(),
                dueAt = dueAt,
                startAt = startAt,
                listId = listId,
                parentId = if (linkMode == "PARENT") parentPick else null,
                priority = priority,
                childIds = if (linkMode == "CHILDREN") childPicks.toList() else emptyList(),
                updateLinks = true,
            ),
        ) { message ->
            saving = false
            formError = message
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dialogTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    HideKeyboardButton(always = true)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E7EB)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!canSave && initial != null) {
                        Text(stringResource(R.string.task_view_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        enabled = canSave,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                        ),
                        placeholder = {
                            Text(
                                stringResource(R.string.task_title_hint),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD1D5DB),
                            )
                        },
                        trailingIcon = if (canSave && title.isNotEmpty()) {
                            { TextFieldClearCopyActions(value = title, onClear = { title = "" }) }
                        } else {
                            null
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            TaskMetaChip(
                                container = Color(0xFFF3F4F6),
                                content = statusColor(status),
                                onClick = { if (canSave) statusMenu = true },
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status)))
                                Text(statusLabel(status), fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            }
                            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                statuses.forEach { value ->
                                    DropdownMenuItem(
                                        text = { ColoredOption(statusLabel(value), statusColor(value), status == value) },
                                        onClick = {
                                            status = value
                                            statusMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        TaskMetaChip(
                            container = if (rangeLabel != null) Color(0xFFFEE2E2) else Color(0xFFF3F4F6),
                            content = if (rangeLabel != null) Color(0xFFB91C1C) else Color(0xFF6B7280),
                            onClick = {
                                if (canSave) {
                                    hideKeyboard()
                                    datePickerOpen = true
                                }
                            },
                            onClear = if (canSave && rangeLabel != null) {
                                {
                                    startAt = null
                                    dueAt = null
                                }
                            } else {
                                null
                            },
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                rangeLabel ?: stringResource(R.string.task_dates),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }

                        Box {
                            TaskMetaChip(
                                container = Color(0xFFF3F4F6),
                                content = Color(0xFF374151),
                                onClick = { if (canSave) assigneeMenu = true },
                            ) {
                                if (selectedMembers.isEmpty()) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.assignee_none), fontWeight = FontWeight.Medium)
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                        selectedMembers.take(3).forEach { member ->
                                            AssigneeAvatar(member.name)
                                        }
                                    }
                                    if (selectedMembers.size > 3) {
                                        Text("+${selectedMembers.size - 3}", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.assignee_none)) },
                                    onClick = {
                                        assigneeIds = emptySet()
                                        assigneeMenu = false
                                    },
                                )
                                members.forEach { member ->
                                    val checked = member.id in assigneeIds
                                    val label = if (member.id == currentUserId) {
                                        stringResource(R.string.assign_me, member.name)
                                    } else {
                                        member.name
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (checked) "✓ $label" else label) },
                                        onClick = {
                                            assigneeIds = if (checked) assigneeIds - member.id else assigneeIds + member.id
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            TaskMetaChip(
                                container = priorityColor(priority).copy(alpha = 0.12f),
                                content = priorityColor(priority),
                                onClick = { if (canSave) priorityMenu = true },
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(priorityLabel(priority), fontWeight = FontWeight.Medium)
                            }
                            DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                                priorities.forEach { value ->
                                    DropdownMenuItem(
                                        text = { ColoredOption(priorityLabel(value), priorityColor(value), priority == value) },
                                        onClick = {
                                            priority = value
                                            priorityMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            TaskMetaChip(
                                container = Color(0xFFF3F4F6),
                                content = Color(0xFF6B7280),
                                onClick = { moreMenu = true },
                            ) {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.task_details)) },
                                    onClick = {
                                        showDetails = !showDetails
                                        moreMenu = false
                                    },
                                )
                                if (canSave) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.attach_location)) },
                                        onClick = {
                                            showDetails = true
                                            moreMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF374151), lineHeight = 22.sp),
                        placeholder = {
                            Text(stringResource(R.string.task_desc_hint), color = Color(0xFF9CA3AF))
                        },
                        trailingIcon = if (canSave && description.isNotEmpty()) {
                            { TextFieldClearCopyActions(value = description, onClear = { description = "" }) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9FAFB),
                            unfocusedContainerColor = Color(0xFFF9FAFB),
                            focusedBorderColor = Color(0xFFE5E7EB),
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                        ),
                    )

                    if (canSave) {
                        LocationAttachActions { link ->
                            description = if (description.isBlank()) link else "$description\n$link"
                        }
                    }

                    if (showDetails) {
                        Text(stringResource(R.string.task_link), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val linkLabel = when (linkMode) {
                            "PARENT" -> stringResource(R.string.task_link_parent)
                            "CHILDREN" -> stringResource(R.string.task_link_children)
                            else -> stringResource(R.string.task_link_none)
                        }
                        ChoiceMenu(
                            label = linkLabel,
                            selected = linkMode != "NONE",
                            enabled = canSave,
                        ) { close ->
                            listOf(
                                "NONE" to stringResource(R.string.task_link_none),
                                "PARENT" to stringResource(R.string.task_link_parent),
                                "CHILDREN" to stringResource(R.string.task_link_children),
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (linkMode == mode) "✓ $label" else label) },
                                    onClick = {
                                        linkMode = mode
                                        close()
                                    },
                                )
                            }
                        }
                        if (linkMode == "PARENT") {
                            if (parentChoices.isEmpty()) {
                                Text(stringResource(R.string.task_no_other), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val parentLabel = parentChoices.firstOrNull { it.id == parentPick }?.title
                                    ?: stringResource(R.string.task_pick_parent)
                                ChoiceMenu(label = parentLabel, selected = parentPick.isNotBlank(), enabled = canSave) { close ->
                                    parentChoices.forEach { task ->
                                        DropdownMenuItem(
                                            text = { Text(if (task.id == parentPick) "✓ ${task.title}" else task.title) },
                                            onClick = {
                                                parentPick = task.id
                                                close()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (linkMode == "CHILDREN") {
                            if (childChoices.isEmpty()) {
                                Text(stringResource(R.string.task_no_other), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                childChoices.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { task ->
                                            FilterChip(
                                                selected = task.id in childPicks,
                                                onClick = {
                                                    if (task.id in childPicks) {
                                                        childPicks = childPicks - task.id
                                                        removedChildren = removedChildren + task.id
                                                    } else {
                                                        childPicks = childPicks + task.id
                                                        removedChildren = removedChildren - task.id
                                                    }
                                                },
                                                label = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (initial != null && initial.parentId == null && linkMode != "PARENT") {
                        Text(stringResource(R.string.task_subtasks), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        subtasks.forEach { child ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF9FAFB))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(child.status)))
                                Text(child.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(statusLabel(child.status), color = Color(0xFF6B7280), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (canSave) {
                            OutlinedField(childTitle, { childTitle = it }, stringResource(R.string.task_pick_children))
                            TextButton(
                                onClick = {
                                    if (childTitle.isNotBlank()) {
                                        onAddSubtask(childTitle.trim())
                                        childTitle = ""
                                    }
                                },
                            ) { Text(stringResource(R.string.add)) }
                        }
                    }

                    if (initial != null) {
                        Text(stringResource(R.string.task_comments), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        comments.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF9FAFB))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(item.authorName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                Text(item.body, color = Color(0xFF374151))
                            }
                        }
                        if (canSave) {
                            OutlinedField(comment, { comment = it }, stringResource(R.string.comment_hint))
                            TextButton(
                                onClick = {
                                    if (comment.isNotBlank()) {
                                        onComment(comment.trim())
                                        comment = ""
                                    }
                                },
                            ) { Text(stringResource(R.string.send_comment)) }
                        }
                    }
                }

                formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E7EB)))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                    Row {
                        if (onDelete != null) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                        }
                        if (canSave) {
                            TextButton(onClick = ::submit, enabled = !saving) {
                                if (saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (datePickerOpen) {
        DateRangePickerDialog(
            startMillis = startAt,
            endMillis = dueAt,
            onDismiss = { datePickerOpen = false },
            onConfirm = { start, end ->
                startAt = start
                dueAt = end
                datePickerOpen = false
            },
        )
    }
}

@Composable
private fun TaskMetaChip(
    container: Color,
    content: Color,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    body: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(container)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            body()
            if (onClear != null) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.clear_date),
                    tint = content,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClear),
                )
            }
        }
    }
}

@Composable
private fun AssigneeAvatar(name: String) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFFEEF2FF))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color(0xFF4F46E5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColoredOption(label: String, color: Color, checked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(if (checked) "✓ $label" else label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun statusColor(status: String): Color = when (normalizeStatus(status)) {
    "BACKLOG" -> Color(0xFF6B7280)
    "TODO" -> Color(0xFF2563EB)
    "IN_PROGRESS" -> Color(0xFFD97706)
    "DONE" -> Color(0xFF059669)
    "CANCELLED" -> Color(0xFFDC2626)
    else -> Color(0xFF6B7280)
}

internal fun priorityColor(priority: String): Color = when (normalizePriority(priority, isNew = false)) {
    "URGENT" -> Color(0xFFDC2626)
    "HIGH" -> Color(0xFFEA580C)
    "MEDIUM" -> Color(0xFFCA8A04)
    "LOW" -> Color(0xFF0284C7)
    "NONE" -> Color(0xFF6B7280)
    else -> Color(0xFF6B7280)
}
