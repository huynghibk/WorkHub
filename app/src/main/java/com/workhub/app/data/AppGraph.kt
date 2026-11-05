package com.workhub.app.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workhub.app.BuildConfig
import com.workhub.app.data.local.WorkHubDb
import com.workhub.shared.UserDto
import com.workhub.shared.WorkspaceDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    /** Older builds stored the password here in plain text; only kept so it can be wiped. */
    private val legacyPasswordKey = stringPreferencesKey("remember_password")

    /** Username/email to prefill on the login screen. The password is never stored. */
    suspend fun rememberedLogin(): String? =
        appContext.sessionDataStore.data.first()[rememberLoginKey]?.takeIf { it.isNotBlank() }

    suspend fun rememberLogin(login: String) {
        appContext.sessionDataStore.edit { it[rememberLoginKey] = login }
    }

    suspend fun forgetLogin() {
        appContext.sessionDataStore.edit { it.remove(rememberLoginKey) }
    }

    suspend fun purgeLegacyPassword() {
        appContext.sessionDataStore.edit { it.remove(legacyPasswordKey) }
    }

    suspend fun clear() {
        appContext.sessionDataStore.edit {
            it.remove(tokenKey)
            it.remove(userKey)
            it.remove(workspaceKey)
        }
    }

    /**
     * Token rejected by the server: send the user back to Login but keep [userKey], so the
     * offline data (possibly unsynced) survives until the same account signs in again.
     */
    suspend fun expire() {
        appContext.sessionDataStore.edit { it.remove(tokenKey) }
    }
}

class AppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val session = SessionStore(context, BuildConfig.DEFAULT_BASE_URL)
    val api = WorkHubApi(session)
    val realtime = RealtimeHub(api)
    private val db = WorkHubDb.create(context)
    val chat = ChatStore(db, api, session) { SyncWorker.enqueue(appContext) }
    val offline = OfflineStore(db, api, session, chat) { SyncWorker.enqueue(appContext) }
    val keepAlive = ServerKeepAlive(api)
    val userPrefs = UserPrefs(context)
    /** Link Maps được chọn trong app để gắn vào chat / task / note. */
    val attachLink = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** When true, MainNav hides the workspace/avatar chrome (task sub-functions use their own header). */
    val hideWorkspaceBar = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** When false on chat, hide workspace top bar (bottom nav stays). */
    val chatChromeVisible = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Deep link from notification tap: route + optional channel id. */
    val pendingDeepLink = kotlinx.coroutines.flow.MutableStateFlow<DeepLink?>(null)
    val pendingChatChannelId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** Quick-create from bottom "+" : "task" | "note". */
    val pendingQuickCreate = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        scope.launch {
            session.purgeLegacyPassword()
            chat.pruneOlderThan(System.currentTimeMillis() - CHAT_CACHE_DAYS * 24 * 60 * 60 * 1000)
        }
        scope.launch {
            realtime.events.collect { event -> runCatching { offline.applyEvent(event) } }
        }
        // Catch up on events missed while the socket was down.
        scope.launch {
            realtime.connected.filter { it }.collect { SyncWorker.enqueue(appContext) }
        }
        scope.launch {
            // Explicit logout drops the user; an expired token keeps it (see SessionStore.expire).
            session.state.map { it.user?.id }.distinctUntilChanged().filter { it == null }.collect { offline.clear() }
        }
    }

    fun offerNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra(com.workhub.app.ui.NotificationIntents.EXTRA_OPEN_ROUTE) ?: return
        val channelId = intent.getStringExtra(com.workhub.app.ui.NotificationIntents.EXTRA_CHANNEL_ID)
        pendingDeepLink.value = DeepLink(route = route, channelId = channelId)
        // Clear so the same intent is not re-handled after config change without a new tap.
        intent.removeExtra(com.workhub.app.ui.NotificationIntents.EXTRA_OPEN_ROUTE)
        intent.removeExtra(com.workhub.app.ui.NotificationIntents.EXTRA_CHANNEL_ID)
    }

    companion object {
        private const val CHAT_CACHE_DAYS = 30L

        @Volatile
        private var instance: AppGraph? = null

        /** One graph per process so Activity and SyncWorker share the same DB and session. */
        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}

data class DeepLink(
    val route: String,
    val channelId: String? = null,
)

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph chưa được cung cấp")
}
