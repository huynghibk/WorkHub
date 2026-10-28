package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.NoteDto
import com.workhub.shared.TaskDto
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

data class CalendarItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val day: LocalDate,
    val kind: Kind,
    val task: TaskDto? = null,
    val note: NoteDto? = null,
) {
    enum class Kind { TASK, NOTE }
}

fun taskCalendarDay(task: TaskDto, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val millis = task.dueAt ?: task.startAt ?: return null
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

fun noteCalendarDay(note: NoteDto, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val millis = note.dueAt ?: return null
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

fun tasksToCalendarItems(tasks: List<TaskDto>): List<CalendarItem> =
    tasks.mapNotNull { task ->
        val day = taskCalendarDay(task) ?: return@mapNotNull null
        CalendarItem(
            id = "task-${task.id}",
            title = task.title,
            subtitle = statusLabelPlain(task.status),
            day = day,
            kind = CalendarItem.Kind.TASK,
            task = task,
        )
    }

fun notesToCalendarItems(notes: List<NoteDto>): List<CalendarItem> =
    notes.mapNotNull { note ->
        val day = noteCalendarDay(note) ?: return@mapNotNull null
        CalendarItem(
            id = "note-${note.id}",
            title = note.title,
            subtitle = note.authorName,
            day = day,
            kind = CalendarItem.Kind.NOTE,
            note = note,
        )
    }

private fun statusLabelPlain(status: String): String = when (normalizeStatus(status)) {
    "BACKLOG" -> "Backlog"
    "TODO" -> "To do"
    "IN_PROGRESS" -> "In progress"
    "DONE" -> "Done"
    "CANCELLED" -> "Cancelled"
    else -> status
}

@Composable
fun MonthCalendarPanel(
    month: YearMonth,
    selected: LocalDate,
    itemsByDay: Map<LocalDate, List<CalendarItem>>,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val title = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    val firstDow = DayOfWeek.MONDAY
    val days = remember(month) { monthGrid(month, firstDow) }
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().sortedBy { (it.value + 6) % 7 }.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B7280),
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    val inMonth = date.month == month.month
                    val dayItems = itemsByDay[date].orEmpty()
                    val isSelected = date == selected
                    val isToday = date == today
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> Color(0xFFEEF2FF)
                                    isToday -> Color(0xFFF3F4F6)
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color(0xFF4F46E5) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelectDay(date) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = when {
                                isSelected -> Color(0xFF4F46E5)
                                !inMonth -> Color(0xFFD1D5DB)
                                else -> Color(0xFF111827)
                            },
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (dayItems.any { it.kind == CalendarItem.Kind.TASK }) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4F46E5)))
                            }
                            if (dayItems.any { it.kind == CalendarItem.Kind.NOTE }) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun monthGrid(month: YearMonth, firstDow: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate> {
    val first = month.atDay(1)
    val startOffset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val start = first.minusDays(startOffset.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

@Composable
fun DateRangePickerDialog(
    startMillis: Long?,
    endMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long?, endMillis: Long?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var draftStart by remember {
        mutableStateOf(startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }
    var draftEnd by remember {
        mutableStateOf(endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }
    var pickingEnd by remember { mutableStateOf(startMillis != null && endMillis != null) }
    var month by remember {
        mutableStateOf(
            YearMonth.from(draftStart ?: draftEnd ?: LocalDate.now()),
        )
    }
    val locale = Locale.getDefault()
    val title = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    val days = remember(month) { monthGrid(month, DayOfWeek.SUNDAY) }
    val rangeStart = listOfNotNull(draftStart, draftEnd).minOrNull()
    val rangeEnd = listOfNotNull(draftStart, draftEnd).maxOrNull()
    val accent = Color(0xFF3B5BDB)
    val accentSoft = Color(0xFFD4DCFF)

    fun onDayClick(date: LocalDate) {
        when {
            draftStart == null || (draftStart != null && draftEnd != null && !pickingEnd) -> {
                draftStart = date
                draftEnd = null
                pickingEnd = true
            }
            pickingEnd -> {
                if (date.isBefore(draftStart)) {
                    draftEnd = draftStart
                    draftStart = date
                } else {
                    draftEnd = date
                }
                pickingEnd = false
            }
            else -> {
                draftStart = date
                draftEnd = null
                pickingEnd = true
            }
        }
        month = YearMonth.from(date)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                        }
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    DayOfWeek.values().sortedBy { it.value % 7 }.forEach { dow ->
                        Text(
                            dow.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9CA3AF),
                        )
                    }
                }
                days.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            val inMonth = date.month == month.month
                            val isStart = date == rangeStart
                            val isEnd = date == rangeEnd
                            val inRange = rangeStart != null && rangeEnd != null &&
                                !date.isBefore(rangeStart) && !date.isAfter(rangeEnd)
                            val isEdge = isStart || isEnd
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(vertical = 2.dp)
                                    .then(
                                        if (inRange && rangeStart != rangeEnd) {
                                            val shape = when {
                                                isStart && isEnd -> RoundedCornerShape(50)
                                                isStart -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                                                isEnd -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                                                else -> RoundedCornerShape(0)
                                            }
                                            Modifier.background(accentSoft, shape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { onDayClick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isEdge) accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = when {
                                            isEdge -> Color.White
                                            !inMonth -> Color(0xFFD1D5DB)
                                            else -> Color(0xFF111827)
                                        },
                                        fontWeight = if (isEdge) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = when {
                        draftStart != null && draftEnd != null ->
                            formatDateRange(
                                draftStart!!.atStartOfDay(zone).toInstant().toEpochMilli(),
                                draftEnd!!.atStartOfDay(zone).toInstant().toEpochMilli(),
                            ).orEmpty()
                        draftStart != null -> stringResource(R.string.task_set_end)
                        else -> stringResource(R.string.task_set_start)
                    },
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            draftStart = null
                            draftEnd = null
                            pickingEnd = false
                        },
                    ) {
                        Text(stringResource(R.string.task_clear_dates), color = Color(0xFFDC2626))
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                        TextButton(
                            onClick = {
                                val start = draftStart?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                                val end = (draftEnd ?: draftStart)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                                onConfirm(start, end)
                            },
                        ) {
                            Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayAgenda(
    day: LocalDate,
    items: List<CalendarItem>,
    onOpenTask: (TaskDto) -> Unit,
    onOpenNote: (NoteDto) -> Unit = {},
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val header = remember(day) {
        day.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.getDefault()))
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(header, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (items.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEach { item ->
                Card(
                    onClick = {
                        when (item.kind) {
                            CalendarItem.Kind.TASK -> item.task?.let(onOpenTask)
                            CalendarItem.Kind.NOTE -> item.note?.let(onOpenNote)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.kind == CalendarItem.Kind.TASK) Icons.Filled.TaskAlt else Icons.Filled.EditNote,
                            contentDescription = null,
                            tint = if (item.kind == CalendarItem.Kind.TASK) Color(0xFF4F46E5) else Color(0xFFF59E0B),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.subtitle, color = Color(0xFF6B7280), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            if (item.kind == CalendarItem.Kind.TASK) stringResource(R.string.tab_tasks)
                            else stringResource(R.string.tab_notes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9CA3AF),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceCalendarScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: WorkspaceCalendarViewModel = viewModel(factory = workspaceCalendarFactory(graph.api))
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val items = remember(vm.tasks, vm.notes) {
        tasksToCalendarItems(vm.tasks) + notesToCalendarItems(vm.notes)
    }
    val byDay = remember(items) { items.groupBy { it.day } }
    val dayItems = byDay[selected].orEmpty().sortedBy { it.title }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_calendar), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                    onOpenTask = {},
                    onOpenNote = {},
                    emptyText = stringResource(R.string.calendar_day_empty),
                )
            }
            if (vm.error != null) {
                item { Text(vm.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

class WorkspaceCalendarViewModel(private val api: WorkHubApi) : ViewModel() {
    var tasks by mutableStateOf<List<TaskDto>>(emptyList())
        private set
    var notes by mutableStateOf<List<NoteDto>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            error = null
            runCatching {
                tasks = api.tasks()
                notes = api.notes()
            }.onFailure { error = it.userMessage() }
        }
    }
}

private fun workspaceCalendarFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WorkspaceCalendarViewModel(api) as T
    }
}
