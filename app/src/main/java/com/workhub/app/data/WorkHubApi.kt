package com.workhub.app.data

import com.workhub.shared.AuthResponse
import com.workhub.shared.CommentDto
import com.workhub.shared.CommentWrite
import com.workhub.shared.FolderNode
import com.workhub.shared.FolderWrite
import com.workhub.shared.ListWrite
import com.workhub.shared.MemberAdd
import com.workhub.shared.SearchResult
import com.workhub.shared.SpaceNode
import com.workhub.shared.SpaceWrite
import com.workhub.shared.WorkListNode
import com.workhub.shared.WorkTree
import com.workhub.shared.DepartmentNode
import com.workhub.shared.DivisionNode
import com.workhub.shared.JoinWorkspace
import com.workhub.shared.NamedCreate
import com.workhub.shared.OrgTree
import com.workhub.shared.RoleUpdate
import com.workhub.shared.TeamNode
import com.workhub.shared.WorkspaceCreate
import com.workhub.shared.WorkspaceDto
import com.workhub.shared.ChannelDto
import com.workhub.shared.ChannelWrite
import com.workhub.shared.DirectWrite
import com.workhub.shared.GroupWrite
import com.workhub.shared.IdList
import com.workhub.shared.NotificationDto
import com.workhub.shared.ErrorResponse
import com.workhub.shared.LoginRequest
import com.workhub.shared.MemberDto
import com.workhub.shared.MessageDto
import com.workhub.shared.MessageWrite
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import com.workhub.shared.ProfileUpdate
import com.workhub.shared.RegisterRequest
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite
import com.workhub.shared.UserDto
import com.workhub.shared.WsEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

class WorkHubApi(private val session: SessionStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp) {
        engine {
            config {
                // Align with HttpTimeout — Render free cold-start is often >10s.
                connectTimeout(30, TimeUnit.SECONDS)
                // 0 = no idle read kill (needed for WebSocket); HTTP still bounded by HttpTimeout.
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
                pingInterval(15, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(HttpTimeout) {
            // Render free cold-start can take 30–60s; keep headroom.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            // Do not set socketTimeout — would kill idle WebSocket frames.
        }
    }

    /** Lightweight ping so Render free tier does not sleep after ~15 minutes idle. */
    suspend fun ping() {
        try {
            val current = session.current()
            val response = http.request(current.baseUrl.trimEnd('/') + "/health") {
                method = HttpMethod.Get
            }
            if (!response.status.isSuccess()) {
                throw ApiException("Máy chủ chưa sẵn sàng (${response.status.value})")
            }
        } catch (error: ApiException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(connectErrorMessage(error))
        }
    }

    /**
     * Render free sleeps ~15' idle. Prefer waking via /health, but don't block auth for a full minute.
     * JWT is stateless — same account may sign in on many devices at once.
     */
    suspend fun ensureAwake(maxAttempts: Int = 4) {
        var last: Exception? = null
        repeat(maxAttempts) {
            try {
                ping()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                last = error
                delay(2_500)
            }
        }
        // Soft-fail: caller can still try the real request (also wakes Render).
        if (last != null) return
    }

    suspend fun login(login: String, password: String): AuthResponse {
        ensureAwake()
        return authWithRetry {
            send(
                HttpMethod.Post,
                "/auth/login",
                LoginRequest(email = login, password = password, login = login),
                auth = false,
            )
        }
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        ensureAwake()
        return authWithRetry {
            send(HttpMethod.Post, "/auth/register", request, auth = false)
        }
    }

    private suspend fun <T> authWithRetry(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(5) { attempt ->
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                last = error
                if (attempt < 4) delay(3_000)
            }
        }
        throw ApiException(
            last?.message
                ?: "Không kết nối được máy chủ. Mở /health trên trình duyệt, chờ ok rồi thử lại.",
        )
    }

    suspend fun updateProfile(update: ProfileUpdate): UserDto {
        // Thử nhiều path vì một số bản server cũ chỉ có một trong các route này.
        val paths = listOf("/profile", "/me", "/me/profile")
        var lastError: Exception? = null
        for (path in paths) {
            try {
                return send(HttpMethod.Post, path, update)
            } catch (e: ApiException) {
                lastError = e
                val msg = e.message.orEmpty()
                val missing = msg.contains("(404)") || msg.contains("(405)") || msg.contains("405") || msg.contains("404")
                if (!missing) throw e
            }
        }
        throw lastError
            ?: ApiException("Máy chủ chưa có API cập nhật hồ sơ. Hãy chạy lại: gradlew :server:run")
    }

    suspend fun workspaces(): List<WorkspaceDto> = send(HttpMethod.Get, "/workspaces")

    suspend fun createWorkspace(name: String): WorkspaceDto =
        send(HttpMethod.Post, "/workspaces", WorkspaceCreate(name))

    suspend fun joinWorkspace(inviteCode: String): WorkspaceDto =
        send(HttpMethod.Post, "/workspaces/join", JoinWorkspace(inviteCode))

    suspend fun renameWorkspace(name: String): WorkspaceDto =
        send(HttpMethod.Put, "/workspaces/current", WorkspaceCreate(name))

    suspend fun orgTree(): OrgTree = send(HttpMethod.Get, "/org/tree")

    suspend fun createDivision(name: String): DivisionNode =
        send(HttpMethod.Post, "/admin/divisions", NamedCreate(name))

    suspend fun createDepartment(divisionId: String, name: String): DepartmentNode =
        send(HttpMethod.Post, "/admin/departments", NamedCreate(name, divisionId))

    suspend fun createTeam(departmentId: String, name: String): TeamNode =
        send(HttpMethod.Post, "/admin/teams", NamedCreate(name, departmentId))

    suspend fun members(): List<MemberDto> = send(HttpMethod.Get, "/users")

    suspend fun workTree(): WorkTree = send(HttpMethod.Get, "/work")

    suspend fun createSpace(name: String, isPrivate: Boolean): SpaceNode =
        send(HttpMethod.Post, "/spaces", SpaceWrite(name, isPrivate))

    suspend fun addSpaceMember(spaceId: String, userId: String) {
        send<Unit>(HttpMethod.Post, "/spaces/$spaceId/members", MemberAdd(userId))
    }

    suspend fun createFolder(spaceId: String, name: String): FolderNode =
        send(HttpMethod.Post, "/folders", FolderWrite(name, spaceId))

    suspend fun createWorkList(spaceId: String, name: String, folderId: String?): WorkListNode =
        send(HttpMethod.Post, "/lists", ListWrite(name, spaceId, folderId))

    suspend fun search(query: String): SearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return send(HttpMethod.Get, "/search?q=$encoded")
    }

