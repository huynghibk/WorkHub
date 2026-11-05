package com.workhub.app.data

import com.workhub.app.data.local.ChannelEntity
import com.workhub.app.data.local.MessageEntity
import com.workhub.app.data.local.WorkHubDb
import com.workhub.shared.ChannelDto
import com.workhub.shared.MessageDto
import com.workhub.shared.MessageWrite
import com.workhub.shared.ReactionDto
import com.workhub.shared.ReactionEvent
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Room-backed chat: channel list + message history stay readable offline, and messages
 * typed offline are queued as "sending" until [push] uploads them.
 */
class ChatStore(
    private val db: WorkHubDb,
    private val api: WorkHubApi,
    private val session: SessionStore,
    private val requestSync: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pushLock = Mutex()

    private val _sendError = MutableStateFlow<String?>(null)
    /** Reason the server gave for the last refused message (the message stays, marked failed). */
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<List<ChannelDto>> = session.state.map { it.workspace?.id }.distinctUntilChanged().flatMapLatest { ws ->
        if (ws == null) {
            flowOf(emptyList())
        } else {
            db.chat().observeChannels(ws).map { rows -> rows.map { json.decodeFromString(ChannelDto.serializer(), it.payload) } }
        }
    }

    /** Ids of messages still waiting to be uploaded (shown with a "sending" clock). */
    val pendingIds: Flow<Set<String>> = db.chat().observePendingIds().map { it.toSet() }

    /** Ids of messages the server refused (shown with a red error mark). */
    val failedIds: Flow<Set<String>> = db.chat().observeFailedIds().map { it.toSet() }

    fun messages(channelId: String, parentId: String?): Flow<List<MessageDto>> =
        db.chat().observeMessages(channelId, parentId).map { rows -> rows.map { decode(it.payload) } }

    suspend fun refreshChannels(): List<ChannelDto> {
        val ws = session.current().workspace?.id ?: return emptyList()
        val list = api.channels()
        db.chat().replaceChannels(
            ws,
            list.mapIndexed { index, channel ->
                ChannelEntity(channel.id, ws, index, json.encodeToString(ChannelDto.serializer(), channel))
            },
        )
        return list
    }

    suspend fun refreshMessages(channelId: String, parentId: String?) {
        val ws = session.current().workspace?.id ?: return
        val list = api.messages(channelId, parentId)
        val pending = db.chat().pendingIds().toSet()
        db.chat().upsertAll(list.filter { it.id !in pending }.map { entity(ws, it) })
    }

    suspend fun send(
        channelId: String,
        body: String,
        parentId: String?,
        locationUrl: String?,
        taskId: String? = null,
        noteId: String? = null,
    ) {
        val current = session.current()
        val ws = current.workspace?.id ?: throw ApiException("Hãy chọn workspace")
        val user = current.user ?: throw ApiException("Bạn cần đăng nhập lại")
        val write = MessageWrite(body.trim(), parentId, locationUrl, taskId, noteId)
        val message = MessageDto(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            senderId = user.id,
            senderName = user.name,
            body = write.body,
            createdAt = api.serverNow(),
            parentId = parentId,
            locationUrl = locationUrl,
            taskId = taskId,
            noteId = noteId,
        )
        db.chat().upsert(
            entity(ws, message).copy(
                pendingWrite = json.encodeToString(MessageWrite.serializer(), write),
                syncPending = true,
            ),
        )
        bumpReplyCount(parentId)
        requestSync()
    }

    suspend fun push() = pushLock.withLock {
        for (row in db.chat().pending()) {
            val sent = row.pendingWrite ?: continue
            val write = json.decodeFromString(MessageWrite.serializer(), sent)
            // The server rejects a share whose task/note it has not received yet.
            if (write.taskId?.let { db.tasks().get(it)?.syncPending } == true) continue
            if (write.noteId?.let { db.notes().get(it)?.syncPending } == true) continue
            try {
                val saved = api.putMessage(row.channelId, row.id, write, row.workspaceId)
                db.chat().markSynced(row.id, sent, json.encodeToString(MessageDto.serializer(), saved), saved.createdAt)
            } catch (e: ApiException) {
                if (e.status !in PERMANENT) throw e
                db.chat().markFailed(row.id)
                _sendError.value = e.message
            }
        }
    }

    /** Still uploading. Refused messages are not counted: retrying them needs a tap. */
    suspend fun sendingCount(): Int = db.chat().pendingIds().size

    suspend fun retry(id: String) {
        _sendError.value = null
        db.chat().retry(id)
        requestSync()
    }

    suspend fun discard(id: String) = db.chat().remove(id)

    /** Keeps the offline history bounded; unsent messages are never dropped. */
    suspend fun pruneOlderThan(cutoff: Long) = db.chat().deleteOlderThan(cutoff)

    /** Messages that would be lost on logout: still sending or refused. */
    suspend fun pendingCount(): Int = db.chat().unsentCount()

    /** One reaction per person. An empty emoji clears it. */
    suspend fun applyReaction(event: ReactionEvent) {
        val row = db.chat().get(event.messageId) ?: return
        val dto = decode(row.payload)
        val rest = dto.reactions.filter { it.userId != event.userId }
        val next = if (event.emoji.isBlank()) rest else rest + ReactionDto(event.userId, event.emoji)
        db.chat().upsert(row.copy(payload = json.encodeToString(MessageDto.serializer(), dto.copy(reactions = next))))
    }

    /** Realtime "message" event: store it unless it is our own upload still in flight. */
    suspend fun apply(message: MessageDto) {
        val ws = session.current().workspace?.id ?: return
        val local = db.chat().get(message.id)
        if (local?.syncPending == true || local?.failed == true) return
        val fresh = entity(ws, message)
        if (local?.payload == fresh.payload) return
        db.chat().upsert(fresh)
        if (local == null) bumpReplyCount(message.parentId)
    }

    private suspend fun bumpReplyCount(parentId: String?) {
        val parent = parentId?.let { db.chat().get(it) } ?: return
        val dto = decode(parent.payload)
        db.chat().upsert(parent.copy(payload = json.encodeToString(MessageDto.serializer(), dto.copy(replyCount = dto.replyCount + 1))))
    }

    private fun entity(ws: String, message: MessageDto) = MessageEntity(
        id = message.id,
        workspaceId = ws,
        channelId = message.channelId,
        parentId = message.parentId,
        createdAt = message.createdAt,
        payload = json.encodeToString(MessageDto.serializer(), message),
    )

    private fun decode(raw: String) = json.decodeFromString(MessageDto.serializer(), raw)

    private companion object {
        val PERMANENT = setOf(400, 403, 404, 409, 410, 422)
    }
}
