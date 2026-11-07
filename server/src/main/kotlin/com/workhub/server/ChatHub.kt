package com.workhub.server

import io.ktor.websocket.Frame
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel

internal class WsConnection(
    val companyId: String,
    val userId: String,
    val outgoing: Channel<Frame> = Channel(Channel.UNLIMITED),
)

internal object ChatHub {
    private val rooms = ConcurrentHashMap<String, MutableSet<WsConnection>>()

    fun join(companyId: String, userId: String): WsConnection {
        val connection = WsConnection(companyId, userId)
        rooms.computeIfAbsent(companyId) { java.util.concurrent.CopyOnWriteArraySet() }.add(connection)
        return connection
    }

    fun leave(connection: WsConnection) {
        rooms[connection.companyId]?.remove(connection)
        connection.outgoing.close()
    }

    fun publish(companyId: String, payload: String) {
        rooms[companyId]?.forEach { connection ->
            connection.outgoing.trySend(Frame.Text(payload))
        }
    }

    fun userIds(companyId: String): Set<String> =
        rooms[companyId]?.map { it.userId }?.toSet().orEmpty()

    fun onlineUserIds(): Set<String> =
        rooms.values.flatMap { room -> room.map { it.userId } }.toSet()

    fun isOnline(companyId: String, userId: String): Boolean =
        rooms[companyId]?.any { it.userId == userId } == true

    fun publishTo(companyId: String, userId: String, payload: String) {
        rooms[companyId]?.filter { it.userId == userId }?.forEach { connection ->
            connection.outgoing.trySend(Frame.Text(payload))
        }
    }

    fun publishToUser(userId: String, payload: String) {
        rooms.values.forEach { room ->
            room.filter { it.userId == userId }.forEach { connection ->
                connection.outgoing.trySend(Frame.Text(payload))
            }
        }
    }
}

internal fun fanoutChannelEvent(companyId: String, memberIds: Iterable<String>, payload: String) {
    ChatHub.publish(companyId, payload)
    memberIds.forEach { ChatHub.publishToUser(it, payload) }
}