    suspend fun setRole(userId: String, role: String) {
        send<Unit>(HttpMethod.Put, "/members/$userId/role", RoleUpdate(role))
    }

    suspend fun tasks(): List<TaskDto> = send(HttpMethod.Get, "/tasks")

    suspend fun createTask(write: TaskWrite): TaskDto = send(HttpMethod.Post, "/tasks", write)

    suspend fun updateTask(id: String, write: TaskWrite): TaskDto = send(HttpMethod.Put, "/tasks/$id", write)

    suspend fun deleteTask(id: String) {
        send<Unit>(HttpMethod.Delete, "/tasks/$id")
    }

    suspend fun comments(taskId: String): List<CommentDto> = send(HttpMethod.Get, "/tasks/$taskId/comments")

    suspend fun addComment(taskId: String, body: String): CommentDto =
        send(HttpMethod.Post, "/tasks/$taskId/comments", CommentWrite(body))

    suspend fun notes(): List<NoteDto> = send(HttpMethod.Get, "/notes")

    suspend fun createNote(write: NoteWrite): NoteDto = send(HttpMethod.Post, "/notes", write)

    suspend fun updateNote(id: String, write: NoteWrite): NoteDto = send(HttpMethod.Put, "/notes/$id", write)

    suspend fun deleteNote(id: String) {
        send<Unit>(HttpMethod.Delete, "/notes/$id")
    }

    suspend fun channels(): List<ChannelDto> = send(HttpMethod.Get, "/channels")

    suspend fun createChannel(name: String, isPrivate: Boolean = false): ChannelDto =
        send(HttpMethod.Post, "/channels", ChannelWrite(name, isPrivate))

    suspend fun openDirect(userId: String): ChannelDto =
        send(HttpMethod.Post, "/channels/direct", DirectWrite(userId))

