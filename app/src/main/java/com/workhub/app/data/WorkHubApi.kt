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
import com.workhub.shared.forServer
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import com.workhub.shared.ProfileUpdate
import com.workhub.shared.RegisterRequest
import com.workhub.shared.SendVerifyRequest
import com.workhub.shared.SendVerifyResponse
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite
import com.workhub.shared.UploadResult
import com.workhub.shared.UserDto
import com.workhub.shared.WsEvent
import com.workhub.shared.MdmBindRequest
import com.workhub.shared.MdmCloudDeviceDto
import com.workhub.shared.MdmCommandRequest
import com.workhub.shared.MdmCommandResult
import com.workhub.shared.MdmDeviceDto
import com.workhub.shared.MdmEnrollByInviteRequest
import com.workhub.shared.MdmEnrollmentTokenDto
import com.workhub.shared.MdmLocalRegisterRequest
import com.workhub.shared.MdmLocationReport
import com.workhub.shared.MdmLostModeRequest
import com.workhub.shared.MdmRemoteArtifactDto
import com.workhub.shared.MdmRemoteCameraRequest
import com.workhub.shared.MdmRemoteListRequest
import com.workhub.shared.MdmRemoteSubmitRequest
import com.workhub.shared.MdmSelfBindRequest
import com.workhub.shared.MdmStatusDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.client.statement.HttpResponse
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int? = null) : Exception(message)

