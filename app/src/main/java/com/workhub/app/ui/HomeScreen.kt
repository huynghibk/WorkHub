package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.SearchHit
import com.workhub.shared.TaskDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Border = Color(0xFFE5E7EB)
private val TodayAccent = Color(0xFFF97316)
private val AssignedAccent = Color(0xFF0284C7)
private val OpenAccent = Color(0xFF10B981)

@Composable
fun HomeScreen(
    onOpenTasks: () -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenMaps: () -> Unit = {},
    onOpenSearch: (String) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val vm: HomeViewModel = viewModel(factory = homeFactory(graph.api))
    val me = session?.user?.id
    val open = vm.tasks.filter { it.parentId == null && it.status != "DONE" && it.status != "CANCELLED" }
    val today = open.count { isToday(it.dueAt) || isToday(it.startAt) }
    val assigned = open.count { me != null && (it.assigneeId == me || me in it.assigneeIds) }
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(query) {
        vm.search(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    if (query.isNotBlank()) onOpenSearch(query.trim())
                }),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { onOpenSearch(query.trim()) }) {
                            Text(stringResource(R.string.app_search_short))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF9FAFB),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Border,
                ),
            )
        }
        if (query.isBlank()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PulseCard(
                        label = stringResource(R.string.home_today),
                        value = today.toString(),
                        icon = Icons.Outlined.CalendarMonth,
                        accent = TodayAccent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTasks,
                    )
                    PulseCard(
                        label = stringResource(R.string.home_assigned),
                        value = assigned.toString(),
                        icon = Icons.Outlined.AssignmentInd,
                        accent = AssignedAccent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTasks,
                    )
                    PulseCard(
                        label = stringResource(R.string.home_open),
                        value = open.size.toString(),
                        icon = Icons.Outlined.TaskAlt,
                        accent = OpenAccent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTasks,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.home_tools),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_calendar),
                            iconRes = R.drawable.ic_brand_calendar,
                            onClick = onOpenCalendar,
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_maps),
                            iconRes = R.drawable.ic_brand_maps,
                            onClick = onOpenMaps,
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_search),
                            iconRes = R.drawable.ic_brand_google,
                            onClick = { onOpenSearch("") },
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_zalo),
                            iconRes = R.drawable.ic_brand_zalo,
                            onClick = { openZalo(context) },
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_messenger),
                            iconRes = R.drawable.ic_brand_messenger,
                            onClick = { openMessenger(context) },
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.app_wechat),
                            iconRes = R.drawable.ic_brand_wechat,
                            onClick = { openWeChat(context) },
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.tab_notes),
                            icon = Icons.Outlined.EditNote,
                            tint = Color(0xFFF59E0B),
                            onClick = onOpenNotes,
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.tab_chat),
                            icon = Icons.Outlined.ChatBubbleOutline,
                            tint = Color(0xFF14B8A6),
                            onClick = onOpenChat,
                        )
                    }
                    item {
                        ToolChip(
                            label = stringResource(R.string.tab_apps),
                            icon = Icons.Filled.Apps,
                            tint = Color(0xFF6366F1),
                            onClick = onOpenApps,
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                )
            }
            if (vm.tasks.isEmpty()) {
                item { Text(stringResource(R.string.home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(vm.tasks.filter { it.parentId == null }.take(8), key = { it.id }) { task ->
                    RecentRow(task, onOpenTasks)
                }
            }
        } else if (query.trim().length < 2) {
            item { Text(stringResource(R.string.search_more), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            if (vm.hits.isEmpty() && !vm.searching) {
                item { Text(stringResource(R.string.search_empty, query), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vm.hits, key = { "${it.kind}-${it.id}" }) { hit ->
                RecentRow(
                    title = hit.title,
                    subtitle = hit.snippet,
                    onClick = onOpenTasks,
                )
            }
        }
        vm.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    tint: Color = Color(0xFF374151),
    @androidx.annotation.DrawableRes iconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconRes != null -> {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF374151))
    }
}

@Composable
private fun PulseCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(118.dp)
            .border(1.dp, Border, SoftCard),
        shape = SoftCard,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                value,
                color = Color(0xFF111827),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentRow(task: TaskDto, onClick: () -> Unit) {
    RecentRow(task.title, "${statusLabel(task.status)} \u00B7 ${task.listName.ifBlank { stringResource(R.string.work_fallback) }}", onClick)
}

@Composable
private fun RecentRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Border, SoftCard),
        shape = SoftCard,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

class HomeViewModel(private val api: WorkHubApi) : ViewModel() {
    var tasks by mutableStateOf<List<TaskDto>>(emptyList())
        private set
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                tasks = api.tasks().sortedByDescending { it.updatedAt }
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        val text = query.trim()
        if (text.length < 2) {
            hits = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            searching = true
            delay(250)
            try {
                hits = api.search(text).hits
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                searching = false
            }
        }
    }
}

private fun homeFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(api) as T
    }
}
