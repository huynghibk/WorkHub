package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workhub.app.R
import com.workhub.shared.TaskDto
import com.workhub.shared.WorkTree
import com.workhub.shared.WorkspaceDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHubScreen(
    workspace: WorkspaceDto?,
    userId: String?,
    userName: String?,
    tasks: List<TaskDto>,
    work: WorkTree,
    scope: TaskScope,
    search: String,
    onScope: (TaskScope) -> Unit,
    onSearch: (String) -> Unit,
    onOpenBucket: (TaskBucket) -> Unit,
    onOpenList: (ListChoice) -> Unit,
    onCreateTask: () -> Unit,
    onAddList: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isAdmin = canManageContent(workspace?.role)
    val scoped = applyScope(tasks, scope, userId, work, isAdmin)
    val lists = flattenLists(work)
    val query = search.trim()
    val filteredLists = if (query.isBlank()) {
        lists
    } else {
        lists.filter { it.label.contains(query, ignoreCase = true) || it.spaceName.contains(query, ignoreCase = true) }
    }
    val personalLists = filteredLists.filter { it.isPrivate }
    val teamLists = filteredLists.filter { !it.isPrivate }

    val todayCount = applyBucket(scoped, TaskBucket.TODAY, userId).size
    val overdueCount = applyBucket(scoped, TaskBucket.OVERDUE, userId).size
    val priorityCount = applyBucket(scoped, TaskBucket.PRIORITIES, userId).size
    val upcomingCount = applyBucket(scoped, TaskBucket.UPCOMING, userId).size
    val mineCount = applyBucket(scoped, TaskBucket.MINE, userId).size
    val reminderCount = applyBucket(scoped, TaskBucket.REMINDERS, userId).size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.my_tasks_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    workspace?.name ?: stringResource(R.string.work_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RoleBadge(workspace?.role.orEmpty())
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(stringResource(R.string.search_hint_short)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF9CA3AF))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }

        item {
            Text(
                stringResource(R.string.task_scope_label),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ScopeChip(
                    label = stringResource(R.string.task_scope_all),
                    selected = scope == TaskScope.ALL,
                    onClick = { onScope(TaskScope.ALL) },
                    modifier = Modifier.weight(1f),
                )
                ScopeChip(
                    label = stringResource(R.string.task_scope_mine),
                    selected = scope == TaskScope.MINE,
                    onClick = { onScope(TaskScope.MINE) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ScopeChip(
                    label = stringResource(R.string.task_scope_personal),
                    selected = scope == TaskScope.PERSONAL,
                    onClick = { onScope(TaskScope.PERSONAL) },
                    modifier = Modifier.weight(1f),
                )
                ScopeChip(
                    label = stringResource(R.string.task_scope_team),
                    selected = scope == TaskScope.TEAM,
                    onClick = { onScope(TaskScope.TEAM) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (isAdmin) {
                Spacer(Modifier.height(8.dp))
                ScopeChip(
                    label = stringResource(R.string.task_scope_managed),
                    selected = scope == TaskScope.MANAGED,
                    onClick = { onScope(TaskScope.MANAGED) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(
                    if (isAdmin) R.string.task_hub_admin_hint else R.string.task_hub_member_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubStatCard(
                            title = stringResource(R.string.hub_today),
                            count = todayCount,
                            icon = Icons.Filled.CalendarMonth,
                            tint = Color(0xFF7C3AED),
                            onClick = { onOpenBucket(TaskBucket.TODAY) },
                            modifier = Modifier.weight(1f),
                        )
                        HubStatCard(
                            title = stringResource(R.string.hub_overdue),
                            count = overdueCount,
                            icon = Icons.Filled.Notifications,
                            tint = Color(0xFFDC2626),
                            emphasize = overdueCount > 0,
                            onClick = { onOpenBucket(TaskBucket.OVERDUE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubStatCard(
                            title = stringResource(R.string.hub_priorities),
                            count = priorityCount,
                            icon = Icons.Filled.Flag,
                            tint = Color(0xFFEA580C),
                            onClick = { onOpenBucket(TaskBucket.PRIORITIES) },
                            modifier = Modifier.weight(1f),
                        )
                        HubStatCard(
                            title = stringResource(R.string.hub_upcoming),
                            count = upcomingCount,
                            icon = Icons.Outlined.Event,
                            tint = Color(0xFFCA8A04),
                            onClick = { onOpenBucket(TaskBucket.UPCOMING) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubStatCard(
                            title = stringResource(R.string.hub_my_tasks),
                            count = mineCount,
                            icon = Icons.Filled.CheckCircle,
                            tint = Color(0xFF2563EB),
                            onClick = { onOpenBucket(TaskBucket.MINE) },
                            modifier = Modifier.weight(1f),
                        )
                        HubStatCard(
                            title = stringResource(R.string.hub_reminders),
                            count = reminderCount,
                            icon = Icons.Filled.Alarm,
                            tint = Color(0xFF0D9488),
                            onClick = { onOpenBucket(TaskBucket.REMINDERS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HubStatCard(
                        title = stringResource(R.string.hub_comments),
                        count = 0,
                        icon = Icons.Filled.ChatBubbleOutline,
                        tint = Color(0xFF16A34A),
                        onClick = { onOpenBucket(TaskBucket.COMMENTS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (personalLists.isNotEmpty() || onAddList != null) {
            item {
                ListSectionHeader(
                    title = stringResource(R.string.hub_personal_lists),
                    onAdd = onAddList,
                )
            }
            if (personalLists.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.hub_personal_empty),
                        color = Color(0xFF9CA3AF),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(personalLists, key = { "p-${it.id}" }) { list ->
                    HubListRow(list = list, initial = userName, onClick = { onOpenList(list) })
                }
            }
        }

        item {
            ListSectionHeader(
                title = stringResource(R.string.hub_team_lists),
                onAdd = onAddList,
            )
        }
        if (teamLists.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.hub_team_empty),
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(teamLists, key = { "t-${it.id}" }) { list ->
                HubListRow(list = list, initial = list.spaceName, onClick = { onOpenList(list) })
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onCreateTask,
                shape = RoundedCornerShape(50),
                color = Color(0xFF6D28D9),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Text(
                        stringResource(R.string.hub_create_task),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val label = roleLabel(role)
    Surface(shape = RoundedCornerShape(50), color = Color(0xFFEEF2FF)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF4F46E5),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEEF2FF) else Color(0xFFF3F4F6),
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF4F46E5) else Color(0xFF374151),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HubStatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF9FAFB),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasize) tint else Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (emphasize) tint else Color(0xFF111827),
                )
            }
        }
    }
}

@Composable
private fun ListSectionHeader(title: String, onAdd: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_level))
            }
        }
    }
}

@Composable
private fun HubListRow(list: ListChoice, initial: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (list.isPrivate) Color(0xFF111827) else Color(0xFF4F46E5)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (initial ?: list.spaceName).trim().take(1).uppercase().ifBlank { "L" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    list.label.substringAfterLast(" / "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (list.isPrivate) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                list.spaceName,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
