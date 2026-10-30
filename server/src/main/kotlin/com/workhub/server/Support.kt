package com.workhub.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.workhub.shared.MessageDto
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import com.workhub.shared.TaskDto
import com.workhub.shared.TaskWrite
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.util.Date
import java.util.UUID

object JwtTokens {
    private val secret = System.getenv("WORKHUB_JWT_SECRET") ?: "workhub-dev-secret-change-before-production"
    private const val issuer = "workhub"
    private const val audience = "workhub-android"
    val verifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun issue(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(secret))

    fun verify(token: String): DecodedJWT? = try {
        verifier.verify(token)
    } catch (_: Exception) {
        null
    }
}

fun String?.trimmed(label: String, max: Int = 200): String {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) throw AppException(HttpStatusCode.BadRequest, "$label không được để trống")
    if (value.length > max) throw AppException(HttpStatusCode.BadRequest, "$label tối đa $max ký tự")
    return value
}

fun String.normalizedUsername(): String {
    val value = trim().lowercase()
    val valid = value.length in 3..30 && value.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    if (!valid) {
        throw AppException(HttpStatusCode.BadRequest, "Username từ 3 đến 30 ký tự, chỉ gồm chữ, số, dấu chấm hoặc gạch dưới")
    }
    return value
}

fun String.normalizedEmail(): String {
    val value = trim().lowercase()
    val valid = value.contains("@") && value.substringAfter("@").contains(".") && value.length in 5..160
    if (!valid) throw AppException(HttpStatusCode.BadRequest, "Email không hợp lệ")
    return value
}

fun String.requirePassword(): String {
    if (length < 6) throw AppException(HttpStatusCode.BadRequest, "Mật khẩu tối thiểu 6 ký tự")
    return this
}

fun inviteCode(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

fun memberNames(companyId: String): Map<String, String> =
    Memberships.select { Memberships.workspaceId eq companyId }.mapNotNull { row ->
        val memberId = row[Memberships.userId]
        val name = Users.select { Users.id eq memberId }.singleOrNull()?.get(Users.name) ?: return@mapNotNull null
        memberId to name
    }.toMap()

fun TaskWrite.cleanTitle(): String = title.trimmed("Tiêu đề", 200)

fun NoteWrite.cleanTitle(): String = title.trimmed("Tiêu đề", 200)

fun TaskWrite.cleanDescription(): String {
    val value = description.trim()
    if (value.length > 4000) throw AppException(HttpStatusCode.BadRequest, "Mô tả tối đa 4000 ký tự")
    return value
}

fun NoteWrite.cleanBody(): String {
    val value = body.trim()
    if (value.length > 20000) throw AppException(HttpStatusCode.BadRequest, "Nội dung tối đa 20000 ký tự")
    return value
}

fun TaskWrite.cleanStatus(): String {
    val value = if (status == "DOING") "IN_PROGRESS" else status
    if (value !in setOf("BACKLOG", "TODO", "IN_PROGRESS", "DONE", "CANCELLED")) {
        throw AppException(HttpStatusCode.BadRequest, "Trạng thái không hợp lệ")
    }
    return value
}

fun TaskWrite.cleanPriority(): String {
    val value = when (val raw = priority.ifBlank { "NONE" }) {
        "NORMAL" -> "MEDIUM"
        else -> raw
    }
    if (value !in setOf("URGENT", "HIGH", "MEDIUM", "LOW", "NONE")) {
        throw AppException(HttpStatusCode.BadRequest, "Mức ưu tiên không hợp lệ")
    }
    return value
}

fun TaskWrite.cleanDates() {
    val start = startAt
    val end = dueAt
    if (start != null && end != null && start > end) {
        throw AppException(HttpStatusCode.BadRequest, "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu")
    }
}

fun TaskWrite.cleanAssignees(companyId: String): List<String> {
    val ids = (assigneeIds + listOfNotNull(assigneeId)).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    ids.forEach { id ->
        val exists = Memberships.select { (Memberships.userId eq id) and (Memberships.workspaceId eq companyId) }.any()
        if (!exists) throw AppException(HttpStatusCode.BadRequest, "Người được giao không thuộc workspace này")
    }
    return ids
}

fun ResultRow.toTask(names: Map<String, String>, lists: Map<String, String> = emptyMap()) = TaskDto(
    id = this[Tasks.id],
    title = this[Tasks.title],
    description = this[Tasks.description],
    status = if (this[Tasks.status] == "DOING") "IN_PROGRESS" else this[Tasks.status],
    assigneeId = this[Tasks.assigneeId],
    assigneeName = this[Tasks.assigneeId]?.let(names::get),
    creatorId = this[Tasks.creatorId],
    creatorName = names[this[Tasks.creatorId]] ?: "Ẩn",
    dueAt = this[Tasks.dueAt],
    updatedAt = this[Tasks.updatedAt],
    listId = this[Tasks.listId],
    parentId = this[Tasks.parentId],
    priority = when (this[Tasks.priority]) {
        null, "", "NORMAL" -> "MEDIUM"
        else -> this[Tasks.priority] ?: "MEDIUM"
    },
    listName = this[Tasks.listId]?.let(lists::get).orEmpty(),
    startAt = this[Tasks.startAt],
    locationUrl = this[Tasks.locationUrl],
)

fun ResultRow.toNote(names: Map<String, String>) = NoteDto(
    id = this[Notes.id],
    title = this[Notes.title],
    body = this[Notes.body],
    authorId = this[Notes.authorId],
    authorName = names[this[Notes.authorId]] ?: "Ẩn",
    updatedAt = this[Notes.updatedAt],
    dueAt = this[Notes.dueAt],
    locationUrl = this[Notes.locationUrl],
)

fun cleanLocationUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.length > 500) throw AppException(HttpStatusCode.BadRequest, "Link vị trí tối đa 500 ký tự")
    return value
}

fun ensureGeneralChannel(companyId: String) {
    val exists = Channels.select { (Channels.companyId eq companyId) and (Channels.name eq "Chung") }.any()
    if (exists) return
    Channels.insert {
        it[id] = UUID.randomUUID().toString()
        it[Channels.companyId] = companyId
        it[Channels.name] = "Chung"
        it[isPrivate] = false
        it[createdAt] = System.currentTimeMillis()
    }
}

fun requireChannel(companyId: String, channelId: String) {
    val row = Channels.select { Channels.id eq channelId }.singleOrNull()
        ?: throw AppException(HttpStatusCode.NotFound, "Không tìm thấy kênh chat")
    if (row[Channels.companyId] != companyId) {
        throw AppException(HttpStatusCode.NotFound, "Không tìm thấy kênh chat")
    }
}

fun messageRows(channelId: String, parentId: String? = null): List<MessageDto> {
    return Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
        .select {
            val inChannel = Messages.channelId eq channelId
            if (parentId == null) inChannel and Messages.parentId.isNull() else inChannel and (Messages.parentId eq parentId)
        }
        .orderBy(Messages.createdAt to SortOrder.DESC)
        .limit(200)
        .map { row ->
            val id = row[Messages.id]
            val replies = if (parentId == null) {
                Messages.select { Messages.parentId eq id }.count().toInt()
            } else {
                0
            }
            MessageDto(
                id = id,
                channelId = row[Messages.channelId],
                senderId = row[Messages.senderId],
                senderName = row[Users.name],
                body = row[Messages.body],
                createdAt = row[Messages.createdAt],
                parentId = row[Messages.parentId],
                replyCount = replies,
                locationUrl = row[Messages.locationUrl],
            )
        }
        .reversed()
}
