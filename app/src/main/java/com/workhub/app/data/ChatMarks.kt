package com.workhub.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactions, pins and stars live on this device only. The server has no fields for them yet.
 */
class ChatMarks(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("workhub_chat_marks", Context.MODE_PRIVATE)
    private val tick = MutableStateFlow(0)
    val changes: StateFlow<Int> = tick.asStateFlow()

    fun reaction(id: String): String? = prefs.getString(reactKey(id), null)?.takeIf { it.isNotEmpty() }

    fun toggleReaction(id: String, emoji: String) {
        val next = if (reaction(id) == emoji) "" else emoji
        prefs.edit().putString(reactKey(id), next).apply()
        tick.value++
    }

    fun pinned(id: String): Boolean = prefs.getBoolean(pinKey(id), false)

    fun togglePin(id: String) {
        prefs.edit().putBoolean(pinKey(id), !pinned(id)).apply()
        tick.value++
    }

    fun hidden(): Set<String> =
        prefs.getStringSet(hiddenKey, emptySet()).orEmpty()

    fun hide(channelId: String) {
        prefs.edit().putStringSet(hiddenKey, hidden() + channelId).apply()
        tick.value++
    }

    fun unhide(channelId: String) {
        prefs.edit().putStringSet(hiddenKey, hidden() - channelId).apply()
        tick.value++
    }

    fun isChannelPinned(channelId: String): Boolean = prefs.getBoolean(channelPinKey(channelId), false)

    fun toggleChannelPin(channelId: String) {
        prefs.edit().putBoolean(channelPinKey(channelId), !isChannelPinned(channelId)).apply()
        tick.value++
    }

    fun isChannelMuted(channelId: String): Boolean = prefs.getBoolean(muteKey(channelId), false)

    fun setChannelMuted(channelId: String, muted: Boolean) {
        prefs.edit().putBoolean(muteKey(channelId), muted).apply()
        tick.value++
    }

    fun isForcedUnread(channelId: String): Boolean = prefs.getBoolean(forceUnreadKey(channelId), false)

    fun markUnread(channelId: String) {
        prefs.edit().putBoolean(forceUnreadKey(channelId), true).apply()
        tick.value++
    }

    fun lastRead(channelId: String): Long = prefs.getLong(readKey(channelId), 0L)

    fun markRead(channelId: String, at: Long) {
        val forced = isForcedUnread(channelId)
        val newer = at > lastRead(channelId)
        if (!forced && !newer) return
        prefs.edit().apply {
            remove(forceUnreadKey(channelId))
            if (newer) putLong(readKey(channelId), at)
        }.apply()
        tick.value++
    }

    fun starred(id: String): Boolean = prefs.getBoolean(starKey(id), false)

    fun toggleStar(id: String) {
        prefs.edit().putBoolean(starKey(id), !starred(id)).apply()
        tick.value++
    }

    private fun reactKey(id: String) = "r:$id"
    private fun pinKey(id: String) = "p:$id"
    private fun starKey(id: String) = "s:$id"
    private fun readKey(id: String) = "read:$id"
    private fun channelPinKey(id: String) = "cp:$id"
    private fun muteKey(id: String) = "cm:$id"
    private fun forceUnreadKey(id: String) = "fu:$id"
    private val hiddenKey = "hidden_channels"
}
