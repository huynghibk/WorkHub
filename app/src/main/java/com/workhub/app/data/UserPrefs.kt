package com.workhub.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsStore by preferencesDataStore(name = "workhub_user_prefs")

data class UserSettings(
    val status: String = "",
    val muteNotifications: Boolean = false,
    val notifyAssignedComments: Boolean = true,
    val notifyMentions: Boolean = true,
    val notifyChannelComments: Boolean = true,
    val notifyResolved: Boolean = true,
    val notifyThreadReplies: Boolean = true,
    val notifyReactions: Boolean = true,
    val themeMode: String = "AUTO", // LIGHT, DARK, AUTO
    val hapticFeedback: Boolean = true,
    val soundEnabled: Boolean = true,
    val launchPage: String = "home",
)

class UserPrefs(context: Context) {
    private val store = context.applicationContext.userPrefsStore

    private val statusKey = stringPreferencesKey("status")
    private val muteKey = booleanPreferencesKey("mute_notifications")
    private val assignedKey = booleanPreferencesKey("notify_assigned")
    private val mentionsKey = booleanPreferencesKey("notify_mentions")
    private val channelKey = booleanPreferencesKey("notify_channel")
    private val resolvedKey = booleanPreferencesKey("notify_resolved")
    private val repliesKey = booleanPreferencesKey("notify_replies")
    private val reactionsKey = booleanPreferencesKey("notify_reactions")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hapticKey = booleanPreferencesKey("haptic")
    private val soundKey = booleanPreferencesKey("sound")
    private val launchKey = stringPreferencesKey("launch_page")

    val state: Flow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            status = prefs[statusKey].orEmpty(),
            muteNotifications = prefs[muteKey] ?: false,
            notifyAssignedComments = prefs[assignedKey] ?: true,
            notifyMentions = prefs[mentionsKey] ?: true,
            notifyChannelComments = prefs[channelKey] ?: true,
            notifyResolved = prefs[resolvedKey] ?: true,
            notifyThreadReplies = prefs[repliesKey] ?: true,
            notifyReactions = prefs[reactionsKey] ?: true,
            themeMode = prefs[themeKey] ?: "AUTO",
            hapticFeedback = prefs[hapticKey] ?: true,
            soundEnabled = prefs[soundKey] ?: true,
            launchPage = prefs[launchKey] ?: "home",
        )
    }

    suspend fun setStatus(value: String) {
        store.edit { it[statusKey] = value }
    }

    suspend fun setMute(value: Boolean) {
        store.edit { it[muteKey] = value }
    }

    suspend fun setTheme(value: String) {
        store.edit { it[themeKey] = value }
    }

    suspend fun setHaptic(value: Boolean) {
        store.edit { it[hapticKey] = value }
    }

    suspend fun setSound(value: Boolean) {
        store.edit { it[soundKey] = value }
    }

    suspend fun setLaunchPage(value: String) {
        store.edit { it[launchKey] = value }
    }

    suspend fun setNotifyAssigned(value: Boolean) = store.edit { it[assignedKey] = value }
    suspend fun setNotifyMentions(value: Boolean) = store.edit { it[mentionsKey] = value }
    suspend fun setNotifyChannel(value: Boolean) = store.edit { it[channelKey] = value }
    suspend fun setNotifyResolved(value: Boolean) = store.edit { it[resolvedKey] = value }
    suspend fun setNotifyReplies(value: Boolean) = store.edit { it[repliesKey] = value }
    suspend fun setNotifyReactions(value: Boolean) = store.edit { it[reactionsKey] = value }

    suspend fun resetNotificationDefaults() {
        store.edit {
            it[assignedKey] = true
            it[mentionsKey] = true
            it[channelKey] = true
            it[resolvedKey] = true
            it[repliesKey] = true
            it[reactionsKey] = true
            it[muteKey] = false
        }
    }
}
