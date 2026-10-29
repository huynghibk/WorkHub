package com.workhub.server

import com.workhub.shared.AuthResponse
import com.workhub.shared.CommentWrite
import com.workhub.shared.FolderWrite
import com.workhub.shared.JoinWorkspace
import com.workhub.shared.ListWrite
import com.workhub.shared.MemberAdd
import com.workhub.shared.RoleUpdate
import com.workhub.shared.SpaceWrite
import com.workhub.shared.ChannelDto
import com.workhub.shared.ChannelWrite
import com.workhub.shared.DirectWrite
import com.workhub.shared.GroupWrite
import com.workhub.shared.IdList
import com.workhub.shared.NotificationDto
import com.workhub.shared.CompanyNode
import com.workhub.shared.DepartmentNode
import com.workhub.shared.DivisionNode
import com.workhub.shared.NamedCreate
import com.workhub.shared.OrgTree
import com.workhub.shared.TeamNode
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
import com.workhub.shared.CommentDto
import com.workhub.shared.FolderNode
import com.workhub.shared.SearchHit
import com.workhub.shared.SearchResult
import com.workhub.shared.SpaceNode
import com.workhub.shared.WorkListNode
import com.workhub.shared.WorkTree
import com.workhub.shared.WorkspaceCreate
import com.workhub.shared.WorkspaceDto
import com.workhub.shared.WsEvent
import org.jetbrains.exposed.sql.ResultRow
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val json = Json { ignoreUnknownKeys = true }

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("WorkHub bind host=0.0.0.0 port=$port")
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    install(CallLogging)
    install(ContentNegotiation) {
        json(json)
    }
    install(WebSockets)
    install(CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader("X-Workspace-Id")
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Options)
    }
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "workhub"
            verifier(JwtTokens.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
        }
    }
    install(StatusPages) {
        exception<AppException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message ?: "Yêu cầu không hợp lệ"))
        }
        exception<Throwable> { call, cause ->
            if (cause is AppException) {
                call.respond(cause.status, ErrorResponse(cause.message ?: "Yêu cầu không hợp lệ"))
            } else {
                call.application.environment.log.error("Request failed", cause)
                val detail = cause.message?.lineSequence()?.firstOrNull()?.take(160)
                val text = if (detail.isNullOrBlank()) "Lỗi máy chủ" else "Lỗi máy chủ: $detail"
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(text))
            }
        }
    }
    routing {
        get("/") {
            call.respondText("WorkHub API is running", ContentType.Text.Plain)
        }
        // Render đôi khi probe HEAD /
        head("/") {
            call.respond(HttpStatusCode.OK)
        }
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }
        head("/health") {
            call.respond(HttpStatusCode.OK)
        }
        get("/healthz") {
            call.respondText("ok", ContentType.Text.Plain)
        }
        post("/auth/register") {
            val response = Repository.register(call.receive())
            call.respond(HttpStatusCode.Created, response)
        }
        post("/auth/login") {
            call.respond(Repository.login(call.receive()))
        }
        authenticate("auth-jwt") {
            get("/workspaces") { call.respond(Repository.myWorkspaces(call.userId())) }
            post("/workspaces") {
                val created = Repository.createWorkspace(call.userId(), call.receive())
                call.respond(HttpStatusCode.Created, created)
            }
            post("/workspaces/join") {
                call.respond(Repository.joinWorkspace(call.userId(), call.receive()))
            }
            get("/org/tree") { call.respond(Repository.orgTree(call.companyId())) }
            route("/admin") {
                post("/divisions") {
                    call.requireWorkspaceAdmin()
                    call.respond(HttpStatusCode.Created, Repository.createDivision(call.companyId(), call.receive()))
                }
                post("/departments") {
                    call.requireWorkspaceAdmin()
                    call.respond(HttpStatusCode.Created, Repository.createDepartment(call.companyId(), call.receive()))
                }
                post("/teams") {
                    call.requireWorkspaceAdmin()
                    call.respond(HttpStatusCode.Created, Repository.createTeam(call.companyId(), call.receive()))
                }
            }
            put("/members/{memberId}/role") {
                call.requireWorkspaceAdmin()
                val target = call.parameters["memberId"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu thành viên")
                Repository.setMemberRole(call.companyId(), target, call.receive<RoleUpdate>().role)
                call.respond(HttpStatusCode.NoContent)
            }
            route("/me") {
                get { call.respond(Repository.profile(call.userId())) }
                put { call.respond(Repository.updateProfile(call.userId(), call.receive())) }
                post { call.respond(Repository.updateProfile(call.userId(), call.receive())) }
                post("/profile") {
                    call.respond(Repository.updateProfile(call.userId(), call.receive()))
                }
            }
            // Endpoint rõ ràng — client ưu tiên path này.
            post("/profile") {
                call.respond(Repository.updateProfile(call.userId(), call.receive()))
            }
            put("/profile") {
                call.respond(Repository.updateProfile(call.userId(), call.receive()))
            }
            get("/users") { call.respond(Repository.members(call.companyId())) }
            get("/work") { call.respond(Repository.workTree(call.companyId(), call.userId(), call.memberRole())) }
            get("/search") {
                val query = call.request.queryParameters["q"].orEmpty()
                call.respond(Repository.search(call.companyId(), call.userId(), call.memberRole(), query))
            }
            post("/spaces") {
                call.requireManager()
                call.respond(HttpStatusCode.Created, Repository.createSpace(call.companyId(), call.userId(), call.receive()))
            }
            post("/spaces/{spaceId}/members") {
                call.requireManager()
                val spaceId = call.parameters["spaceId"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu khu vực")
                Repository.addSpaceMember(call.companyId(), spaceId, call.receive<MemberAdd>().userId)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/folders") {
                call.requireManager()
                call.respond(HttpStatusCode.Created, Repository.createFolder(call.companyId(), call.userId(), call.memberRole(), call.receive()))
            }
            post("/lists") {
                call.requireManager()
                call.respond(HttpStatusCode.Created, Repository.createList(call.companyId(), call.userId(), call.memberRole(), call.receive()))
            }
            route("/tasks") {
                get { call.respond(Repository.tasks(call.companyId(), call.userId(), call.memberRole())) }
                post {
                    val created = Repository.createTask(call.companyId(), call.userId(), call.memberRole(), call.receive())
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    call.respond(Repository.updateTask(call.companyId(), call.userId(), call.memberRole(), call.taskId(), call.receive()))
                }
                delete("/{id}") {
                    Repository.deleteTask(call.companyId(), call.userId(), call.memberRole(), call.taskId())
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/{id}/comments") { call.respond(Repository.comments(call.companyId(), call.userId(), call.memberRole(), call.taskId())) }
                post("/{id}/comments") {
                    val created = Repository.addComment(call.companyId(), call.userId(), call.memberRole(), call.taskId(), call.receive<CommentWrite>().body)
                    call.respond(HttpStatusCode.Created, created)
                }
            }
            route("/notes") {
                get { call.respond(Repository.notes(call.companyId())) }
                post {
                    val created = Repository.createNote(call.companyId(), call.userId(), call.receive())
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    call.respond(Repository.updateNote(call.companyId(), call.userId(), call.memberRole(), call.noteId(), call.receive()))
                }
                delete("/{id}") {
                    Repository.deleteNote(call.companyId(), call.userId(), call.memberRole(), call.noteId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
            get("/notifications") {
                call.respond(Repository.unreadNotifications(call.companyId(), call.userId()))
            }
            post("/notifications/read") {
                val ids = runCatching { call.receive<IdList>().ids }.getOrDefault(emptyList())
                Repository.markNotificationsRead(call.companyId(), call.userId(), ids)
                call.respond(HttpStatusCode.NoContent)
            }
            route("/channels") {
                get { call.respond(Repository.channels(call.companyId(), call.userId(), call.memberRole())) }
                post("/direct") {
                    val created = Repository.openDirect(call.companyId(), call.userId(), call.receive<DirectWrite>().userId)
                    call.respond(HttpStatusCode.Created, created)
                }
                post("/group") {
                    val body = call.receive<GroupWrite>()
                    val created = Repository.openGroup(call.companyId(), call.userId(), body.name, body.memberIds)
                    call.respond(HttpStatusCode.Created, created)
                }
                post {
                    call.requireManager()
                    val created = Repository.createChannel(call.companyId(), call.userId(), call.receive())
                    call.respond(HttpStatusCode.Created, created)
                }
                post("/{id}/members") {
                    val memberId = call.receive<MemberAdd>().userId
                    Repository.addChannelMember(call.companyId(), call.userId(), call.memberRole(), call.channelId(), memberId)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/{id}/messages") {
                    call.respond(Repository.messages(call.companyId(), call.userId(), call.memberRole(), call.channelId(), call.request.queryParameters["parent"]))
                }
                post("/{id}/messages") {
                    val body = call.receive<MessageWrite>()
                    val saved = Repository.addMessage(call.companyId(), call.userId(), call.memberRole(), call.channelId(), body.body, body.parentId)
                    ChatHub.publish(call.companyId(), json.encodeToString(WsEvent.serializer(), WsEvent("message", saved)))
                    call.respond(HttpStatusCode.Created, saved)
                }
                post("/{id}/messages/{messageId}/task") {
                    val messageId = call.parameters["messageId"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu tin nhắn")
                    try {
                        val created = Repository.messageToTask(call.companyId(), call.userId(), call.memberRole(), call.channelId(), messageId)
                        call.respond(HttpStatusCode.Created, created)
                    } catch (error: AppException) {
                        throw error
                    } catch (error: Exception) {
                        call.application.environment.log.error("messageToTask failed", error)
                        val detail = error.message?.lineSequence()?.firstOrNull()?.take(160)
                        throw AppException(HttpStatusCode.InternalServerError, detail ?: "Lỗi máy chủ")
                    }
                }
            }
        }
        webSocket("/ws") {
            val token = call.request.headers[io.ktor.http.HttpHeaders.Authorization]
                ?.removePrefix("Bearer")
                ?.trim()
                ?: call.request.queryParameters["token"]
            val jwt = token?.let(JwtTokens::verify)
            if (jwt == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }
            val userId = jwt.getClaim("userId").asString()
            val workspaceId = call.request.headers["X-Workspace-Id"]?.trim().orEmpty()
            if (userId.isNullOrBlank() || Repository.memberRole(userId, workspaceId) == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }
            val connection = ChatHub.join(workspaceId, userId)
            val outbound = launch {
                connection.outgoing.consumeEach { frame -> send(frame) }
            }
            try {
                incoming.consumeEach { }
            } finally {
                outbound.cancel()
                ChatHub.leave(connection)
            }
        }
    }
}

private fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()

private fun ApplicationCall.companyId(): String {
    val workspaceId = request.headers["X-Workspace-Id"]?.trim().orEmpty()
    if (workspaceId.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "Chưa chọn workspace")
    if (Repository.memberRole(userId(), workspaceId) == null) {
        throw AppException(HttpStatusCode.Forbidden, "Bạn không thuộc workspace này")
    }
    return workspaceId
}

private fun ApplicationCall.memberRole(): String =
    Repository.memberRole(userId(), companyId())
        ?: throw AppException(HttpStatusCode.Forbidden, "Bạn không thuộc workspace này")

private fun ApplicationCall.requireWorkspaceAdmin() {
    if (memberRole() != "ADMIN") {
        throw AppException(HttpStatusCode.Forbidden, "Chỉ quản trị viên của workspace được thực hiện")
    }
}

private fun ApplicationCall.requireManager() {
    val role = memberRole()
    if (role != "ADMIN" && role != "SUPERVISOR") {
        throw AppException(HttpStatusCode.Forbidden, "Chỉ quản trị hoặc giám sát được thực hiện")
    }
}

private fun ApplicationCall.taskId(): String = parameters["id"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu mã công việc")
private fun ApplicationCall.noteId(): String = parameters["id"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu mã ghi chú")
private fun ApplicationCall.channelId(): String = parameters["id"] ?: throw AppException(HttpStatusCode.BadRequest, "Thiếu mã kênh chat")

class AppException(val status: HttpStatusCode, message: String) : RuntimeException(message)

private class Connection(
    val companyId: String,
    val userId: String,
    val outgoing: Channel<Frame> = Channel(Channel.UNLIMITED),
)

private object ChatHub {
    private val rooms = ConcurrentHashMap<String, MutableSet<Connection>>()

    fun join(companyId: String, userId: String): Connection {
        val connection = Connection(companyId, userId)
        rooms.computeIfAbsent(companyId) { java.util.concurrent.CopyOnWriteArraySet() }.add(connection)
        return connection
    }

    fun leave(connection: Connection) {
        rooms[connection.companyId]?.remove(connection)
        connection.outgoing.close()
    }

    fun publish(companyId: String, payload: String) {
        rooms[companyId]?.forEach { connection ->
            connection.outgoing.trySend(Frame.Text(payload))
        }
    }

    fun publishTo(companyId: String, userId: String, payload: String) {
        rooms[companyId]?.filter { it.userId == userId }?.forEach { connection ->
            connection.outgoing.trySend(Frame.Text(payload))
        }
    }
}

private data class PendingNotice(val userId: String, val notice: NotificationDto)

private fun deliverNotices(companyId: String, notices: List<PendingNotice>) {
    notices.forEach { pending ->
        val payload = json.encodeToString(
            WsEvent.serializer(),
            WsEvent(type = "notification", notification = pending.notice),
        )
        ChatHub.publishTo(companyId, pending.userId, payload)
    }
}

private object Repository {
    fun register(request: RegisterRequest): AuthResponse {
        val displayName = request.name.trimmed("Tên", 80)
        val handle = request.username.normalizedUsername()
        val emailAddress = request.email.normalizedEmail()
        val password = request.password.requirePassword()
        val hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
        return db {
            if (Users.select { Users.username eq handle }.any()) {
                throw AppException(HttpStatusCode.Conflict, "Username đã được dùng")
            }
            if (Users.select { Users.email eq emailAddress }.any()) {
                throw AppException(HttpStatusCode.Conflict, "Email đã được dùng")
            }
            val userId = UUID.randomUUID().toString()
            Users.insert {
                it[id] = userId
                it[companyId] = "none"
                it[Users.name] = displayName
                it[Users.username] = handle
                it[Users.email] = emailAddress
                it[passwordHash] = hash
                it[role] = "MEMBER"
                it[createdAt] = System.currentTimeMillis()
            }
            AuthResponse(JwtTokens.issue(userId), accountDto(userId))
        }
    }

    fun login(request: LoginRequest): AuthResponse = db {
        val raw = request.identifier()
        if (raw.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "Hãy nhập email hoặc username")
        val row = findAccount(raw)
            ?: throw AppException(HttpStatusCode.Unauthorized, "Tài khoản hoặc mật khẩu không đúng")
        val matches = org.mindrot.jbcrypt.BCrypt.checkpw(request.password, row[Users.passwordHash])
        if (!matches) throw AppException(HttpStatusCode.Unauthorized, "Tài khoản hoặc mật khẩu không đúng")
        AuthResponse(JwtTokens.issue(row[Users.id]), accountDto(row[Users.id]))
    }

    fun profile(userId: String): UserDto = db { accountDto(userId) }

    fun updateProfile(userId: String, request: ProfileUpdate): UserDto = db {
        Users.select { Users.id eq userId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy tài khoản")
        val nextName = request.name?.let { it.trimmed("Tên", 80) }
        val nextUsername = request.username?.let { it.normalizedUsername() }
        if (nextUsername != null) {
            val owner = Users.select { Users.username eq nextUsername }.singleOrNull()
            if (owner != null && owner[Users.id] != userId) {
                throw AppException(HttpStatusCode.Conflict, "Username đã được dùng")
            }
        }
        val nextAvatar = request.avatarDataUrl?.let { cleanAvatarDataUrl(it) }
        Users.update({ Users.id eq userId }) {
            if (nextName != null) it[Users.name] = nextName
            if (nextUsername != null) it[Users.username] = nextUsername
            if (nextAvatar != null) {
                it[Users.avatarDataUrl] = nextAvatar.ifBlank { null }
            }
        }
        accountDto(userId)
    }

    private fun findAccount(raw: String): ResultRow? {
        val trimmed = raw.trim()
        if (trimmed.contains("@")) {
            val email = trimmed.lowercase()
            return Users.select { Users.email eq email }.singleOrNull()
                ?: runCatching { trimmed.normalizedEmail() }.getOrNull()?.let { normalized ->
                    Users.select { Users.email eq normalized }.singleOrNull()
                }
        }
        val handle = trimmed.lowercase()
        if (handle.isEmpty()) return null
        Users.select { Users.username eq handle }.singleOrNull()?.let { return it }
        // Fallback: một số bản ghi cũ lưu username khác hoa/thường
        return Users.selectAll().firstOrNull { row ->
            row[Users.username]?.equals(handle, ignoreCase = true) == true
        }
    }

    private fun cleanAvatarDataUrl(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("data:image/") || !value.contains(";base64,")) {
            throw AppException(HttpStatusCode.BadRequest, "Avatar không hợp lệ")
        }
        if (value.length > 180_000) {
            throw AppException(HttpStatusCode.BadRequest, "Ảnh avatar quá lớn, hãy chọn ảnh nhỏ hơn")
        }
        return value
    }

    fun memberRole(userId: String, workspaceId: String): String? = db {
        Memberships.select {
            (Memberships.userId eq userId) and (Memberships.workspaceId eq workspaceId)
        }.singleOrNull()?.get(Memberships.role)
    }

    fun myWorkspaces(userId: String): List<WorkspaceDto> = db {
        Memberships.select { Memberships.userId eq userId }.mapNotNull { row ->
            val company = Companies.select { Companies.id eq row[Memberships.workspaceId] }.singleOrNull()
                ?: return@mapNotNull null
            workspaceDto(company, row[Memberships.role])
        }.sortedBy { it.name.lowercase() }
    }

    fun createWorkspace(userId: String, request: WorkspaceCreate): WorkspaceDto = db {
        val companyName = request.name.trimmed("Tên workspace", 120)
        val workspaceId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val code = inviteCode()
        Companies.insert {
            it[id] = workspaceId
            it[name] = companyName
            it[inviteCode] = code
            it[createdAt] = now
        }
        Channels.insert {
            it[id] = UUID.randomUUID().toString()
            it[companyId] = workspaceId
            it[Channels.name] = "Chung"
            it[isPrivate] = false
            it[createdAt] = now
        }
        seedWorkspaceWork(workspaceId)
        Memberships.insert {
            it[id] = UUID.randomUUID().toString()
            it[Memberships.userId] = userId
            it[Memberships.workspaceId] = workspaceId
            it[role] = "ADMIN"
            it[createdAt] = now
        }
        val company = Companies.select { Companies.id eq workspaceId }.single()
        workspaceDto(company, "ADMIN")
    }

    fun joinWorkspace(userId: String, request: JoinWorkspace): WorkspaceDto = db {
        val code = request.inviteCode.trim().uppercase()
        if (code.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "Hãy nhập mã mời")
        val company = Companies.select { Companies.inviteCode eq code }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Mã mời không đúng")
        val workspaceId = company[Companies.id]
        val existing = Memberships.select {
            (Memberships.userId eq userId) and (Memberships.workspaceId eq workspaceId)
        }.singleOrNull()
        if (existing != null) return@db workspaceDto(company, existing[Memberships.role])
        Memberships.insert {
            it[id] = UUID.randomUUID().toString()
            it[Memberships.userId] = userId
            it[Memberships.workspaceId] = workspaceId
            it[role] = "MEMBER"
            it[createdAt] = System.currentTimeMillis()
        }
        workspaceDto(company, "MEMBER")
    }

    fun setMemberRole(workspaceId: String, targetUserId: String, newRole: String) = db {
        if (newRole !in setOf("ADMIN", "SUPERVISOR", "MEMBER")) {
            throw AppException(HttpStatusCode.BadRequest, "Vai trò không hợp lệ")
        }
        val target = Memberships.select {
            (Memberships.userId eq targetUserId) and (Memberships.workspaceId eq workspaceId)
        }.singleOrNull() ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy thành viên")
        if (target[Memberships.role] == "ADMIN" && newRole != "ADMIN") {
            val admins = Memberships.select {
                (Memberships.workspaceId eq workspaceId) and (Memberships.role eq "ADMIN")
            }.count()
            if (admins <= 1) {
                throw AppException(HttpStatusCode.BadRequest, "Workspace cần ít nhất một quản trị viên")
            }
        }
        Memberships.update({ Memberships.id eq target[Memberships.id] }) {
            it[role] = newRole
        }
    }

    fun members(companyId: String): List<MemberDto> = db {
        Memberships.select { Memberships.workspaceId eq companyId }.map { row ->
            val user = Users.select { Users.id eq row[Memberships.userId] }.single()
            MemberDto(user[Users.id], user[Users.name], user[Users.email], row[Memberships.role])
        }.sortedBy { it.name.lowercase() }
    }

    fun workTree(companyId: String, userId: String, role: String): WorkTree = db {
        val spaces = visibleSpaces(companyId, userId, role)
        WorkTree(spaces.map { space -> spaceNode(space) })
    }

    fun createSpace(companyId: String, userId: String, request: SpaceWrite): com.workhub.shared.SpaceNode = db {
        val spaceName = request.name.trimmed("Tên khu vực", 120)
        val id = UUID.randomUUID().toString()
        Spaces.insert {
            it[Spaces.id] = id
            it[Spaces.companyId] = companyId
            it[name] = spaceName
            it[isPrivate] = request.isPrivate
            it[createdAt] = System.currentTimeMillis()
        }
        if (request.isPrivate) addSpaceMemberRow(id, userId)
        spaceNode(Spaces.select { Spaces.id eq id }.single())
    }

    fun addSpaceMember(companyId: String, spaceId: String, memberId: String) = db {
        val space = Spaces.select { Spaces.id eq spaceId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy khu vực")
        if (space[Spaces.companyId] != companyId) throw AppException(HttpStatusCode.NotFound, "Không tìm thấy khu vực")
        if (Memberships.select { (Memberships.userId eq memberId) and (Memberships.workspaceId eq companyId) }.empty()) {
            throw AppException(HttpStatusCode.BadRequest, "Người này không thuộc workspace")
        }
        addSpaceMemberRow(spaceId, memberId)
    }

    fun createFolder(companyId: String, userId: String, role: String, request: FolderWrite): com.workhub.shared.FolderNode = db {
        val space = requireVisibleSpace(companyId, userId, role, request.spaceId)
        val folderName = request.name.trimmed("Tên thư mục", 120)
        val id = UUID.randomUUID().toString()
        Folders.insert {
            it[Folders.id] = id
            it[Folders.companyId] = companyId
            it[spaceId] = space[Spaces.id]
            it[name] = folderName
            it[createdAt] = System.currentTimeMillis()
        }
        com.workhub.shared.FolderNode(id, folderName)
    }

    fun createList(companyId: String, userId: String, role: String, request: ListWrite): com.workhub.shared.WorkListNode = db {
        val space = requireVisibleSpace(companyId, userId, role, request.spaceId)
        val folderId = request.folderId?.trim().orEmpty().ifBlank { null }
        if (folderId != null) {
            val folder = Folders.select { Folders.id eq folderId }.singleOrNull()
                ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy thư mục")
            if (folder[Folders.spaceId] != space[Spaces.id]) {
                throw AppException(HttpStatusCode.NotFound, "Không tìm thấy thư mục")
            }
        }
        val listName = request.name.trimmed("Tên danh sách", 120)
        val id = UUID.randomUUID().toString()
        WorkLists.insert {
            it[WorkLists.id] = id
            it[WorkLists.companyId] = companyId
            it[WorkLists.spaceId] = space[Spaces.id]
            it[WorkLists.folderId] = folderId
            it[name] = listName
            it[createdAt] = System.currentTimeMillis()
        }
        com.workhub.shared.WorkListNode(id, listName, folderId)
    }

    fun tasks(companyId: String, userId: String, role: String): List<TaskDto> = db {
        val visible = visibleListIds(companyId, userId, role)
        val names = memberNames(companyId)
        val lists = listNames(companyId)
        val rows = Tasks.select { Tasks.companyId eq companyId }
            .orderBy(Tasks.updatedAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toTask(names, lists) }
            .filter { it.listId == null || it.listId in visible }
        val counts = rows.groupingBy { it.parentId }.eachCount()
        val grouped = TaskAssignees.selectAll().groupBy({ it[TaskAssignees.taskId] }, { it[TaskAssignees.userId] })
        rows.map { task ->
            val ids = grouped[task.id].orEmpty().ifEmpty { listOfNotNull(task.assigneeId) }
            task.copy(
                subtaskCount = counts[task.id] ?: 0,
                assigneeIds = ids,
                assigneeNames = ids.mapNotNull { names[it] },
                assigneeId = ids.firstOrNull(),
                assigneeName = ids.firstOrNull()?.let { names[it] },
            )
        }
    }

    fun createTask(companyId: String, userId: String, role: String, request: TaskWrite): TaskDto {
        val (created, notices) = db {
            val parentKey = request.parentId?.trim()?.ifBlank { null }
            if (parentKey != null) {
                ensureCanEditTask(requireTask(companyId, userId, role, parentKey), userId)
            }
            val listId = resolveListId(companyId, userId, role, request.listId, request.parentId)
            val created = insertTask(companyId, userId, listId, request)
            created to queueAssignmentNotices(companyId, userId, created.title, created.assigneeIds)
        }
        deliverNotices(companyId, notices)
        return created
    }

    fun updateTask(companyId: String, userId: String, role: String, taskId: String, request: TaskWrite): TaskDto {
        val result = db {
            val existing = requireTask(companyId, userId, role, taskId)
            ensureCanEditTask(existing, userId)
            val beforeAssignees = TaskAssignees.select { TaskAssignees.taskId eq existing[Tasks.id] }
                .map { it[TaskAssignees.userId] }
                .ifEmpty { listOfNotNull(existing[Tasks.assigneeId]) }
            request.cleanDates()
            val nextTitle = request.cleanTitle()
            val nextDescription = request.cleanDescription()
            val nextStatus = request.cleanStatus()
            val people = request.cleanAssignees(companyId)
            val nextPriority = request.cleanPriority()
            val wasDone = existing[Tasks.status] == "DONE"
            Tasks.update({ Tasks.id eq existing[Tasks.id] }) {
                it[title] = nextTitle
                it[description] = nextDescription
                it[status] = nextStatus
                it[priority] = nextPriority
                it[assigneeId] = people.firstOrNull()
                it[dueAt] = request.dueAt
                it[startAt] = request.startAt
                it[updatedAt] = System.currentTimeMillis()
            }
            replaceAssignees(existing[Tasks.id], people)
            if (request.updateLinks) {
                applyLinks(companyId, taskId, request.parentId?.trim()?.ifBlank { null }, request.childIds)
            }
            val done = if (!wasDone && nextStatus == "DONE") announceDone(companyId, userId, nextTitle) else null
            val notices = queueAssignmentNotices(companyId, userId, nextTitle, people.filter { it !in beforeAssignees }) +
                (done?.second ?: emptyList())
            val names = memberNames(companyId)
            Triple(
                withPeople(Tasks.select { Tasks.id eq taskId }.single().toTask(names, listNames(companyId)), names),
                done?.first,
                notices,
            )
        }
        val (updated, doneMessage, notices) = result
        if (doneMessage != null) {
            ChatHub.publish(companyId, json.encodeToString(WsEvent.serializer(), WsEvent("message", doneMessage)))
        }
        deliverNotices(companyId, notices)
        return updated
    }

    fun deleteTask(companyId: String, userId: String, role: String, taskId: String) = db {
        val existing = requireTask(companyId, userId, role, taskId)
        ensureCanEditTask(existing, userId)
        val childIds = Tasks.select { Tasks.parentId eq existing[Tasks.id] }.map { it[Tasks.id] }
        (childIds + existing[Tasks.id]).forEach { id ->
            TaskComments.deleteWhere { with(it) { TaskComments.taskId eq id } }
            TaskAssignees.deleteWhere { with(it) { TaskAssignees.taskId eq id } }
        }
        Tasks.deleteWhere { with(it) { Tasks.parentId eq existing[Tasks.id] } }
        Tasks.deleteWhere { with(it) { Tasks.id eq existing[Tasks.id] } }
    }

    fun comments(companyId: String, userId: String, role: String, taskId: String): List<com.workhub.shared.CommentDto> = db {
        requireTask(companyId, userId, role, taskId)
        val names = memberNames(companyId)
        TaskComments.select { TaskComments.taskId eq taskId }
            .orderBy(TaskComments.createdAt to org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { row ->
                com.workhub.shared.CommentDto(
                    id = row[TaskComments.id],
                    taskId = taskId,
                    authorId = row[TaskComments.authorId],
                    authorName = names[row[TaskComments.authorId]] ?: "Ẩn",
                    body = row[TaskComments.body],
                    createdAt = row[TaskComments.createdAt],
                )
            }
    }

    fun addComment(companyId: String, userId: String, role: String, taskId: String, rawBody: String): com.workhub.shared.CommentDto = db {
        ensureCanEditTask(requireTask(companyId, userId, role, taskId), userId)
        val body = rawBody.trim()
        if (body.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "Bình luận trống")
        if (body.length > 2000) throw AppException(HttpStatusCode.BadRequest, "Bình luận tối đa 2000 ký tự")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        TaskComments.insert {
            it[TaskComments.id] = id
            it[TaskComments.taskId] = taskId
            it[authorId] = userId
            it[TaskComments.body] = body
            it[createdAt] = now
        }
        val name = Users.select { Users.id eq userId }.single()[Users.name]
        com.workhub.shared.CommentDto(id, taskId, userId, name, body, now)
    }

    fun search(companyId: String, userId: String, role: String, rawQuery: String): com.workhub.shared.SearchResult = db {
        val query = rawQuery.trim()
        if (query.length < 2) throw AppException(HttpStatusCode.BadRequest, "Nhập ít nhất 2 ký tự")
        val hits = mutableListOf<com.workhub.shared.SearchHit>()
        val visible = visibleListIds(companyId, userId, role)
        Tasks.select { Tasks.companyId eq companyId }.forEach { row ->
            val listId = row[Tasks.listId]
            if (listId != null && listId !in visible) return@forEach
            val title = row[Tasks.title]
            val description = row[Tasks.description]
            if (title.contains(query, true) || description.contains(query, true)) {
                hits += com.workhub.shared.SearchHit("task", row[Tasks.id], title, description.take(120))
            }
        }
        Notes.select { Notes.companyId eq companyId }.forEach { row ->
            val title = row[Notes.title]
            val body = row[Notes.body]
            if (title.contains(query, true) || body.contains(query, true)) {
                hits += com.workhub.shared.SearchHit("note", row[Notes.id], title, body.take(120))
            }
        }
        Channels.select { Channels.companyId eq companyId }.forEach { channel ->
            if (!canSeeChannel(userId, role, channel)) return@forEach
            val channelName = displayChannel(channel, userId).name
            Messages.select { Messages.channelId eq channel[Channels.id] }.forEach { row ->
                val body = row[Messages.body]
                if (body.contains(query, true)) {
                    hits += com.workhub.shared.SearchHit(
                        "message",
                        row[Messages.id],
                        channelName,
                        body.take(120),
                        channel[Channels.id],
                    )
                }
            }
        }
        com.workhub.shared.SearchResult(hits.take(40))
    }

    fun notes(companyId: String): List<NoteDto> = db {
        val names = memberNames(companyId)
        Notes.select { Notes.companyId eq companyId }
            .orderBy(Notes.updatedAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toNote(names) }
    }

    fun createNote(companyId: String, userId: String, request: NoteWrite): NoteDto = db {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        Notes.insert {
            it[Notes.id] = id
            it[Notes.companyId] = companyId
            it[authorId] = userId
            it[title] = request.cleanTitle()
            it[body] = request.cleanBody()
            it[dueAt] = request.dueAt
            it[createdAt] = now
            it[updatedAt] = now
        }
        Notes.select { Notes.id eq id }.single().toNote(memberNames(companyId))
    }

    fun updateNote(companyId: String, userId: String, role: String, noteId: String, request: NoteWrite): NoteDto = db {
        val existing = Notes.select { (Notes.id eq noteId) and (Notes.companyId eq companyId) }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy ghi chú")
        ensureCanChange(role, existing[Notes.authorId], userId)
        Notes.update({ Notes.id eq existing[Notes.id] }) {
            it[title] = request.cleanTitle()
            it[body] = request.cleanBody()
            it[dueAt] = request.dueAt
            it[updatedAt] = System.currentTimeMillis()
        }
        Notes.select { Notes.id eq noteId }.single().toNote(memberNames(companyId))
    }

    fun deleteNote(companyId: String, userId: String, role: String, noteId: String) = db {
        val existing = Notes.select { (Notes.id eq noteId) and (Notes.companyId eq companyId) }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy ghi chú")
        ensureCanChange(role, existing[Notes.authorId], userId)
        Notes.deleteWhere { with(it) { Notes.id eq existing[Notes.id] } }
    }

    fun channels(companyId: String, userId: String, role: String): List<ChannelDto> = db {
        ensureGeneralChannel(companyId)
        Channels.select { Channels.companyId eq companyId }
            .orderBy(Channels.createdAt to org.jetbrains.exposed.sql.SortOrder.ASC)
            .filter { canSeeChannel(userId, role, it) }
            .map { displayChannel(it, userId) }
    }

    fun openDirect(companyId: String, userId: String, otherId: String): ChannelDto = db {
        val peerId = otherId.trim()
        if (peerId == userId) throw AppException(HttpStatusCode.BadRequest, "Không thể nhắn với chính mình")
        if (Memberships.select { (Memberships.userId eq peerId) and (Memberships.workspaceId eq companyId) }.empty()) {
            throw AppException(HttpStatusCode.BadRequest, "Người này không thuộc workspace")
        }
        val key = directKey(userId, peerId)
        val existing = Channels.select { (Channels.companyId eq companyId) and (Channels.name eq key) }.singleOrNull()
        if (existing != null) {
            addChannelMemberRow(existing[Channels.id], userId)
            addChannelMemberRow(existing[Channels.id], peerId)
            return@db displayChannel(existing, userId)
        }
        val id = UUID.randomUUID().toString()
        Channels.insert {
            it[Channels.id] = id
            it[Channels.companyId] = companyId
            it[Channels.name] = key
            it[isPrivate] = true
            it[kind] = "DIRECT"
            it[createdAt] = System.currentTimeMillis()
        }
        addChannelMemberRow(id, userId)
        addChannelMemberRow(id, peerId)
        displayChannel(Channels.select { Channels.id eq id }.single(), userId)
    }

    fun openGroup(companyId: String, userId: String, rawName: String, memberIds: List<String>): ChannelDto = db {
        val others = memberIds.map { it.trim() }.filter { it.isNotEmpty() && it != userId }.distinct()
        others.forEach { memberId ->
            if (Memberships.select { (Memberships.userId eq memberId) and (Memberships.workspaceId eq companyId) }.empty()) {
                throw AppException(HttpStatusCode.BadRequest, "Người này không thuộc workspace")
            }
        }
        if (others.size < 2) throw AppException(HttpStatusCode.BadRequest, "Nhóm cần ít nhất 3 người")
        val names = memberNames(companyId)
        val fallback = (listOf(userId) + others).mapNotNull { names[it] }.joinToString(", ").take(80).ifBlank { "Nhóm" }
        val channelName = rawName.trim().ifBlank { fallback }.take(80)
        val id = UUID.randomUUID().toString()
        Channels.insert {
            it[Channels.id] = id
            it[Channels.companyId] = companyId
            it[Channels.name] = channelName
            it[isPrivate] = true
            it[kind] = "GROUP"
            it[createdAt] = System.currentTimeMillis()
        }
        addChannelMemberRow(id, userId)
        others.forEach { addChannelMemberRow(id, it) }
        displayChannel(Channels.select { Channels.id eq id }.single(), userId)
    }

    fun createChannel(companyId: String, userId: String, request: ChannelWrite): ChannelDto = db {
        val channelName = request.name.trimmed("Tên kênh", 80)
        val id = UUID.randomUUID().toString()
        Channels.insert {
            it[Channels.id] = id
            it[Channels.companyId] = companyId
            it[Channels.name] = channelName
            it[isPrivate] = request.isPrivate
            it[kind] = if (request.isPrivate) "GROUP" else "CHANNEL"
            it[createdAt] = System.currentTimeMillis()
        }
        if (request.isPrivate) addChannelMemberRow(id, userId)
        displayChannel(Channels.select { Channels.id eq id }.single(), userId)
    }

    fun addChannelMember(companyId: String, actorId: String, role: String, channelId: String, memberId: String) = db {
        val channel = requireVisibleChannel(companyId, actorId, role, channelId)
        if (!channel[Channels.isPrivate]) {
            throw AppException(HttpStatusCode.BadRequest, "Kênh công khai đã gồm mọi thành viên")
        }
        if (Memberships.select { (Memberships.userId eq memberId) and (Memberships.workspaceId eq companyId) }.empty()) {
            throw AppException(HttpStatusCode.BadRequest, "Người này không thuộc workspace")
        }
        addChannelMemberRow(channel[Channels.id], memberId)
        val count = ChannelMembers.select { ChannelMembers.channelId eq channel[Channels.id] }.count()
        if (channelKind(channel) == "DIRECT" && count >= 3) {
            Channels.update({ Channels.id eq channel[Channels.id] }) {
                it[kind] = "GROUP"
                it[name] = groupLabel(channel[Channels.id])
            }
        }
    }

    fun messages(companyId: String, userId: String, role: String, channelId: String, parentId: String?): List<MessageDto> = db {
        requireVisibleChannel(companyId, userId, role, channelId)
        messageRows(channelId, parentId?.trim()?.ifBlank { null })
    }

    fun addMessage(
        companyId: String,
        userId: String,
        role: String,
        channelId: String,
        rawBody: String,
        parentId: String?,
    ): MessageDto {
        val (saved, notices) = db {
            val channel = requireVisibleChannel(companyId, userId, role, channelId)
            val body = rawBody.trim()
            if (body.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "Tin nhắn trống")
            if (body.length > 2000) throw AppException(HttpStatusCode.BadRequest, "Tin nhắn tối đa 2000 ký tự")
            val threadId = parentId?.trim()?.ifBlank { null }
            if (threadId != null) {
                val parent = Messages.select { Messages.id eq threadId }.singleOrNull()
                    ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy tin nhắn gốc")
                if (parent[Messages.channelId] != channelId || parent[Messages.parentId] != null) {
                    throw AppException(HttpStatusCode.BadRequest, "Chỉ trả lời vào tin nhắn gốc của kênh")
                }
            }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            Messages.insert {
                it[Messages.id] = id
                it[Messages.channelId] = channelId
                it[Messages.parentId] = threadId
                it[senderId] = userId
                it[Messages.body] = body
                it[createdAt] = now
            }
            val senderName = Users.select { Users.id eq userId }.singleOrNull()?.get(Users.name) ?: "Ẩn"
            val saved = MessageDto(id, channelId, userId, senderName, body, now, threadId, 0)
            saved to queueChatNotices(companyId, userId, channel, body)
        }
        deliverNotices(companyId, notices)
        return saved
    }

    fun messageToTask(companyId: String, userId: String, role: String, channelId: String, messageId: String): TaskDto = db {
        requireVisibleChannel(companyId, userId, role, channelId)
        val message = Messages.select { (Messages.id eq messageId) and (Messages.channelId eq channelId) }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy tin nhắn")
        val raw = message[Messages.body].trim()
        val title = raw.lineSequence().firstOrNull().orEmpty().trim().take(200).ifBlank { "Việc từ chat" }
        val listId = defaultListId(companyId)
        insertTask(
            companyId,
            userId,
            listId,
            TaskWrite(title = title, description = raw.take(4000).ifBlank { title }, listId = listId),
        )
    }

    fun orgTree(workspaceId: String): OrgTree = db {
        val company = Companies.select { Companies.id eq workspaceId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy workspace")
        OrgTree(
            listOf(
                CompanyNode(
                    id = workspaceId,
                    name = company[Companies.name],
                    divisions = Divisions.select { Divisions.companyId eq workspaceId }
                        .orderBy(Divisions.name to org.jetbrains.exposed.sql.SortOrder.ASC)
                        .map { division ->
                            val divisionId = division[Divisions.id]
                            DivisionNode(
                                id = divisionId,
                                name = division[Divisions.name],
                                departments = Departments.select { Departments.divisionId eq divisionId }
                                    .orderBy(Departments.name to org.jetbrains.exposed.sql.SortOrder.ASC)
                                    .map { department ->
                                        val departmentId = department[Departments.id]
                                        DepartmentNode(
                                            id = departmentId,
                                            name = department[Departments.name],
                                            teams = Teams.select { Teams.departmentId eq departmentId }
                                                .orderBy(Teams.name to org.jetbrains.exposed.sql.SortOrder.ASC)
                                                .map { TeamNode(it[Teams.id], it[Teams.name]) },
                                        )
                                    },
                            )
                        },
                ),
            ),
        )
    }

    fun createDivision(workspaceId: String, request: NamedCreate): DivisionNode = db {
        if (Companies.select { Companies.id eq workspaceId }.singleOrNull() == null) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy workspace")
        }
        val companyId = workspaceId
        val unitName = request.name.trimmed("Tên bộ phận", 120)
        val siblings = Divisions.select { Divisions.companyId eq companyId }
        if (siblings.any { it[Divisions.name].equals(unitName, ignoreCase = true) }) {
            throw AppException(HttpStatusCode.Conflict, "Bộ phận đã tồn tại")
        }
        val id = UUID.randomUUID().toString()
        Divisions.insert {
            it[Divisions.id] = id
            it[Divisions.companyId] = companyId
            it[name] = unitName
            it[createdAt] = System.currentTimeMillis()
        }
        DivisionNode(id, unitName)
    }

    fun createDepartment(workspaceId: String, request: NamedCreate): DepartmentNode = db {
        val division = Divisions.select { Divisions.id eq request.parentId.trim() }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy bộ phận")
        if (division[Divisions.companyId] != workspaceId) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy bộ phận")
        }
        val unitName = request.name.trimmed("Tên phòng", 120)
        val siblings = Departments.select { Departments.divisionId eq division[Divisions.id] }
        if (siblings.any { it[Departments.name].equals(unitName, ignoreCase = true) }) {
            throw AppException(HttpStatusCode.Conflict, "Phòng đã tồn tại")
        }
        val id = UUID.randomUUID().toString()
        Departments.insert {
            it[Departments.id] = id
            it[companyId] = division[Divisions.companyId]
            it[divisionId] = division[Divisions.id]
            it[name] = unitName
            it[createdAt] = System.currentTimeMillis()
        }
        DepartmentNode(id, unitName)
    }

    fun createTeam(workspaceId: String, request: NamedCreate): TeamNode = db {
        val department = Departments.select { Departments.id eq request.parentId.trim() }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy phòng")
        if (department[Departments.companyId] != workspaceId) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy phòng")
        }
        val unitName = request.name.trimmed("Tên nhóm", 120)
        val siblings = Teams.select { Teams.departmentId eq department[Departments.id] }
        if (siblings.any { it[Teams.name].equals(unitName, ignoreCase = true) }) {
            throw AppException(HttpStatusCode.Conflict, "Nhóm đã tồn tại")
        }
        val id = UUID.randomUUID().toString()
        Teams.insert {
            it[Teams.id] = id
            it[companyId] = department[Departments.companyId]
            it[departmentId] = department[Departments.id]
            it[name] = unitName
            it[createdAt] = System.currentTimeMillis()
        }
        TeamNode(id, unitName)
    }

    private fun visibleSpaces(companyId: String, userId: String, role: String): List<ResultRow> =
        Spaces.select { Spaces.companyId eq companyId }
            .orderBy(Spaces.name to SortOrder.ASC)
            .filter { space ->
                !space[Spaces.isPrivate] || role == "ADMIN" || spaceMember(space[Spaces.id], userId)
            }

    private fun spaceMember(spaceId: String, userId: String): Boolean =
        SpaceMembers.select { (SpaceMembers.spaceId eq spaceId) and (SpaceMembers.userId eq userId) }.any()

    private fun requireVisibleSpace(companyId: String, userId: String, role: String, spaceId: String): ResultRow {
        val space = Spaces.select { Spaces.id eq spaceId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy khu vực")
        if (space[Spaces.companyId] != companyId) throw AppException(HttpStatusCode.NotFound, "Không tìm thấy khu vực")
        val allowed = !space[Spaces.isPrivate] || role == "ADMIN" || spaceMember(spaceId, userId)
        if (!allowed) throw AppException(HttpStatusCode.Forbidden, "Khu vực này là riêng tư")
        return space
    }

    private fun spaceNode(space: ResultRow): SpaceNode {
        val spaceId = space[Spaces.id]
        val folders = Folders.select { Folders.spaceId eq spaceId }
            .orderBy(Folders.name to SortOrder.ASC)
            .map { folder ->
                val folderId = folder[Folders.id]
                FolderNode(
                    id = folderId,
                    name = folder[Folders.name],
                    lists = WorkLists.select { WorkLists.folderId eq folderId }
                        .orderBy(WorkLists.name to SortOrder.ASC)
                        .map { WorkListNode(it[WorkLists.id], it[WorkLists.name], folderId) },
                )
            }
        val loose = WorkLists.select { (WorkLists.spaceId eq spaceId) and WorkLists.folderId.isNull() }
            .orderBy(WorkLists.name to SortOrder.ASC)
            .map { WorkListNode(it[WorkLists.id], it[WorkLists.name], null) }
        return SpaceNode(spaceId, space[Spaces.name], space[Spaces.isPrivate], folders, loose)
    }

    private fun addSpaceMemberRow(spaceId: String, userId: String) {
        if (spaceMember(spaceId, userId)) return
        SpaceMembers.insert {
            it[id] = UUID.randomUUID().toString()
            it[SpaceMembers.spaceId] = spaceId
            it[SpaceMembers.userId] = userId
        }
    }

    private fun visibleListIds(companyId: String, userId: String, role: String): Set<String> {
        val spaceIds = visibleSpaces(companyId, userId, role).map { it[Spaces.id] }.toSet()
        return WorkLists.select { WorkLists.companyId eq companyId }
            .filter { it[WorkLists.spaceId] in spaceIds }
            .map { it[WorkLists.id] }
            .toSet()
    }

    private fun listNames(companyId: String): Map<String, String> =
        WorkLists.select { WorkLists.companyId eq companyId }.associate { it[WorkLists.id] to it[WorkLists.name] }

    private fun defaultListId(companyId: String): String {
        val ready = WorkLists.select { WorkLists.companyId eq companyId }.firstOrNull()?.get(WorkLists.id)
        if (ready != null) return ready
        seedWorkspaceWork(companyId)
        return WorkLists.select { WorkLists.companyId eq companyId }.firstOrNull()?.get(WorkLists.id)
            ?: throw AppException(HttpStatusCode.BadRequest, "Chưa có danh sách việc")
    }

    private fun requireTask(companyId: String, userId: String, role: String, taskId: String): ResultRow {
        val task = Tasks.select { (Tasks.id eq taskId) and (Tasks.companyId eq companyId) }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy công việc")
        val listId = task[Tasks.listId]
        if (listId != null && listId !in visibleListIds(companyId, userId, role)) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy công việc")
        }
        return task
    }

    private fun resolveListId(companyId: String, userId: String, role: String, requested: String?, parentId: String?): String {
        if (!parentId.isNullOrBlank()) {
            val parent = requireTask(companyId, userId, role, parentId)
            if (parent[Tasks.parentId] != null) {
                throw AppException(HttpStatusCode.BadRequest, "Việc con chỉ nằm một cấp dưới việc chính")
            }
            return parent[Tasks.listId] ?: defaultListId(companyId)
        }
        val listId = requested?.trim().orEmpty().ifBlank { defaultListId(companyId) }
        val list = WorkLists.select { WorkLists.id eq listId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy danh sách")
        if (list[WorkLists.companyId] != companyId) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy danh sách")
        }
        requireVisibleSpace(companyId, userId, role, list[WorkLists.spaceId])
        return list[WorkLists.id]
    }

    private fun insertTask(companyId: String, userId: String, listId: String, request: TaskWrite): TaskDto {
        request.cleanDates()
        val parentId = request.parentId?.trim()?.ifBlank { null }
        val children = request.childIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (parentId != null && children.isNotEmpty()) {
            throw AppException(HttpStatusCode.BadRequest, "Chỉ chọn một kiểu liên kết việc")
        }
        if (parentId != null) {
            val parent = Tasks.select { Tasks.id eq parentId }.singleOrNull()
                ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy việc chính")
            if (parent[Tasks.companyId] != companyId || parent[Tasks.parentId] != null) {
                throw AppException(HttpStatusCode.BadRequest, "Việc con chỉ nằm một cấp dưới việc chính")
            }
        }
        val people = request.cleanAssignees(companyId)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        Tasks.insert {
            it[Tasks.id] = id
            it[Tasks.companyId] = companyId
            it[Tasks.listId] = listId
            it[Tasks.parentId] = parentId
            it[title] = request.cleanTitle()
            it[description] = request.cleanDescription()
            it[status] = request.cleanStatus()
            it[priority] = request.cleanPriority()
            it[assigneeId] = people.firstOrNull()
            it[creatorId] = userId
            it[dueAt] = request.dueAt
            it[startAt] = request.startAt
            it[createdAt] = now
            it[updatedAt] = now
        }
        replaceAssignees(id, people)
        if (children.isNotEmpty()) {
            applyLinks(companyId, id, null, children)
        }
        val names = memberNames(companyId)
        return withPeople(Tasks.select { Tasks.id eq id }.single().toTask(names, listNames(companyId)), names)
    }

    private fun replaceAssignees(ownerId: String, userIds: List<String>) {
        TaskAssignees.deleteWhere { with(it) { TaskAssignees.taskId eq ownerId } }
        userIds.forEach { memberId ->
            TaskAssignees.insert {
                it[taskId] = ownerId
                it[userId] = memberId
            }
        }
    }

    private fun withPeople(task: TaskDto, names: Map<String, String>): TaskDto {
        val ids = TaskAssignees.select { TaskAssignees.taskId eq task.id }.map { it[TaskAssignees.userId] }
            .ifEmpty { listOfNotNull(task.assigneeId) }
        return task.copy(
            assigneeIds = ids,
            assigneeNames = ids.mapNotNull { names[it] },
            assigneeId = ids.firstOrNull(),
            assigneeName = ids.firstOrNull()?.let { names[it] },
        )
    }

    private fun applyLinks(companyId: String, taskId: String, parentId: String?, childIds: List<String>) {
        val wanted = childIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (parentId != null && wanted.isNotEmpty()) {
            throw AppException(HttpStatusCode.BadRequest, "Chỉ chọn một kiểu liên kết việc")
        }
        if (parentId != null) {
            if (parentId == taskId) {
                throw AppException(HttpStatusCode.BadRequest, "Không thể gắn việc với chính nó")
            }
            val parent = Tasks.select { Tasks.id eq parentId }.singleOrNull()
                ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy việc cha")
            if (parent[Tasks.companyId] != companyId || parent[Tasks.parentId] != null) {
                throw AppException(HttpStatusCode.BadRequest, "Việc con chỉ nằm một cấp")
            }
            Tasks.update({ Tasks.parentId eq taskId }) { it[Tasks.parentId] = null }
            Tasks.update({ Tasks.id eq taskId }) { it[Tasks.parentId] = parentId }
            return
        }
        Tasks.update({ Tasks.id eq taskId }) { it[Tasks.parentId] = null }
        Tasks.select { Tasks.parentId eq taskId }.map { it[Tasks.id] }.filter { it !in wanted }.forEach { childId ->
            Tasks.update({ Tasks.id eq childId }) { it[Tasks.parentId] = null }
        }
        wanted.forEach { childId ->
            if (childId == taskId) {
                throw AppException(HttpStatusCode.BadRequest, "Không thể gắn việc với chính nó")
            }
            val child = Tasks.select { Tasks.id eq childId }.singleOrNull()
                ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy việc con")
            if (child[Tasks.companyId] != companyId) {
                throw AppException(HttpStatusCode.BadRequest, "Việc không thuộc workspace này")
            }
            if (Tasks.select { Tasks.parentId eq childId }.any()) {
                throw AppException(HttpStatusCode.BadRequest, "Việc đã có việc con thì không gắn làm con")
            }
            Tasks.update({ Tasks.id eq childId }) { it[Tasks.parentId] = taskId }
        }
    }

    private fun announceDone(companyId: String, userId: String, title: String): Pair<MessageDto, List<PendingNotice>> {
        ensureGeneralChannel(companyId)
        val channel = Channels.select { (Channels.companyId eq companyId) and (Channels.name eq "Chung") }.first()
        val senderName = Users.select { Users.id eq userId }.singleOrNull()?.get(Users.name) ?: "Ẩn"
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val body = "$senderName đã hoàn thành: $title"
        Messages.insert {
            it[Messages.id] = id
            it[Messages.channelId] = channel[Channels.id]
            it[Messages.parentId] = null
            it[senderId] = userId
            it[Messages.body] = body
            it[createdAt] = now
        }
        val saved = MessageDto(id, channel[Channels.id], userId, senderName, body, now)
        return saved to queueChatNotices(companyId, userId, channel, body)
    }

    private fun canSeeChannel(userId: String, role: String, channel: ResultRow): Boolean {
        if (!channel[Channels.isPrivate]) return true
        val member = ChannelMembers.select {
            (ChannelMembers.channelId eq channel[Channels.id]) and (ChannelMembers.userId eq userId)
        }.any()
        val kind = channelKind(channel)
        if (kind == "DIRECT" || kind == "GROUP") return member
        if (role == "ADMIN") return true
        return member
    }

    private fun channelKind(channel: ResultRow): String {
        val stored = channel[Channels.kind]
        if (stored == "DIRECT" || stored == "GROUP" || stored == "CHANNEL") return stored
        return if (channel[Channels.isPrivate]) "GROUP" else "CHANNEL"
    }

    private fun displayChannel(channel: ResultRow, userId: String): ChannelDto {
        val kind = channelKind(channel)
        val name = if (kind == "DIRECT") {
            val peerId = ChannelMembers.select { ChannelMembers.channelId eq channel[Channels.id] }
                .map { it[ChannelMembers.userId] }
                .firstOrNull { it != userId }
            peerId?.let { Users.select { Users.id eq it }.singleOrNull()?.get(Users.name) } ?: "Tin nhắn riêng"
        } else {
            channel[Channels.name]
        }
        return ChannelDto(channel[Channels.id], name, channel[Channels.isPrivate] || kind != "CHANNEL", kind)
    }

    private fun directKey(firstId: String, secondId: String): String {
        val (first, second) = if (firstId < secondId) firstId to secondId else secondId to firstId
        return "dm:$first:$second"
    }

    private fun groupLabel(channelId: String): String {
        val names = ChannelMembers.select { ChannelMembers.channelId eq channelId }.mapNotNull { row ->
            Users.select { Users.id eq row[ChannelMembers.userId] }.singleOrNull()?.get(Users.name)
        }
        return names.joinToString(", ").take(80).ifBlank { "Nhóm" }
    }

    private fun queueChatNotices(companyId: String, senderId: String, channel: ResultRow, body: String): List<PendingNotice> {
        val channelId = channel[Channels.id]
        val kind = channelKind(channel)
        val senderName = Users.select { Users.id eq senderId }.singleOrNull()?.get(Users.name) ?: "Ẩn"
        val preview = body.replace('\n', ' ').trim().take(140)
        val title = if (kind == "DIRECT") senderName else channel[Channels.name]
        val text = if (kind == "DIRECT") preview else "$senderName: $preview"
        val recipients = if (!channel[Channels.isPrivate]) {
            Memberships.select { Memberships.workspaceId eq companyId }.map { it[Memberships.userId] }
        } else {
            ChannelMembers.select { ChannelMembers.channelId eq channelId }.map { it[ChannelMembers.userId] }
        }
        return recipients.distinct().filter { it != senderId }.map { recipient ->
            storeNotice(recipient, companyId, "chat", title, text, channelId)
        }
    }

    private fun queueAssignmentNotices(
        companyId: String,
        actorId: String,
        taskTitle: String,
        assigneeIds: List<String>,
    ): List<PendingNotice> {
        val actorName = Users.select { Users.id eq actorId }.singleOrNull()?.get(Users.name) ?: "Ẩn"
        return assigneeIds.distinct().filter { it != actorId }.map { recipient ->
            storeNotice(recipient, companyId, "task_assigned", taskTitle, actorName, null)
        }
    }

    private fun storeNotice(
        userId: String,
        workspaceId: String,
        noticeKind: String,
        title: String,
        body: String,
        channelId: String?,
    ): PendingNotice {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val safeTitle = title.trim().take(200).ifBlank { "WorkHub" }
        val safeBody = body.trim().take(500)
        Notifications.insert {
            it[Notifications.id] = id
            it[Notifications.userId] = userId
            it[Notifications.workspaceId] = workspaceId
            it[Notifications.kind] = noticeKind
            it[Notifications.title] = safeTitle
            it[Notifications.body] = safeBody
            it[Notifications.channelId] = channelId
            it[createdAt] = now
            it[seen] = false
        }
        return PendingNotice(userId, NotificationDto(id, noticeKind, safeTitle, safeBody, now, channelId))
    }

    fun unreadNotifications(companyId: String, userId: String): List<NotificationDto> = db {
        Notifications.select {
            (Notifications.userId eq userId) and (Notifications.workspaceId eq companyId) and (Notifications.seen eq false)
        }
            .orderBy(Notifications.createdAt to SortOrder.ASC)
            .limit(30)
            .map { row ->
                NotificationDto(
                    id = row[Notifications.id],
                    kind = row[Notifications.kind],
                    title = row[Notifications.title],
                    body = row[Notifications.body],
                    createdAt = row[Notifications.createdAt],
                    channelId = row[Notifications.channelId],
                )
            }
    }

    fun markNotificationsRead(companyId: String, userId: String, ids: List<String>) = db {
        val wanted = ids.map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) {
            Notifications.update({ (Notifications.userId eq userId) and (Notifications.workspaceId eq companyId) }) {
                it[seen] = true
            }
        } else {
            wanted.forEach { noticeId ->
                Notifications.update({
                    (Notifications.id eq noticeId) and (Notifications.userId eq userId) and (Notifications.workspaceId eq companyId)
                }) {
                    it[seen] = true
                }
            }
        }
    }

    private fun requireVisibleChannel(companyId: String, userId: String, role: String, channelId: String): ResultRow {
        val channel = Channels.select { Channels.id eq channelId }.singleOrNull()
            ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy kênh chat")
        if (channel[Channels.companyId] != companyId || !canSeeChannel(userId, role, channel)) {
            throw AppException(HttpStatusCode.NotFound, "Không tìm thấy kênh chat")
        }
        return channel
    }

    private fun addChannelMemberRow(channelId: String, userId: String) {
        val exists = ChannelMembers.select {
            (ChannelMembers.channelId eq channelId) and (ChannelMembers.userId eq userId)
        }.any()
        if (exists) return
        ChannelMembers.insert {
            it[id] = UUID.randomUUID().toString()
            it[ChannelMembers.channelId] = channelId
            it[ChannelMembers.userId] = userId
        }
    }

    private fun accountDto(userId: String): UserDto {
        val user = Users.select { Users.id eq userId }.single()
        return UserDto(
            id = user[Users.id],
            name = user[Users.name],
            email = user[Users.email],
            username = user[Users.username].orEmpty(),
            companyId = "",
            companyName = "",
            inviteCode = "",
            role = "MEMBER",
            avatarDataUrl = user[Users.avatarDataUrl].orEmpty(),
        )
    }

    private fun workspaceDto(company: ResultRow, role: String) = WorkspaceDto(
        id = company[Companies.id],
        name = company[Companies.name],
        role = role,
        inviteCode = if (role == "ADMIN") company[Companies.inviteCode] else "",
    )

    private fun ensureCanEditTask(task: ResultRow, actorId: String) {
        val linked = TaskAssignees.select { TaskAssignees.taskId eq task[Tasks.id] }.map { it[TaskAssignees.userId] }
        val assignees = linked.ifEmpty { listOfNotNull(task[Tasks.assigneeId]) }
        val allowed = if (assignees.isEmpty()) task[Tasks.creatorId] == actorId else actorId in assignees
        if (!allowed) {
            throw AppException(HttpStatusCode.Forbidden, "Việc không giao cho bạn, chỉ xem được")
        }
    }

    private fun ensureCanChange(role: String, ownerId: String, actorId: String) {
        if (role == "ADMIN" || role == "SUPERVISOR") return
        if (role == "MEMBER" && ownerId == actorId) return
        throw AppException(HttpStatusCode.Forbidden, "Bạn chỉ sửa được ghi chú của mình")
    }
}
