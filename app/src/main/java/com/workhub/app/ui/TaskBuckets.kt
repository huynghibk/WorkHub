package com.workhub.app.ui

import com.workhub.shared.SpaceNode
import com.workhub.shared.TaskDto
import com.workhub.shared.WorkTree
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TaskScope {
    ALL,
    MINE,
    PERSONAL,
    TEAM,
    MANAGED,
}

enum class TaskBucket {
    HUB,
    TODAY,
    OVERDUE,
    PRIORITIES,
    UPCOMING,
    MINE,
    REMINDERS,
    COMMENTS,
    LIST,
}

/** Date strip only for day-based task views — not Reminders / Comments / Priorities / My Tasks. */
fun TaskBucket.showsDayStrip(): Boolean = when (this) {
    TaskBucket.TODAY, TaskBucket.OVERDUE, TaskBucket.UPCOMING, TaskBucket.LIST -> true
    else -> false
}

fun TaskBucket.showsViewSwitcher(): Boolean = when (this) {
    TaskBucket.TODAY, TaskBucket.OVERDUE, TaskBucket.UPCOMING, TaskBucket.LIST, TaskBucket.MINE -> true
    else -> false
}

fun TaskBucket.allowsCreate(): Boolean = this != TaskBucket.COMMENTS && this != TaskBucket.HUB

data class ListChoice(
    val id: String,
    val label: String,
    val spaceId: String,
    val spaceName: String,
    val folderId: String?,
    val isPrivate: Boolean,
)

fun flattenLists(work: WorkTree): List<ListChoice> = work.spaces.flatMap { space ->
    val loose = space.lists.map {
        ListChoice(it.id, "${space.name} / ${it.name}", space.id, space.name, null, space.isPrivate)
    }
    val nested = space.folders.flatMap { folder ->
        folder.lists.map {
            ListChoice(
                it.id,
                "${space.name} / ${folder.name} / ${it.name}",
                space.id,
                space.name,
                folder.id,
                space.isPrivate,
            )
        }
    }
    loose + nested
}

fun spaceByListId(work: WorkTree, listId: String?): SpaceNode? {
    if (listId == null) return null
    return work.spaces.firstOrNull { space ->
        space.lists.any { it.id == listId } ||
            space.folders.any { folder -> folder.lists.any { it.id == listId } }
    }
}

fun isOpenTask(task: TaskDto): Boolean {
    val status = normalizeStatus(task.status)
    return status != "DONE" && status != "CANCELLED"
}

fun isAssignedTo(task: TaskDto, userId: String?): Boolean {
    if (userId.isNullOrBlank()) return false
    val ids = task.assigneeIds.ifEmpty { listOfNotNull(task.assigneeId) }
    return userId in ids
}

fun taskDueDate(task: TaskDto): LocalDate? =
    task.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

fun applyScope(
    tasks: List<TaskDto>,
    scope: TaskScope,
    userId: String?,
    work: WorkTree,
    isAdmin: Boolean,
): List<TaskDto> {
    val privateListIds = flattenLists(work).filter { it.isPrivate }.map { it.id }.toSet()
    val teamListIds = flattenLists(work).filter { !it.isPrivate }.map { it.id }.toSet()
    return tasks.filter { task ->
        when (scope) {
            TaskScope.ALL -> true
            TaskScope.MINE -> isAssignedTo(task, userId) || task.creatorId == userId
            TaskScope.PERSONAL -> task.listId != null && task.listId in privateListIds
            TaskScope.TEAM -> task.listId == null || task.listId in teamListIds
            TaskScope.MANAGED -> if (isAdmin) true else task.creatorId == userId
        }
    }
}

fun applyBucket(tasks: List<TaskDto>, bucket: TaskBucket, userId: String?, today: LocalDate = LocalDate.now()): List<TaskDto> {
    val roots = tasks.filter { it.parentId == null }
    return when (bucket) {
        TaskBucket.HUB, TaskBucket.LIST -> roots
        TaskBucket.TODAY -> roots.filter { task ->
            isOpenTask(task) && taskDueDate(task) == today
        }
        TaskBucket.OVERDUE -> roots.filter { task ->
            isOpenTask(task) && (taskDueDate(task)?.isBefore(today) == true)
        }
        TaskBucket.PRIORITIES -> roots.filter { task ->
            isOpenTask(task) &&
                isAssignedTo(task, userId) &&
                normalizePriority(task.priority, isNew = false) in setOf("URGENT", "HIGH")
        }
        TaskBucket.UPCOMING -> roots.filter { task ->
            isOpenTask(task) && (taskDueDate(task)?.isAfter(today) == true)
        }
        TaskBucket.MINE -> roots.filter { task ->
            isOpenTask(task) && isAssignedTo(task, userId)
        }
        TaskBucket.REMINDERS -> roots.filter { task ->
            isOpenTask(task) && task.dueAt != null && (isAssignedTo(task, userId) || task.creatorId == userId)
        }
        TaskBucket.COMMENTS -> emptyList() // comment feed chưa có API count; giữ empty state
    }
}

fun pinChoices(lists: List<ListChoice>, selectedId: String?, limit: Int): List<ListChoice> {
    if (lists.size <= limit) return lists
    val selected = lists.firstOrNull { it.id == selectedId }
    val head = lists.take(limit).toMutableList()
    if (selected != null && head.none { it.id == selected.id }) {
        head[head.lastIndex] = selected
    }
    return head
}
