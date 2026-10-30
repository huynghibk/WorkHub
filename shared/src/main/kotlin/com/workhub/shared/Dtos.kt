package com.workhub.shared

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val username: String = "",
    val companyId: String? = null,
    val divisionId: String? = null,
    val departmentId: String? = null,
    val teamId: String? = null,
)

@Serializable
data class LoginRequest(
    /** Email hoặc username — giữ tên field `email` để tương thích client cũ. */
    val email: String = "",
    val password: String,
    /** Tuỳ chọn: nếu gửi thì ưu tiên hơn `email`. */
    val login: String = "",
) {
    fun identifier(): String = login.trim().ifBlank { email.trim() }
}

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class ProfileUpdate(
    val name: String? = null,
    val username: String? = null,
    /** data:image/...;base64,... — null = không đổi, "" = xoá avatar. */
    val avatarDataUrl: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val username: String = "",
    val companyId: String,
    val companyName: String,
    val inviteCode: String = "",
    val role: String = "MEMBER",
    val divisionName: String = "",
    val departmentName: String = "",
    val teamName: String = "",
    val avatarDataUrl: String = "",
)

@Serializable
data class AdminStatus(val exists: Boolean)

@Serializable
data class NamedCreate(val name: String, val parentId: String = "")

@Serializable
data class TeamNode(val id: String, val name: String)

@Serializable
data class DepartmentNode(val id: String, val name: String, val teams: List<TeamNode> = emptyList())

@Serializable
data class DivisionNode(val id: String, val name: String, val departments: List<DepartmentNode> = emptyList())

@Serializable
data class CompanyNode(val id: String, val name: String, val divisions: List<DivisionNode> = emptyList())

@Serializable
data class OrgTree(val companies: List<CompanyNode> = emptyList())

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val role: String,
    val inviteCode: String = "",
)

@Serializable
data class WorkspaceCreate(val name: String)

@Serializable
data class JoinWorkspace(val inviteCode: String)

@Serializable
data class RoleUpdate(val role: String)

@Serializable
data class MemberDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "MEMBER",
)

@Serializable
data class SpaceWrite(val name: String, val isPrivate: Boolean = false)

@Serializable
data class FolderWrite(val name: String, val spaceId: String)

@Serializable
data class ListWrite(val name: String, val spaceId: String, val folderId: String? = null)

@Serializable
data class WorkListNode(val id: String, val name: String, val folderId: String? = null)

@Serializable
data class FolderNode(val id: String, val name: String, val lists: List<WorkListNode> = emptyList())

@Serializable
data class SpaceNode(
    val id: String,
    val name: String,
    val isPrivate: Boolean = false,
    val folders: List<FolderNode> = emptyList(),
    val lists: List<WorkListNode> = emptyList(),
)

@Serializable
data class WorkTree(val spaces: List<SpaceNode> = emptyList())

@Serializable
data class MemberAdd(val userId: String)

@Serializable
data class TaskWrite(
    val title: String,
    val description: String = "",
    val status: String = "TODO",
    val assigneeId: String? = null,
    val dueAt: Long? = null,
    val listId: String? = null,
    val parentId: String? = null,
    val priority: String = "NONE",
    val assigneeIds: List<String> = emptyList(),
    val startAt: Long? = null,
    val childIds: List<String> = emptyList(),
    val updateLinks: Boolean = false,
    val locationUrl: String? = null,
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val assigneeId: String?,
    val assigneeName: String?,
    val creatorId: String,
    val creatorName: String,
    val dueAt: Long?,
    val updatedAt: Long,
    val listId: String? = null,
    val parentId: String? = null,
    val priority: String = "NONE",
    val listName: String = "",
    val subtaskCount: Int = 0,
    val assigneeIds: List<String> = emptyList(),
    val assigneeNames: List<String> = emptyList(),
    val startAt: Long? = null,
    val locationUrl: String? = null,
)

@Serializable
data class CommentWrite(val body: String)

@Serializable
data class CommentDto(
    val id: String,
    val taskId: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: Long,
)

@Serializable
data class SearchHit(
    val kind: String,
    val id: String,
    val title: String,
    val snippet: String,
    val channelId: String? = null,
)

@Serializable
data class SearchResult(val hits: List<SearchHit> = emptyList())

@Serializable
data class NoteWrite(
    val title: String,
    val body: String = "",
    val dueAt: Long? = null,
    val locationUrl: String? = null,
)

@Serializable
data class NoteDto(
    val id: String,
    val title: String,
    val body: String,
    val authorId: String,
    val authorName: String,
    val updatedAt: Long,
    val dueAt: Long? = null,
    val locationUrl: String? = null,
)

@Serializable
data class ChannelWrite(
    val name: String,
    val isPrivate: Boolean = false,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val isPrivate: Boolean = false,
    val kind: String = "CHANNEL",
)

@Serializable
data class DirectWrite(val userId: String)

@Serializable
data class GroupWrite(
    val name: String = "",
    val memberIds: List<String> = emptyList(),
)

@Serializable
data class NotificationDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val channelId: String? = null,
)

@Serializable
data class IdList(val ids: List<String> = emptyList())

@Serializable
data class MessageDto(
    val id: String,
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: Long,
    val parentId: String? = null,
    val replyCount: Int = 0,
    val locationUrl: String? = null,
)

@Serializable
data class MessageWrite(
    val body: String,
    val parentId: String? = null,
    val locationUrl: String? = null,
)

@Serializable
data class WsEvent(
    val type: String,
    val message: MessageDto? = null,
    val error: String? = null,
    val notification: NotificationDto? = null,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
