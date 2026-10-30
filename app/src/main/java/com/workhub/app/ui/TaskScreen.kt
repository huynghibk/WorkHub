package com.workhub.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.CommentDto
import com.workhub.shared.MemberDto
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite
import com.workhub.shared.WorkTree
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen() {
    val graph = LocalAppGraph.current
    val vm: TaskViewModel = viewModel(factory = taskFactory(graph.api))
    val session by graph.session.state.collectAsState(initial = null)
    val user = session?.user
    val workspace = session?.workspace
    var scope by remember(workspace?.id, workspace?.role) {
        mutableStateOf(if (canManageContent(workspace?.role)) TaskScope.ALL else TaskScope.MINE)
    }
    var bucket by remember { mutableStateOf(TaskBucket.HUB) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var view by remember { mutableStateOf("LIST") }
    var daySelection by remember { mutableStateOf<DayStripSelection>(DayStripSelection.Day(LocalDate.now())) }
    var editor by remember { mutableStateOf<TaskDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var structuring by remember { mutableStateOf(false) }
    val lists = remember(vm.work) { flattenLists(vm.work) }
    val selectedList = lists.firstOrNull { it.id == vm.selectedListId }
    val isAdmin = canManageContent(workspace?.role)
    val scoped = remember(vm.tasks, scope, user?.id, vm.work, isAdmin) {
        applyScope(vm.tasks, scope, user?.id, vm.work, isAdmin)
    }
    val bucketed = remember(scoped, bucket, user?.id, selectedList?.id) {
        when (bucket) {
            TaskBucket.HUB -> emptyList()
            TaskBucket.LIST -> scoped.filter { task ->
                task.parentId == null && (selectedList == null || task.listId == selectedList.id)
            }
            else -> applyBucket(scoped, bucket, user?.id)
        }
    }
    val query = search.trim()
    val searched = if (query.isBlank() || bucket == TaskBucket.HUB) {
        bucketed
    } else {
        bucketed.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.listName.contains(query, ignoreCase = true)
        }
    }
    val statusFiltered = searched.filter { filter == "ALL" || it.status == filter }
    val overdueInScope = remember(statusFiltered, user?.id) {
        applyBucket(statusFiltered, TaskBucket.OVERDUE, user?.id)
    }
    val showDayStrip = bucket.showsDayStrip()
    val shown = remember(statusFiltered, daySelection, view, overdueInScope, showDayStrip) {
        when {
            !showDayStrip || view == "CALENDAR" || view == "BOARD" -> statusFiltered
            daySelection is DayStripSelection.Overdue -> overdueInScope
            daySelection is DayStripSelection.Day ->
                statusFiltered.filter { taskDueDate(it) == (daySelection as DayStripSelection.Day).date }
            else -> statusFiltered
        }
    }
    val functionTitle = when (bucket) {
        TaskBucket.HUB -> stringResource(R.string.my_tasks_title)
        TaskBucket.TODAY -> if (showDayStrip) dayStripTitle(daySelection) else stringResource(R.string.hub_today)
        TaskBucket.OVERDUE -> stringResource(R.string.hub_overdue)
        TaskBucket.PRIORITIES -> stringResource(R.string.hub_priorities)
        TaskBucket.UPCOMING -> if (showDayStrip) dayStripTitle(daySelection) else stringResource(R.string.hub_upcoming)
        TaskBucket.MINE -> stringResource(R.string.hub_my_tasks)
        TaskBucket.REMINDERS -> stringResource(R.string.hub_reminders)
        TaskBucket.COMMENTS -> stringResource(R.string.hub_comments)
        TaskBucket.LIST -> selectedList?.label?.substringAfterLast(" / ")
            ?: stringResource(R.string.tab_tasks)
    }
    val createLabel = when (bucket) {
        TaskBucket.REMINDERS -> stringResource(R.string.hub_create_reminder)
        TaskBucket.PRIORITIES -> stringResource(R.string.hub_add_task)
        else -> stringResource(R.string.hub_create_task)
    }
    val createDueAt = when {
        bucket == TaskBucket.REMINDERS ->
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        !showDayStrip -> null
        daySelection is DayStripSelection.Overdue ->
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        daySelection is DayStripSelection.Day ->
            (daySelection as DayStripSelection.Day).date
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else -> null
    }
    var viewMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    DisposableEffect(bucket) {
        graph.hideWorkspaceBar.value = bucket != TaskBucket.HUB
        onDispose { graph.hideWorkspaceBar.value = false }
    }
    BackHandler(enabled = bucket != TaskBucket.HUB) {
        bucket = TaskBucket.HUB
        filter = "ALL"
        view = "LIST"
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            if (bucket != TaskBucket.HUB) {
                TopAppBar(
                    title = {
                        Text(functionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            bucket = TaskBucket.HUB
                            filter = "ALL"
                            view = "LIST"
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    },
                    actions = {
                        if (bucket.showsViewSwitcher()) {
                            Box {
                                IconButton(onClick = { viewMenu = true }) {
                                    Icon(
                                        Icons.Outlined.ViewList,
                                        contentDescription = stringResource(R.string.change_view),
                                    )
                                }
                                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.view_list)) },
                                        onClick = { view = "LIST"; viewMenu = false },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.ViewList, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.view_calendar)) },
                                        onClick = { view = "CALENDAR"; viewMenu = false },
                                        leadingIcon = {
                                            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.view_board)) },
                                        onClick = { view = "BOARD"; viewMenu = false },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.customize_display))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf(
                                    "ALL" to stringResource(R.string.status_all),
                                    "BACKLOG" to stringResource(R.string.status_backlog),
                                    "TODO" to stringResource(R.string.status_todo),
                                    "IN_PROGRESS" to stringResource(R.string.status_in_progress),
                                    "DONE" to stringResource(R.string.status_done),
                                    "CANCELLED" to stringResource(R.string.status_cancelled),
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            filter = value
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (bucket.allowsCreate()) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = createLabel)
                }
            }
        },
    ) { padding ->
        if (bucket == TaskBucket.HUB) {
            if (vm.loading && vm.tasks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TaskHubScreen(
                    workspace = workspace,
                    userId = user?.id,
                    userName = user?.name,
                    tasks = vm.tasks,
                    work = vm.work,
                    scope = scope,
                    search = search,
                    onScope = { scope = it },
                    onSearch = { search = it },
                    onOpenBucket = {
                        bucket = it
                        daySelection = when (it) {
                            TaskBucket.OVERDUE -> DayStripSelection.Overdue
                            TaskBucket.TODAY -> DayStripSelection.Day(LocalDate.now())
                            TaskBucket.UPCOMING -> DayStripSelection.Day(LocalDate.now().plusDays(1))
                            else -> DayStripSelection.Day(LocalDate.now())
                        }
                    },
                    onOpenList = { list ->
                        vm.selectList(list.id)
                        bucket = TaskBucket.LIST
                        daySelection = DayStripSelection.Day(LocalDate.now())
                    },
                    onCreateTask = { creating = true },
                    onAddList = if (isAdmin) ({ structuring = true }) else null,
                    modifier = Modifier.padding(padding),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!showDayStrip) {
                    Text(
                        stringResource(R.string.sort_by_due),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280),
                    )
                }
                if (showDayStrip) {
                    TaskDayStrip(
                        selection = daySelection,
                        overdueCount = overdueInScope.size,
                        onSelect = { daySelection = it },
                    )
                }
                vm.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (vm.loading && shown.isEmpty()) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                } else when {
                    view == "CALENDAR" && bucket.showsViewSwitcher() ->
                        CalendarView(statusFiltered, onOpen = { editor = it })
                    shown.isEmpty() -> TaskEmptyState(
                        bucket = bucket,
                        createLabel = createLabel,
                        onCreate = if (bucket.allowsCreate()) ({ creating = true }) else null,
                    )
                    view == "BOARD" && bucket.showsViewSwitcher() -> BoardView(
                        statusFiltered,
                        canMove = { canEditTask(it, user?.id) },
                        onOpen = { editor = it },
                        onMove = vm::move,
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shown, key = { it.id }) { task ->
                            TaskCard(task, onClick = { editor = task })
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(editor?.id) {
        editor?.id?.let(vm::loadComments)
    }

    if (structuring) {
        StructureDialog(
            work = vm.work,
            members = vm.members,
            onDismiss = { structuring = false },
            onCreateSpace = { name, isPrivate -> vm.createSpace(name, isPrivate) { structuring = false } },
            onCreateFolder = { spaceId, name -> vm.createFolder(spaceId, name) { structuring = false } },
            onCreateList = { spaceId, folderId, name -> vm.createList(spaceId, folderId, name) { structuring = false } },
            onAddSpaceMember = vm::addSpaceMember,
        )
    }

    if (creating || editor != null) {
        val editing = editor
        TaskEditor(
            initial = editing,
            members = vm.members,
            listId = editing?.listId ?: selectedList?.id,
            currentUserId = user?.id,
            allTasks = vm.tasks,
            comments = if (editing == null) emptyList() else vm.comments,
            subtasks = vm.tasks.filter { it.parentId == editing?.id && editing != null },
            canSave = editing == null || canEditTask(editing, user?.id),
            defaultDueAt = if (editing == null && bucket.allowsCreate()) createDueAt else null,
            onDismiss = { creating = false; editor = null },
            onComment = { body -> editor?.let { vm.comment(it.id, body) } },
            onAddSubtask = { title -> editor?.let { vm.addSubtask(it, title) } },
            onSave = { write, reportError ->
                vm.save(
                    editor,
                    write,
                    onDone = {
                        filter = "ALL"
                        creating = false
                        editor = null
                    },
                    onError = reportError,
                )
            },
            onDelete = editor?.let { task ->
                if (canEditTask(task, user?.id)) {
                    {
                        vm.delete(task.id) {
                            creating = false
                            editor = null
                        }
                    }
                } else {
                    null
                }
            },
        )
    }
}

class TaskViewModel(private val api: WorkHubApi) : ViewModel() {
    var tasks by mutableStateOf<List<TaskDto>>(emptyList())
        private set
    var members by mutableStateOf<List<MemberDto>>(emptyList())
        private set
    var work by mutableStateOf(WorkTree())
        private set
    var comments by mutableStateOf<List<CommentDto>>(emptyList())
        private set
    var selectedListId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun selectList(id: String) {
        selectedListId = id
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                tasks = api.tasks()
                members = api.members()
                work = api.workTree()
                if (selectedListId == null) selectedListId = flattenLists(work).firstOrNull()?.id
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    fun save(existing: TaskDto?, write: TaskWrite, onDone: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (existing == null) api.createTask(write) else api.updateTask(existing.id, write)
                refresh()
                onDone()
            } catch (e: Exception) {
                val message = e.userMessage()
                error = message
                onError(message)
            }
        }
    }

    fun move(task: TaskDto, status: String) {
        save(task, task.write(status)) {}
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteTask(id)
                refresh()
                onDone()
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun loadComments(taskId: String) {
        viewModelScope.launch {
            try {
                comments = api.comments(taskId)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun comment(taskId: String, body: String) {
        viewModelScope.launch {
            try {
                api.addComment(taskId, body)
                comments = api.comments(taskId)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun addSubtask(parent: TaskDto, title: String) {
        viewModelScope.launch {
            try {
                api.createTask(TaskWrite(title = title, listId = parent.listId, parentId = parent.id))
                refresh()
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun createSpace(name: String, isPrivate: Boolean, onDone: () -> Unit) = structure({ api.createSpace(name, isPrivate); Unit }, onDone)
    fun createFolder(spaceId: String, name: String, onDone: () -> Unit) = structure({ api.createFolder(spaceId, name); Unit }, onDone)
    fun createList(spaceId: String, folderId: String?, name: String, onDone: () -> Unit) =
        structure({ api.createWorkList(spaceId, name, folderId); Unit }, onDone)

    fun addSpaceMember(spaceId: String, userId: String) {
        viewModelScope.launch {
            try {
                api.addSpaceMember(spaceId, userId)
                work = api.workTree()
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    private fun structure(call: suspend () -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                call()
                work = api.workTree()
                error = null
                onDone()
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }
}

private fun TaskDto.write(status: String = this.status) = TaskWrite(
    title = title,
    description = description,
    status = status,
    assigneeId = assigneeIds.firstOrNull() ?: assigneeId,
    dueAt = dueAt,
    listId = listId,
    parentId = parentId,
    priority = priority,
    assigneeIds = assigneeIds.ifEmpty { listOfNotNull(assigneeId) },
    startAt = startAt,
    locationUrl = locationUrl,
)

private fun taskFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TaskViewModel(api) as T
    }
}

@Composable
internal fun ChoiceMenu(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color? = null,
    enabled: Boolean = true,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = color ?: MaterialTheme.colorScheme.onSurface
    val hideKeyboard = rememberHideKeyboard()
    Box(modifier) {
        FilterChip(
            selected = selected,
            enabled = enabled,
            onClick = {
                hideKeyboard()
                open = true
            },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = tint) },
            colors = if (color == null) {
                FilterChipDefaults.filterChipColors()
            } else {
                FilterChipDefaults.filterChipColors(
                    containerColor = color.copy(alpha = 0.14f),
                    labelColor = color,
                    iconColor = color,
                    selectedContainerColor = color.copy(alpha = 0.22f),
                    selectedLabelColor = color,
                    selectedLeadingIconColor = color,
                    selectedTrailingIconColor = color,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

@Composable
internal fun MemberPicker(
    label: String,
    members: List<MemberDto>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    currentUserId: String? = null,
    multiple: Boolean = true,
    noneLabel: String? = null,
    onNone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val hideKeyboard = rememberHideKeyboard()
    var query by remember { mutableStateOf("") }
    val selectedNames = members.filter { it.id in selectedIds }.joinToString(", ") { it.name }
    val summary = selectedNames.ifBlank { noneLabel ?: stringResource(R.string.choose_option, label) }
    val filtered = members.filter { member ->
        val text = query.trim()
        text.isBlank() || member.name.contains(text, ignoreCase = true) || member.email.contains(text, ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = open || selectedIds.isNotEmpty(),
            enabled = enabled,
            onClick = {
                if (!enabled) return@FilterChip
                hideKeyboard()
                open = !open
            },
            label = { Text("$label \u00B7 $summary", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (open && enabled) {
            OutlinedField(
                value = query,
                onValue = { query = it },
                label = stringResource(R.string.search_members),
            )
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            ) {
                if (onNone != null && noneLabel != null && query.isBlank()) {
                    TextButton(onClick = onNone, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedIds.isEmpty()) "\u2713 $noneLabel" else noneLabel)
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.search_no_member),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                filtered.forEach { member ->
                    TextButton(
                        onClick = {
                            onToggle(member.id)
                            if (!multiple) open = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val name = if (member.id == currentUserId) {
                            stringResource(R.string.assign_me, member.name)
                        } else {
                            member.name
                        }
                        Text(
                            if (member.id in selectedIds) "\u2713 $name" else name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DateField(
    label: String,
    millis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val useCalendar = configuration.screenHeightDp >= 640 && configuration.screenWidthDp >= 360
    val hideKeyboard = rememberHideKeyboard()
    Box(modifier) {
        OutlinedTextField(
            value = millis?.let { formatDay(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(stringResource(R.string.pick_date)) },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.pick_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!enabled) return@Box
        Box(
            Modifier.matchParentSize().clickable {
                hideKeyboard()
                val initial = millis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    if (useCalendar) R.style.WorkHubCalendarDatePicker else R.style.WorkHubSpinnerDatePicker,
                    { _, year, month, day ->
                        val picked = LocalDate.of(year, month + 1, day)
                        onChange(picked.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            },
        )
        if (millis != null) {
            IconButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_date))
            }
        }
    }
}

@Composable
private fun TaskEmptyState(
    bucket: TaskBucket,
    createLabel: String,
    onCreate: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (bucket) {
                TaskBucket.MINE -> stringResource(R.string.hub_empty_mine_title)
                TaskBucket.REMINDERS -> stringResource(R.string.hub_empty_reminders_title)
                TaskBucket.COMMENTS -> stringResource(R.string.hub_empty_comments_title)
                TaskBucket.PRIORITIES -> stringResource(R.string.hub_empty_priorities_title)
                TaskBucket.OVERDUE -> stringResource(R.string.hub_empty_overdue_title)
                else -> stringResource(R.string.hub_empty_generic_title)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Text(
            when (bucket) {
                TaskBucket.MINE -> stringResource(R.string.hub_empty_mine_body)
                TaskBucket.REMINDERS -> stringResource(R.string.hub_empty_reminders_body)
                TaskBucket.COMMENTS -> stringResource(R.string.hub_empty_comments_body)
                TaskBucket.PRIORITIES -> stringResource(R.string.hub_empty_priorities_body)
                else -> stringResource(R.string.hub_empty_generic_body)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (onCreate != null) {
            SoftPrimaryButton(text = createLabel, onClick = onCreate)
        }
    }
}

@Composable
private fun TaskCard(task: TaskDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(statusLabel(task.status))
                StatusPill(priorityLabel(task.priority))
            }
            val people = task.assigneeNames.ifEmpty { listOfNotNull(task.assigneeName) }
            val peopleLabel = if (people.isEmpty()) {
                stringResource(R.string.unassigned)
            } else {
                stringResource(R.string.assigned_to, people.joinToString(", "))
            }
            val startDay = task.startAt?.let { formatDay(it) }
            val endDay = task.dueAt?.let { formatDay(it) }
            val startLabel = if (startDay != null) stringResource(R.string.start_short, startDay) else null
            val endLabel = if (endDay != null) stringResource(R.string.end_short, endDay) else null
            val subLabel = if (task.subtaskCount > 0) stringResource(R.string.subtask_count, task.subtaskCount) else null
            Text(
                listOfNotNull(peopleLabel, startLabel, endLabel, subLabel).joinToString(" \u00B7 "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BoardView(
    tasks: List<TaskDto>,
    canMove: (TaskDto) -> Boolean,
    onOpen: (TaskDto) -> Unit,
    onMove: (TaskDto, String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("BACKLOG", "TODO", "IN_PROGRESS", "DONE", "CANCELLED").forEach { status ->
            Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusLabel(status), style = MaterialTheme.typography.titleMedium)
                tasks.filter { it.status == status }.forEach { task ->
                    Card(onClick = { onOpen(task) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(task.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (canMove(task)) {
                                TextButton(onClick = { onMove(task, nextStatus(task.status)) }) {
                                    Text(stringResource(R.string.move_to, statusLabel(nextStatus(task.status))))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarView(tasks: List<TaskDto>, onOpen: (TaskDto) -> Unit) {
    val items = remember(tasks) { tasksToCalendarItems(tasks) }
    val byDay = remember(items) { items.groupBy { it.day } }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val dayItems = byDay[selected].orEmpty()
    val undated = tasks.filter { it.dueAt == null && it.startAt == null }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            MonthCalendarPanel(
                month = month,
                selected = selected,
                itemsByDay = byDay,
                onMonthChange = { month = it },
                onSelectDay = {
                    selected = it
                    month = YearMonth.from(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            )
        }
        item {
            CalendarDayAgenda(
                day = selected,
                items = dayItems,
                onOpenTask = onOpen,
                emptyText = stringResource(R.string.calendar_day_empty),
            )
        }
        if (undated.isNotEmpty()) {
            item {
                Text(stringResource(R.string.no_due), style = MaterialTheme.typography.titleMedium)
            }
            items(undated, key = { "undated-${it.id}" }) { task ->
                TaskCard(task, onClick = { onOpen(task) })
            }
        }
    }
}

@Composable
private fun StructureDialog(
    work: WorkTree,
    members: List<MemberDto>,
    onDismiss: () -> Unit,
    onCreateSpace: (String, Boolean) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onCreateList: (String, String?, String) -> Unit,
    onAddSpaceMember: (String, String) -> Unit,
) {
    var kind by remember { mutableStateOf("SPACE") }
    var name by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var spaceId by remember { mutableStateOf(work.spaces.firstOrNull()?.id.orEmpty()) }
    var folderId by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf("") }
    val space = work.spaces.firstOrNull { it.id == spaceId }
    EditorDialog(
        title = stringResource(R.string.structure_title),
        onDismiss = onDismiss,
        onSave = {
            when (kind) {
                "FOLDER" -> onCreateFolder(spaceId, name.trim())
                "LIST" -> onCreateList(spaceId, folderId.ifBlank { null }, name.trim())
                else -> onCreateSpace(name.trim(), isPrivate)
            }
        },
        onDelete = null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "SPACE" to stringResource(R.string.space),
                "FOLDER" to stringResource(R.string.folder),
                "LIST" to stringResource(R.string.list),
            ).forEach { (value, label) ->
                FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
            }
        }
        OutlinedField(name, { name = it }, stringResource(R.string.name))
        if (kind == "SPACE") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.private_label))
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }
        }
        if (kind != "SPACE" && work.spaces.isNotEmpty()) {
            ChoiceField(stringResource(R.string.space), spaceId, work.spaces.map { it.id to it.name }) { spaceId = it }
        }
        if (kind == "LIST" && space != null && space.folders.isNotEmpty()) {
            ChoiceField(
                stringResource(R.string.folder),
                folderId,
                listOf("" to stringResource(R.string.no_folder)) + space.folders.map { it.id to it.name },
            ) {
                folderId = it
            }
        }
        if (kind == "SPACE" && work.spaces.isNotEmpty()) {
            Text(stringResource(R.string.add_space_people), style = MaterialTheme.typography.labelLarge)
            ChoiceField(stringResource(R.string.space), spaceId, work.spaces.map { it.id to it.name }) { spaceId = it }
            MemberPicker(
                label = stringResource(R.string.members),
                members = members,
                selectedIds = if (memberId.isBlank()) emptySet() else setOf(memberId),
                multiple = false,
                onToggle = { memberId = it },
            )
            TextButton(
                onClick = { if (memberId.isNotBlank() && spaceId.isNotBlank()) onAddSpaceMember(spaceId, memberId) },
                enabled = memberId.isNotBlank() && spaceId.isNotBlank(),
            ) {
                Text(stringResource(R.string.add_to_space))
            }
        }
    }
}

@Composable
internal fun OutlinedField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int? = null,
    enabled: Boolean = true,
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines ?: if (singleLine) 1 else 3,
        trailingIcon = if (enabled && value.isNotEmpty()) {
            { TextFieldClearCopyActions(value = value, onClear = { onValue("") }) }
        } else {
            null
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = if (singleLine) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = {
                focus.clearFocus(force = true)
                keyboard?.hide()
            },
        ),
    )
}
