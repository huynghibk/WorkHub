package com.workhub.app.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workhub.app.BuildConfig
import com.workhub.shared.UserDto
import com.workhub.shared.WorkspaceDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore(name = "workhub_session")

data class Session(
    val token: String?,
    val user: UserDto?,
    val baseUrl: String,
    val workspace: WorkspaceDto? = null,
)

class SessionStore(
    context: Context,
    private val defaultBaseUrl: String,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")
    private val baseKey = stringPreferencesKey("base_url")
    private val workspaceKey = stringPreferencesKey("workspace")

    val state: Flow<Session> = appContext.sessionDataStore.data.map { prefs ->
        val user = prefs[userKey]?.let { raw ->
            runCatching { json.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
        }
        val workspace = prefs[workspaceKey]?.let { raw ->
            runCatching { json.decodeFromString(WorkspaceDto.serializer(), raw) }.getOrNull()
        }
        Session(
            token = prefs[tokenKey],
            user = user,
            baseUrl = prefs[baseKey] ?: defaultBaseUrl,
            workspace = workspace,
        )
    }

    suspend fun current(): Session = state.first()

    suspend fun setBaseUrl(url: String) {
        val clean = url.trim().ifBlank { defaultBaseUrl }.trimEnd('/')
        appContext.sessionDataStore.edit { it[baseKey] = clean }
    }

    suspend fun save(token: String, user: UserDto) {
        appContext.sessionDataStore.edit {
            it[tokenKey] = token
            it[userKey] = json.encodeToString(UserDto.serializer(), user)
            it.remove(workspaceKey)
        }
    }

    suspend fun updateUser(user: UserDto) {
        appContext.sessionDataStore.edit {
            it[userKey] = json.encodeToString(UserDto.serializer(), user)
        }
    }

    suspend fun openWorkspace(workspace: WorkspaceDto) {
        appContext.sessionDataStore.edit {
            it[workspaceKey] = json.encodeToString(WorkspaceDto.serializer(), workspace)
        }
    }

    suspend fun clearWorkspace() {
        appContext.sessionDataStore.edit { it.remove(workspaceKey) }
    }

    private val rememberLoginKey = stringPreferencesKey("remember_login")
    private val rememberPasswordKey = stringPreferencesKey("remember_password")

    suspend fun rememberedLogin(): Pair<String, String>? {
        val prefs = appContext.sessionDataStore.data.first()
        val login = prefs[rememberLoginKey]?.takeIf { it.isNotBlank() } ?: return null
        val password = prefs[rememberPasswordKey] ?: return null
        return login to password
    }

    suspend fun rememberLogin(login: String, password: String) {
        appContext.sessionDataStore.edit {
            it[rememberLoginKey] = login
            it[rememberPasswordKey] = password
        }
    }

    suspend fun forgetLogin() {
        appContext.sessionDataStore.edit {
            it.remove(rememberLoginKey)
            it.remove(rememberPasswordKey)
        }
    }

    suspend fun clear() {
        appContext.sessionDataStore.edit {
            it.remove(tokenKey)
            it.remove(userKey)
            it.remove(workspaceKey)
        }
    }
}

class AppGraph(context: Context) {
    val session = SessionStore(context, BuildConfig.DEFAULT_BASE_URL)
    val api = WorkHubApi(session)
    val realtime = RealtimeHub(api)
    val userPrefs = UserPrefs(context)
    /** Link Maps được chọn trong app để gắn vào chat / task / note. */
    val attachLink = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** When true, MainNav hides the workspace/avatar chrome (task sub-functions use their own header). */
    val hideWorkspaceBar = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Chat tab is immersive by default; user can reveal workspace + bottom tabs. */
    val chatChromeVisible = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Deep link from notification tap: route + optional channel id. */
    val pendingDeepLink = kotlinx.coroutines.flow.MutableStateFlow<DeepLink?>(null)
    val pendingChatChannelId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun offerNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra(com.workhub.app.ui.NotificationIntents.EXTRA_OPEN_ROUTE) ?: return
        val channelId = intent.getStringExtra(com.workhub.app.ui.NotificationIntents.EXTRA_CHANNEL_ID)
        pendingDeepLink.value = DeepLink(route = route, channelId = channelId)
        // Clear so the same intent is not re-handled after config change without a new tap.
        intent.removeExtra(com.workhub.app.ui.NotificationIntents.EXTRA_OPEN_ROUTE)
        intent.removeExtra(com.workhub.app.ui.NotificationIntents.EXTRA_CHANNEL_ID)
    }
}

data class DeepLink(
    val route: String,
    val channelId: String? = null,
)

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph chưa được cung cấp")
}
