package com.workhub.app.data

import com.workhub.app.data.local.NoteEntity
import com.workhub.app.data.local.TaskEntity
import com.workhub.app.data.local.WorkHubDb
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite
import com.workhub.shared.UserDto
import com.workhub.shared.WsEvent
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Room is the single source of truth for tasks and notes: writes land locally first,
 * then [sync] uploads pending rows and pulls the server copy. Realtime events go
 * straight into Room through [applyEvent].
 */
class OfflineStore(
    private val db: WorkHubDb,
    private val api: WorkHubApi,
    private val session: SessionStore,
    private val chat: ChatStore,
    private val requestSync: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _syncError = MutableStateFlow<String?>(null)
    /** Last change the server refused (it has been rolled back locally). */
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val workspaceId = session.state.map { it.workspace?.id }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: Flow<List<TaskDto>> = workspaceId.flatMapLatest { ws ->
        if (ws == null) {
            flowOf(emptyList())
        } else {
            db.tasks().observe(ws).map { rows ->
                val list = rows.map { decodeTask(it.payload) }
                val counts = list.groupingBy { it.parentId }.eachCount()
                list.map { it.copy(subtaskCount = counts[it.id] ?: 0) }.sortedByDescending { it.updatedAt }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: Flow<List<NoteDto>> = workspaceId.flatMapLatest { ws ->
        if (ws == null) {
            flowOf(emptyList())
        } else {
            db.notes().observe(ws).map { rows ->
                rows.map { decodeNote(it.payload) }.sortedByDescending { it.updatedAt }
            }
        }
    }

    suspend fun saveTask(
        existing: TaskDto?,
        write: TaskWrite,
        memberNames: Map<String, String> = emptyMap(),
        listNames: Map<String, String> = emptyMap(),
    ) {
        val (ws, user) = requireSession()
        val id = existing?.id ?: UUID.randomUUID().toString()
        val now = api.serverNow()
        val row = db.tasks().get(id)
        val base = row?.payload?.let(::decodeTask) ?: existing
        val previous = row?.pendingWrite?.let { json.decodeFromString(TaskWrite.serializer(), it) }
        var merged = write.copy(updatedAt = now)
        // A later plain edit must not discard link changes that are still waiting to upload.
        if (previous?.updateLinks == true && !merged.updateLinks) {
            merged = merged.copy(updateLinks = true, parentId = previous.parentId, childIds = previous.childIds)
        }
        val dto = optimisticTask(base, id, merged, user, memberNames, listNames, now)
        db.tasks().upsert(
            TaskEntity(
                id = id,
                workspaceId = row?.workspaceId ?: ws,
                parentId = dto.parentId,
                payload = json.encodeToString(TaskDto.serializer(), dto),
                pendingWrite = json.encodeToString(TaskWrite.serializer(), merged),
                syncPending = true,
            ),
        )
        requestSync()
    }

    suspend fun deleteTask(id: String) {
        val row = db.tasks().get(id) ?: return
        db.tasks().upsert(row.copy(deleted = true, syncPending = true))
        db.tasks().hideChildren(id)
        requestSync()
    }

    suspend fun saveNote(existing: NoteDto?, write: NoteWrite) {
        val (ws, user) = requireSession()
        val id = existing?.id ?: UUID.randomUUID().toString()
        val now = api.serverNow()
        val row = db.notes().get(id)
        val base = row?.payload?.let(::decodeNote) ?: existing
        val merged = write.copy(updatedAt = now)
        val dto = NoteDto(
            id = id,
            title = merged.title.trim(),
            body = merged.body,
            authorId = base?.authorId ?: user.id,
            authorName = base?.authorName ?: user.name,
            updatedAt = now,
            dueAt = merged.dueAt,
            locationUrl = merged.locationUrl,
        )
        db.notes().upsert(
            NoteEntity(
                id = id,
                workspaceId = row?.workspaceId ?: ws,
                payload = json.encodeToString(NoteDto.serializer(), dto),
                pendingWrite = json.encodeToString(NoteWrite.serializer(), merged),
                syncPending = true,
            ),
        )
        requestSync()
    }

    suspend fun deleteNote(id: String) {
        val row = db.notes().get(id) ?: return
        db.notes().upsert(row.copy(deleted = true, syncPending = true))
        requestSync()
    }

    /** Upload pending rows (all workspaces), then refresh the current workspace from the server. */
    suspend fun sync() = mutex.withLock {
        val current = session.current()
        if (current.token == null) return@withLock
        _syncError.value = null
        chat.push()
        // Edits made while uploading are picked up by the next round.
        for (round in 1..3) {
            val tasks = db.tasks().pending()
            val notes = db.notes().pending()
            if (tasks.isEmpty() && notes.isEmpty()) break
            tasks.forEach { pushTask(it) }
            notes.forEach { pushNote(it) }
        }
        // Messages sharing an item that was still unsynced were held back by the first pass.
        chat.push()
        val ws = current.workspace?.id ?: return@withLock
        val serverTasks = api.tasks(ws)
        val serverNotes = api.notes(ws)
        db.tasks().replaceSynced(ws, serverTasks.map { taskEntity(ws, it) })
        db.notes().replaceSynced(ws, serverNotes.map { noteEntity(ws, it) })
    }

    suspend fun applyEvent(event: WsEvent) {
        val ws = session.current().workspace?.id ?: return
        when (event.type) {
            "task_upsert" -> {
                val task = event.task ?: return
                val local = db.tasks().get(task.id)
                // A pending local edit wins until the server answers its own upload.
                if (local?.syncPending == true) return
                val fresh = taskEntity(ws, task)
                if (local != null && !local.deleted && local.payload == fresh.payload) return
                db.tasks().upsert(fresh)
            }
            "task_delete" -> event.deletedId?.let { db.tasks().removeTree(it) }
            "note_upsert" -> {
                val note = event.note ?: return
                val local = db.notes().get(note.id)
                if (local?.syncPending == true) return
                val fresh = noteEntity(ws, note)
                if (local != null && !local.deleted && local.payload == fresh.payload) return
                db.notes().upsert(fresh)
            }
            "note_delete" -> event.deletedId?.let { db.notes().remove(it) }
            "message" -> event.message?.let {
                chat.apply(it)
                chat.ensureChannel(it.channelId)
            }
            "reaction" -> event.reaction?.let { chat.applyReaction(it) }
            "channel" -> chat.refreshChannels()
            "message_edit", "message_delete" -> event.message?.let { chat.apply(it) }
            "typing", "read", "presence", "pong" -> Unit
        }
    }

    suspend fun pendingCount(): Int = db.tasks().pending().size + db.notes().pending().size + chat.pendingCount()

    suspend fun hasPending(): Boolean =
        db.tasks().pending().isNotEmpty() || db.notes().pending().isNotEmpty() || chat.sendingCount() > 0

    suspend fun clear() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private suspend fun pushTask(row: TaskEntity) {
        try {
            if (row.deleted) {
                api.deleteTask(row.id, row.workspaceId)
                db.tasks().removeTree(row.id)
                return
            }
            val sent = row.pendingWrite ?: return db.tasks().dropPending(row.id)
            val saved = api.putTask(row.id, json.decodeFromString(TaskWrite.serializer(), sent), row.workspaceId)
            db.tasks().markSynced(row.id, sent, json.encodeToString(TaskDto.serializer(), saved), saved.parentId)
        } catch (e: ApiException) {
            when {
                e.status == 410 || (e.status == 404 && row.deleted) -> db.tasks().removeTree(row.id)
                e.status in PERMANENT -> {
                    db.tasks().dropPending(row.id)
                    _syncError.value = e.message
                }
                else -> throw e
            }
        }
    }

    private suspend fun pushNote(row: NoteEntity) {
        try {
            if (row.deleted) {
                api.deleteNote(row.id, row.workspaceId)
                db.notes().remove(row.id)
                return
            }
            val sent = row.pendingWrite ?: return db.notes().dropPending(row.id)
            val saved = api.putNote(row.id, json.decodeFromString(NoteWrite.serializer(), sent), row.workspaceId)
            db.notes().markSynced(row.id, sent, json.encodeToString(NoteDto.serializer(), saved))
        } catch (e: ApiException) {
            when {
                e.status == 410 || (e.status == 404 && row.deleted) -> db.notes().remove(row.id)
                e.status in PERMANENT -> {
                    db.notes().dropPending(row.id)
                    _syncError.value = e.message
                }
                else -> throw e
            }
        }
    }

    private suspend fun requireSession(): Pair<String, UserDto> {
        val current = session.current()
        val ws = current.workspace?.id ?: throw ApiException("Hãy chọn workspace")
        val user = current.user ?: throw ApiException("Bạn cần đăng nhập lại")
        return ws to user
    }

    private fun taskEntity(ws: String, task: TaskDto) =
        TaskEntity(task.id, ws, task.parentId, json.encodeToString(TaskDto.serializer(), task))

    private fun noteEntity(ws: String, note: NoteDto) =
        NoteEntity(note.id, ws, json.encodeToString(NoteDto.serializer(), note))

    private fun decodeTask(raw: String) = json.decodeFromString(TaskDto.serializer(), raw)

    private fun decodeNote(raw: String) = json.decodeFromString(NoteDto.serializer(), raw)

    private companion object {
        /** Server refused the change itself; retrying would never succeed. */
        val PERMANENT = setOf(400, 403, 404, 409, 422)
    }
}

/** What the UI shows until the server returns the authoritative copy. */
private fun optimisticTask(
    base: TaskDto?,
    id: String,
    write: TaskWrite,
    user: UserDto,
    names: Map<String, String>,
    listNames: Map<String, String>,
    now: Long,
): TaskDto {
    val ids = write.assigneeIds.ifEmpty { listOfNotNull(write.assigneeId) }
    val knownNames = if (base != null && base.assigneeIds == ids) base.assigneeNames else ids.mapNotNull { names[it] }
    // Server only moves lists on create and re-parents only when updateLinks is set.
    val listId = if (base == null) write.listId else base.listId
    val parentId = if (base == null || write.updateLinks) write.parentId else base.parentId
    return TaskDto(
        id = id,
        title = write.title.trim(),
        description = write.description,
        status = write.status,
        assigneeId = ids.firstOrNull(),
        assigneeName = knownNames.firstOrNull(),
        creatorId = base?.creatorId ?: user.id,
        creatorName = base?.creatorName ?: user.name,
        dueAt = write.dueAt,
        updatedAt = now,
        listId = listId,
        parentId = parentId?.ifBlank { null },
        priority = write.priority,
        listName = base?.listName?.ifBlank { null } ?: listId?.let(listNames::get).orEmpty(),
        assigneeIds = ids,
        assigneeNames = knownNames,
        startAt = write.startAt,
        locationUrl = write.locationUrl,
        commentCount = base?.commentCount ?: 0,
    )
}