    suspend fun openGroup(name: String, memberIds: List<String>): ChannelDto =
        send(HttpMethod.Post, "/channels/group", GroupWrite(name, memberIds))

    suspend fun unreadNotifications(): List<NotificationDto> = send(HttpMethod.Get, "/notifications")

    suspend fun markNotificationsRead(ids: List<String>) {
        send<Unit>(HttpMethod.Post, "/notifications/read", IdList(ids))
    }

    suspend fun addChannelMember(channelId: String, userId: String) {
        send<Unit>(HttpMethod.Post, "/channels/$channelId/members", MemberAdd(userId))
    }

    suspend fun messages(channelId: String, parentId: String? = null): List<MessageDto> {
        val path = if (parentId.isNullOrBlank()) {
            "/channels/$channelId/messages"
        } else {
            "/channels/$channelId/messages?parent=$parentId"
        }
        return send(HttpMethod.Get, path)
    }

    suspend fun sendMessage(
        channelId: String,
        body: String,
        parentId: String? = null,
        locationUrl: String? = null,
    ): MessageDto =
        send(HttpMethod.Post, "/channels/$channelId/messages", MessageWrite(body, parentId, locationUrl))

    suspend fun messageToTask(channelId: String, messageId: String): TaskDto =
        send(HttpMethod.Post, "/channels/$channelId/messages/$messageId/task")

    suspend fun listen(onEvent: suspend (WsEvent) -> Unit) {
        val current = session.current()
        val token = current.token ?: throw ApiException("Bạn cần đăng nhập lại")
        val wsUrl = current.baseUrl.trimEnd('/').replace(Regex("^http"), "ws") + "/ws"
        val workspaceId = current.workspace?.id ?: throw ApiException("Hãy chọn workspace")
        http.webSocket(wsUrl, request = {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-Workspace-Id", workspaceId)
        }) {
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val event = runCatching { json.decodeFromString(WsEvent.serializer(), text) }.getOrNull() ?: continue
                onEvent(event)
            }
        }
    }

    private suspend inline fun <reified T> send(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        auth: Boolean = true,
    ): T {
        try {
            val current = session.current()
            val response = http.request(current.baseUrl.trimEnd('/') + path) {
                this.method = method
                if (auth) {
                    val token = current.token ?: throw ApiException("Bạn cần đăng nhập lại")
                    header(HttpHeaders.Authorization, "Bearer $token")
                    current.workspace?.id?.let { header("X-Workspace-Id", it) }
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            if (!response.status.isSuccess()) {
                if (response.status.value == 405 && path.contains("/me")) {
                    throw ApiException("Máy chủ chưa cập nhật API hồ sơ (405). Hãy restart server backend rồi thử lại.")
                }
                if (response.status.value == 404) {
                    throw ApiException(
                        "Không tìm thấy API (${path}). Kiểm tra địa chỉ máy chủ và Render còn Live; mở /health trên trình duyệt phải ra ok.",
                    )
                }
                val message = runCatching { response.body<ErrorResponse>().error }.getOrNull()
                    ?: "Yêu cầu thất bại (${response.status.value})"
                // Stale workspace after Render DB wipe / redeploy — drop local selection.
                if (response.status.value == 403 &&
                    message.contains("không thuộc workspace", ignoreCase = true)
                ) {
                    session.clearWorkspace()
                }
                throw ApiException(message)
            }
            if (T::class == Unit::class) {
                @Suppress("UNCHECKED_CAST")
                return Unit as T
            }
            return response.body()
        } catch (error: ApiException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(connectErrorMessage(error))
        }
    }
}

private fun connectErrorMessage(error: Exception): String {
    val detail = error.message?.lineSequence()?.firstOrNull()?.take(120).orEmpty()
    return if (detail.isBlank()) {
        "Không kết nối được máy chủ. Mở /health trên trình duyệt, chờ chữ ok (Render có thể đang ngủ ~1 phút) rồi thử lại."
    } else {
        "Không kết nối được máy chủ ($detail). Nếu Render vừa ngủ, mở /health chờ ok rồi thử lại."
    }
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "Có lỗi xảy ra"
    else -> "Có lỗi xảy ra"
}
