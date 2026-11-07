package com.workhub.app.data

import com.workhub.app.data.local.ChannelEntity
import com.workhub.app.data.local.MessageEntity
import com.workhub.app.data.local.MessagePeek
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

    /** Newest root message per channel, for the conversation list. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val latest: Flow<Map<String, MessageDto>> =
        session.state.map { it.workspace?.id }.distinctUntilChanged().flatMapLatest { ws ->
            if (ws == null) {
                flowOf(emptyMap())
            } else {
                db.chat().observeRoots(ws).map { rows ->
                    val byChannel = linkedMapOf<String, MessageDto>()
                    rows.forEach { row ->
                        if (row.channelId !in byChannel) byChannel[row.channelId] = decode(row.payload)
                    }
                    byChannel
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val peeks: Flow<List<MessagePeek>> =
        session.state.map { it.workspace?.id }.distinctUntilChanged().flatMapLatest { ws ->
            if (ws == null) {
                flowOf(emptyList())
            } else {
                db.chat().observeRoots(ws).map { rows ->
                    rows.map { row -> MessagePeek(row.channelId, row.createdAt, decode(row.payload).senderId) }
                }
            }
        }

    /** Ids of messages still waiting to be uploaded (shown with a "sending" clock). */
    val pendingIds: Flow<Set<String>> = db.chat().observePendingIds().map { it.toSet() }

    /** Ids of messages the server refused (shown with a red error mark). */
    val failedIds: Flow<Set<String>> = db.chat().observeFailedIds().map { it.toSet() }

    fun messages(channelId: String, parentId: String?): Flow<List<MessageDto>> =
        db.chat().observeMessages(channelId, parentId).map { rows -> rows.map { decode(it.payload) } }

    suspend fun dropConversation(channelId: String) {
        db.chat().deleteChannel(channelId)
        db.chat().deleteChannelMessages(channelId)
    }

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

    suspend fun refreshMessages(channelId: String, parentId: String?, catchUp: Boolean = false) {
        val ws = session.current().workspace?.id ?: return
        val after = if (catchUp) db.chat().maxCreatedAt(channelId, parentId) else null
        val list = if (after != null && after > 0) {
            api.messages(channelId, parentId, after = after, limit = 200)
        } else {
            api.messages(channelId, parentId, limit = 50)
        }
        val pending = db.chat().pendingIds().toSet()
        db.chat().upsertAll(list.filter { it.id !in pending }.map { entity(ws, it) })
    }

    suspend fun loadOlderMessages(channelId: String, parentId: String?, before: Long): Boolean {
        val ws = session.current().workspace?.id ?: return false
        val list = api.messages(channelId, parentId, before = before, limit = 50)
        if (list.isEmpty()) return false
        val pending = db.chat().pendingIds().toSet()
        db.chat().upsertAll(list.filter { it.id !in pending }.map { entity(ws, it) })
        return list.size >= 50
    }

    suspend fun applyEdit(message: MessageDto) = apply(message)

    suspend fun applyDelete(message: MessageDto) = apply(message)

    private suspend fun uploadAttachment(write: MessageWrite): MessageWrite {
        val path = write.localPath?.takeIf { it.isNotBlank() } ?: return write.copy(localPath = null)
        val file = java.io.File(path)
        if (!file.exists()) throw ApiException("Không còn tệp trên máy", 400)
        val uploaded = api.upload(file, write.attachmentName ?: file.name)
        return write.copy(
            localPath = null,
            attachmentUrl = uploaded.url,
            attachmentName = uploaded.name,
            attachmentKind = uploaded.kind,
        )
    }

    suspend fun send(
        channelId: String,
        body: String,
        parentId: String?,
        locationUrl: String?,
        taskId: String? = null,
        noteId: String? = null,
        localPath: String? = null,
        attachmentName: String? = null,
        attachmentKind: String? = null,
        replyToId: String? = null,
        replyToName: String? = null,
        replyToBody: String? = null,
        messageId: String? = null,
    ) {
        val current = session.current()
        val ws = current.workspace?.id ?: throw ApiException("Hãy chọn workspace")
        val user = current.user ?: throw ApiException("Bạn cần đăng nhập lại")
        val write = MessageWrite(
            body.trim(), parentId, locationUrl, taskId, noteId,
            attachmentName = attachmentName,
            attachmentKind = attachmentKind,
            replyToId = replyToId,
            replyToName = replyToName,
            replyToBody = replyToBody,
            localPath = localPath,
        )
        val message = MessageDto(
            id = messageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            channelId = channelId,
            senderId = user.id,
            senderName = user.name,
            body = write.body,
            createdAt = api.serverNow(),
            parentId = parentId,
            locationUrl = locationUrl,
            taskId = taskId,
            noteId = noteId,
            attachmentUrl = localPath,
            attachmentName = attachmentName,
            attachmentKind = attachmentKind,
            replyToId = replyToId,
            replyToName = replyToName,
            replyToBody = replyToBody,
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

    /** Idempotent call-history row shared by both peers (same [callId] → same message id). */
    suspend fun sendCallLog(channelId: String, callId: String, body: String) {
        send(
            channelId = channelId,
            body = body,
            parentId = null,
            locationUrl = null,
            messageId = callId,
        )
    }

    suspend fun push() = pushLock.withLock {
        for (row in db.chat().pending()) {
            val sent = row.pendingWrite ?: continue
            val write = json.decodeFromString(MessageWrite.serializer(), sent)
            // The server rejects a share whose task/note it has not received yet.
            if (write.taskId?.let { db.tasks().get(it)?.syncPending } == true) continue
            if (write.noteId?.let { db.notes().get(it)?.syncPending } == true) continue
            val ready = uploadAttachment(write)
            try {
                val saved = try {
                    api.putMessage(row.channelId, row.id, ready, row.workspaceId)
                } catch (e: ApiException) {
                    // Servers that predate the idempotent route still accept a normal send.
                    if (e.status != 404) throw e
                    api.postMessage(row.channelId, ready, row.workspaceId)
                }
                if (saved.id == row.id) {
                    db.chat().markSynced(row.id, sent, json.encodeToString(MessageDto.serializer(), saved), saved.createdAt)
                } else {
                    db.chat().remove(row.id)
                    db.chat().upsert(entity(row.workspaceId, saved))
                }
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

    /** Pull the conversation list when a message arrives for a chat we have not cached yet. */
    suspend fun ensureChannel(channelId: String) {
        val ws = session.current().workspace?.id ?: return
        if (db.chat().channelCount(ws, channelId) == 0) refreshChannels()
    }

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
