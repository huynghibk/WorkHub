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

    fun starred(id: String): Boolean = prefs.getBoolean(starKey(id), false)

    fun toggleStar(id: String) {
        prefs.edit().putBoolean(starKey(id), !starred(id)).apply()
        tick.value++
    }

    private fun reactKey(id: String) = "r:$id"
    private fun pinKey(id: String) = "p:$id"
    private fun starKey(id: String) = "s:$id"
}
