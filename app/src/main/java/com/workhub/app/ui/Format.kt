package com.workhub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.workhub.app.R
import com.workhub.shared.TaskDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM")

fun isToday(millis: Long?): Boolean {
    if (millis == null) return false
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == LocalDate.now(zone)
}

fun formatDay(millis: Long?): String {
    if (millis == null) return ""
    return dayFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
}

private val rangeDayFormatter = DateTimeFormatter.ofPattern("MMM d")
private val rangeMonthDayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun formatDateRange(startMillis: Long?, endMillis: Long?): String? {
    if (startMillis == null && endMillis == null) return null
    val zone = ZoneId.systemDefault()
    val start = startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val end = endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    return when {
        start != null && end != null && start.year == end.year && start.month == end.month ->
            "${start.format(rangeDayFormatter)} – ${end.dayOfMonth}, ${end.year}"
        start != null && end != null && start.year == end.year ->
            "${start.format(rangeDayFormatter)} – ${end.format(rangeMonthDayFormatter)}"
        start != null && end != null ->
            "${start.format(rangeMonthDayFormatter)} – ${end.format(rangeMonthDayFormatter)}"
        start != null -> start.format(rangeMonthDayFormatter)
        else -> end!!.format(rangeMonthDayFormatter)
    }
}

fun formatTime(millis: Long): String =
    timeFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

fun parseDay(text: String): Long? {
    if (text.isBlank()) return null
    val date = LocalDate.parse(text.trim(), dayFormatter)
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun normalizeStatus(status: String?): String = when (status) {
    "DOING" -> "IN_PROGRESS"
    "BACKLOG", "TODO", "IN_PROGRESS", "DONE", "CANCELLED" -> status
    else -> "TODO"
}

fun normalizePriority(priority: String?, isNew: Boolean): String = when (priority) {
    "URGENT", "HIGH", "MEDIUM", "LOW", "NONE" -> priority
    "NORMAL" -> "MEDIUM"
    else -> if (isNew) "NONE" else "MEDIUM"
}

@Composable
fun statusLabel(status: String): String = stringResource(
    when (normalizeStatus(status)) {
        "BACKLOG" -> R.string.status_backlog
        "TODO" -> R.string.status_todo
        "IN_PROGRESS" -> R.string.status_in_progress
        "DONE" -> R.string.status_done
        "CANCELLED" -> R.string.status_cancelled
        else -> R.string.status_todo
    },
)

@Composable
fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> stringResource(R.string.role_admin)
    "SUPERVISOR" -> stringResource(R.string.role_supervisor)
    "MEMBER" -> stringResource(R.string.role_member)
    else -> role
}

fun canManageContent(role: String?): Boolean = role == "ADMIN" || role == "SUPERVISOR"

fun canEditTask(task: TaskDto, userId: String?): Boolean {
    if (userId.isNullOrBlank()) return false
    val assignees = task.assigneeIds.ifEmpty { listOfNotNull(task.assigneeId) }
    if (assignees.isEmpty()) return task.creatorId == userId
    return userId in assignees
}

@Composable
fun priorityLabel(priority: String): String = stringResource(
    when (normalizePriority(priority, isNew = false)) {
        "URGENT" -> R.string.priority_urgent
        "HIGH" -> R.string.priority_high
        "LOW" -> R.string.priority_low
        "NONE" -> R.string.priority_none
        else -> R.string.priority_medium
    },
)

fun nextStatus(status: String): String = when (normalizeStatus(status)) {
    "BACKLOG" -> "TODO"
    "TODO" -> "IN_PROGRESS"
    "IN_PROGRESS" -> "DONE"
    "DONE" -> "CANCELLED"
    else -> "BACKLOG"
}