class WorkHubApi(private val session: SessionStore) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Server clock minus device clock, refreshed from every response. */
    @Volatile
    private var clockOffset = 0L

    /** Device time corrected to the server clock (falls back to the device clock until first contact). */
    fun serverNow(): Long = System.currentTimeMillis() + clockOffset

    private fun trackClock(response: HttpResponse) {
        response.headers["X-Server-Time"]?.toLongOrNull()?.let { clockOffset = it - System.currentTimeMillis() }
    }
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
            trackClock(response)
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

    suspend fun sendVerifyCode(email: String): SendVerifyResponse {
        ensureAwake()
        return authWithRetry {
            send(HttpMethod.Post, "/auth/verify-email", SendVerifyRequest(email), auth = false)
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

    suspend fun lookupUser(username: String): MemberDto {
        val query = java.net.URLEncoder.encode(username.trim(), Charsets.UTF_8.name())
        return send(HttpMethod.Get, "/users/lookup?username=$query")
    }

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

    suspend fun mdmStatus(): MdmStatusDto = send(HttpMethod.Get, "/admin/mdm/status")

    suspend fun mdmEnrollmentToken(): MdmEnrollmentTokenDto =
        send(HttpMethod.Post, "/admin/mdm/enrollment-token")

    /** Thành viên tự lấy mã đăng ký máy (đã đăng nhập). */
    suspend fun myMdmEnrollmentToken(): MdmEnrollmentTokenDto =
        send(HttpMethod.Post, "/me/mdm/enroll-token")

    /** Màn login: lấy mã bằng invite code, không cần JWT. */
    suspend fun enrollTokenByInvite(inviteCode: String): MdmEnrollmentTokenDto =
        send(
            HttpMethod.Post,
            "/mdm/enroll-token",
            MdmEnrollByInviteRequest(inviteCode),
            auth = false,
        )

    suspend fun mdmDevices(): List<MdmDeviceDto> = send(HttpMethod.Get, "/admin/mdm/devices")

    suspend fun myMdmDevices(): List<MdmDeviceDto> = send(HttpMethod.Get, "/me/mdm/my-devices")

    suspend fun mdmCloudDevices(): List<MdmCloudDeviceDto> =
        send(HttpMethod.Get, "/admin/mdm/cloud-devices")

    suspend fun myMdmCloudDevices(): List<MdmCloudDeviceDto> =
        send(HttpMethod.Get, "/me/mdm/cloud-devices")

    suspend fun mdmBind(userId: String, deviceName: String, displayName: String = ""): MdmDeviceDto =
        send(HttpMethod.Post, "/admin/mdm/bind", MdmBindRequest(userId, deviceName, displayName))

    suspend fun mdmSelfBind(deviceName: String, displayName: String = ""): MdmDeviceDto =
        send(HttpMethod.Post, "/me/mdm/self-bind", MdmSelfBindRequest(deviceName, displayName))

    suspend fun mdmRegisterLocal(deviceId: String, displayName: String = ""): MdmDeviceDto =
        send(HttpMethod.Post, "/me/mdm/register-local", MdmLocalRegisterRequest(deviceId, displayName))

    suspend fun mdmLock(userId: String? = null, deviceName: String? = null): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/lock", MdmCommandRequest(userId, deviceName))

    suspend fun mdmWipe(userId: String? = null, deviceName: String? = null): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/wipe", MdmCommandRequest(userId, deviceName))

    suspend fun mdmPlayAlarm(userId: String): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/play-alarm", MdmCommandRequest(userId = userId))

    suspend fun mdmLocationRequest(userId: String): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/location-request", MdmCommandRequest(userId = userId))

    suspend fun reportDeviceLocation(locationUrl: String, deviceName: String = ""): MdmCommandResult =
        send(HttpMethod.Post, "/me/device-location", MdmLocationReport(locationUrl, deviceName))

    suspend fun mdmLostMode(userId: String, active: Boolean, message: String = ""): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/lost-mode", MdmLostModeRequest(userId, active, message))

    suspend fun mdmRemoteCamera(userId: String, facing: String = "front"): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/remote/camera", MdmRemoteCameraRequest(userId, facing))

    suspend fun mdmRemoteMedia(userId: String, limit: Int = 10): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/remote/media", MdmRemoteListRequest(userId, limit))

    suspend fun mdmRemoteSms(userId: String, limit: Int = 30): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/remote/sms", MdmRemoteListRequest(userId, limit))

    suspend fun mdmRemoteContacts(userId: String, limit: Int = 100): MdmCommandResult =
        send(HttpMethod.Post, "/admin/mdm/remote/contacts", MdmRemoteListRequest(userId, limit))

    suspend fun mdmRemoteResults(userId: String? = null): List<MdmRemoteArtifactDto> {
        val q = userId?.trim()?.takeIf { it.isNotEmpty() }?.let { "?userId=$it" }.orEmpty()
        return send(HttpMethod.Get, "/admin/mdm/remote/results$q")
    }

    suspend fun mdmSubmitRemoteResult(body: MdmRemoteSubmitRequest): MdmRemoteArtifactDto =
        send(HttpMethod.Post, "/me/mdm/remote-result", body)

    suspend fun tasks(workspaceId: String? = null): List<TaskDto> =
        send(HttpMethod.Get, "/tasks", workspaceId = workspaceId)

    /** Idempotent create-or-update with a client-generated id. */
    suspend fun putTask(id: String, write: TaskWrite, workspaceId: String): TaskDto =
        send(HttpMethod.Put, "/tasks/$id", write, workspaceId = workspaceId)

    suspend fun deleteTask(id: String, workspaceId: String) {
        send<Unit>(HttpMethod.Delete, "/tasks/$id", workspaceId = workspaceId)
    }

    suspend fun comments(taskId: String): List<CommentDto> = send(HttpMethod.Get, "/tasks/$taskId/comments")

    suspend fun addComment(taskId: String, body: String): CommentDto =
        send(HttpMethod.Post, "/tasks/$taskId/comments", CommentWrite(body))

    suspend fun notes(workspaceId: String? = null): List<NoteDto> =
        send(HttpMethod.Get, "/notes", workspaceId = workspaceId)

    /** Idempotent create-or-update with a client-generated id. */
    suspend fun putNote(id: String, write: NoteWrite, workspaceId: String): NoteDto =
        send(HttpMethod.Put, "/notes/$id", write, workspaceId = workspaceId)

    suspend fun deleteNote(id: String, workspaceId: String) {
        send<Unit>(HttpMethod.Delete, "/notes/$id", workspaceId = workspaceId)
    }

    suspend fun channels(): List<ChannelDto> = send(HttpMethod.Get, "/channels")

    suspend fun createChannel(name: String, isPrivate: Boolean = false): ChannelDto =
        send(HttpMethod.Post, "/channels", ChannelWrite(name, isPrivate))

    suspend fun openDirect(userId: String): ChannelDto =
        send(HttpMethod.Post, "/channels/direct", DirectWrite(userId))

    suspend fun openGroup(name: String, memberIds: List<String>): ChannelDto =
        send(HttpMethod.Post, "/channels/group", GroupWrite(name, memberIds))

    suspend fun renameChannel(channelId: String, name: String): ChannelDto =
        send(HttpMethod.Put, "/channels/$channelId/name", ChannelWrite(name))

    suspend fun unreadNotifications(): List<NotificationDto> = send(HttpMethod.Get, "/notifications")

    suspend fun markNotificationsRead(ids: List<String>) {
        send<Unit>(HttpMethod.Post, "/notifications/read", IdList(ids))
    }

    suspend fun addChannelMember(channelId: String, userId: String) {
        send<Unit>(HttpMethod.Post, "/channels/$channelId/members", MemberAdd(userId))
    }

    suspend fun messages(
        channelId: String,
        parentId: String? = null,
        before: Long? = null,
        after: Long? = null,
        limit: Int? = null,
        query: String? = null,
    ): List<MessageDto> {
        val params = buildList {
            if (!parentId.isNullOrBlank()) add("parent=$parentId")
            before?.let { add("before=$it") }
            after?.let { add("after=$it") }
            limit?.let { add("limit=$it") }
            query?.trim()?.takeIf { it.length >= 2 }?.let { add("q=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}") }
        }
        val qs = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return send(HttpMethod.Get, "/channels/$channelId/messages$qs")
    }

    suspend fun channelReadStates(): List<com.workhub.shared.ChannelReadDto> =
        send(HttpMethod.Get, "/channels/read-states")

    suspend fun markChannelRead(channelId: String, lastReadAt: Long, lastMessageId: String?) {
        send<Unit>(
            HttpMethod.Post,
            "/channels/$channelId/read",
            com.workhub.shared.ChannelReadWrite(lastReadAt, lastMessageId),
        )
    }

    suspend fun editMessage(channelId: String, messageId: String, body: String): MessageDto =
        send(HttpMethod.Patch, "/channels/$channelId/messages/$messageId", com.workhub.shared.MessageEdit(body))

    suspend fun deleteMessage(channelId: String, messageId: String): MessageDto =
        send(HttpMethod.Delete, "/channels/$channelId/messages/$messageId")

    suspend fun setMessagePin(channelId: String, messageId: String, pinned: Boolean) {
        send<Unit>(HttpMethod.Put, "/channels/$channelId/messages/$messageId/pin", com.workhub.shared.PinWrite(pinned))
    }

    suspend fun pinnedMessages(channelId: String): List<String> =
        send(HttpMethod.Get, "/channels/$channelId/pins")

    suspend fun leaveChannel(channelId: String) {
        send<Unit>(HttpMethod.Post, "/channels/$channelId/leave")
    }

    suspend fun muteChannel(channelId: String, muted: Boolean) {
        if (muted) {
            send<Unit>(HttpMethod.Put, "/channels/$channelId/mute")
        } else {
            send<Unit>(HttpMethod.Delete, "/channels/$channelId/mute")
        }
    }

    suspend fun onlineUserIds(): List<String> =
        send<com.workhub.shared.IdList>(HttpMethod.Get, "/presence/online").ids

    suspend fun registerPushToken(token: String) {
        send<Unit>(HttpMethod.Post, "/push-tokens", com.workhub.shared.PushTokenWrite(token))
    }

    /** Idempotent send with a client-generated id (used by the offline queue). */
    suspend fun putMessage(channelId: String, id: String, write: MessageWrite, workspaceId: String): MessageDto =
        send(HttpMethod.Put, "/channels/$channelId/messages/$id", write.forServer(), workspaceId = workspaceId)

    /** Older servers only accept this. The returned id is the server's, not the client's. */
    suspend fun postMessage(channelId: String, write: MessageWrite, workspaceId: String): MessageDto =
        send(HttpMethod.Post, "/channels/$channelId/messages", write.forServer(), workspaceId = workspaceId)

    suspend fun upload(file: java.io.File, fileName: String): UploadResult {
        val current = session.current()
        val token = current.token ?: throw ApiException("Bạn cần đăng nhập lại")
        val response = http.submitFormWithBinaryData(
            url = current.baseUrl.trimEnd('/') + "/upload",
            formData = formData {
                append(
                    "file",
                    file.readBytes(),
                    Headers.build {
                        append(HttpHeaders.ContentType, contentTypeFor(fileName))
                        append(HttpHeaders.ContentDisposition, "filename=${fileName.replace("\"", "")}")
                    },
                )
            },
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        trackClock(response)
        if (!response.status.isSuccess()) {
            throw ApiException("Không tải được tệp (${response.status.value})", response.status.value)
        }
        return response.body()
    }

    private fun contentTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    suspend fun react(channelId: String, messageId: String, emoji: String): com.workhub.shared.ReactionEvent =
        send(HttpMethod.Put, "/channels/$channelId/messages/$messageId/react", com.workhub.shared.ReactionWrite(emoji))

    suspend fun messageToTask(channelId: String, messageId: String): TaskDto =
        send(HttpMethod.Post, "/channels/$channelId/messages/$messageId/task")

    suspend fun openRealtimeSession(
        onConnected: (suspend (WsEvent) -> Unit) -> Unit,
        onEvent: suspend (WsEvent) -> Unit,
    ) {
        val current = session.current()
        val token = current.token ?: throw ApiException("Bạn cần đăng nhập lại")
        val wsUrl = current.baseUrl.trimEnd('/').replace(Regex("^http"), "ws") + "/ws"
        val workspaceId = current.workspace?.id ?: throw ApiException("Hãy chọn workspace")
        http.webSocket(wsUrl, request = {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-Workspace-Id", workspaceId)
        }) {
            val sendEvent: suspend (WsEvent) -> Unit = { event ->
                send(Frame.Text(json.encodeToString(WsEvent.serializer(), event)))
            }
            onConnected(sendEvent)
            val pingJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()).launch {
                while (isActive) {
                    delay(25_000)
                    sendEvent(WsEvent(type = "ping"))
                }
            }
            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val event = runCatching { json.decodeFromString(WsEvent.serializer(), text) }.getOrNull() ?: continue
                    if (event.type == "pong") continue
                    onEvent(event)
                }
            } finally {
                pingJob.cancel()
            }
        }
    }

    private suspend inline fun <reified T> send(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        auth: Boolean = true,
        workspaceId: String? = null,
    ): T {
        try {
            val current = session.current()
            val response = http.request(current.baseUrl.trimEnd('/') + path) {
                this.method = method
                if (auth) {
                    val token = current.token ?: throw ApiException("Bạn cần đăng nhập lại")
                    header(HttpHeaders.Authorization, "Bearer $token")
                    (workspaceId ?: current.workspace?.id)?.let { header("X-Workspace-Id", it) }
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            trackClock(response)
            if (!response.status.isSuccess()) {
                if (auth && response.status.value == 401) {
                    session.expire()
                    throw ApiException("Phiên đăng nhập đã hết hạn, hãy đăng nhập lại", 401)
                }
                if (response.status.value == 405 && path.contains("/me")) {
                    throw ApiException("Máy chủ chưa cập nhật API hồ sơ (405). Hãy restart server backend rồi thử lại.")
                }
                if (response.status.value == 404) {
                    throw ApiException(
                        "Không tìm thấy API (${path}). Kiểm tra địa chỉ máy chủ và Render còn Live; mở /health trên trình duyệt phải ra ok.",
                        404,
                    )
                }
                val message = runCatching { response.body<ErrorResponse>().error }.getOrNull()
                    ?: "Yêu cầu thất bại (${response.status.value})"
                // Stale workspace after Render DB wipe / redeploy — drop local selection.
                if (response.status.value == 403 && workspaceId == null &&
                    message.contains("không thuộc workspace", ignoreCase = true)
                ) {
                    session.clearWorkspace()
                }
                throw ApiException(message, response.status.value)
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
    val detail = error.message?.lineSequence()?.firstOrNull()?.take(80).orEmpty()
    val hint =
        "Thử mở URL server trong Chrome trên emulator. Máy công ty/proxy có thể chặn emulator dù điện thoại vẫn vào được."
    return if (detail.isBlank()) {
        "Không kết nối được máy chủ. $hint"
    } else {
        "Không kết nối được máy chủ ($detail). $hint"
    }
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "Có lỗi xảy ra"
    else -> "Có lỗi xảy ra"
}
