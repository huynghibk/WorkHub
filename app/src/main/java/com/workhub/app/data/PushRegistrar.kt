package com.workhub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.pushStore by preferencesDataStore("workhub_push")

/** Registers a stable device token for FCM or local fallback push routing. */
class PushRegistrar(context: Context, private val api: WorkHubApi) {
    private val app = context.applicationContext
    private val tokenKey = stringPreferencesKey("install_token")

    suspend fun registerIfNeeded() {
        val token = app.pushStore.data.first()[tokenKey]
            ?: "local:${UUID.randomUUID()}".also { fresh ->
                app.pushStore.edit { it[tokenKey] = fresh }
            }
        runCatching { api.registerPushToken(token) }
    }
}
