package com.workhub.server

import com.workhub.shared.NotificationDto
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

internal fun isChannelMuted(userId: String, channelId: String): Boolean = db {
    ChannelMutes.select {
        (ChannelMutes.userId eq userId) and (ChannelMutes.channelId eq channelId)
    }.any()
}

internal fun registerPushToken(userId: String, token: String, platform: String) {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return
    db {
        PushTokens.deleteWhere { (PushTokens.userId eq userId) and (PushTokens.token eq trimmed) }
        PushTokens.insert {
            it[PushTokens.userId] = userId
            it[PushTokens.token] = trimmed.take(512)
            it[PushTokens.platform] = platform.take(16)
        }
    }
}

internal fun tryPushNotification(userId: String, notice: NotificationDto) {
    if (notice.channelId != null && isChannelMuted(userId, notice.channelId)) return
    val key = System.getenv("FCM_SERVER_KEY")?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val tokens = db {
        PushTokens.select { PushTokens.userId eq userId }
            .map { it[PushTokens.token] }
            .filter { !it.startsWith("local:") }
    }
    if (tokens.isEmpty()) return
    tokens.forEach { token ->
        runCatching {
            val payload = buildJsonObject {
                put("to", token)
                put("data", buildJsonObject {
                    put("kind", notice.kind)
                    put("title", notice.title)
                    put("body", notice.body)
                    put("channelId", notice.channelId ?: "")
                    put("notificationId", notice.id)
                })
            }.toString()
            val conn = URL("https://fcm.googleapis.com/fcm/send").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "key=$key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.inputStream.close()
            conn.disconnect()
        }
    }
}
