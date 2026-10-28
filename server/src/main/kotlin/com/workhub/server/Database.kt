package com.workhub.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.util.UUID

object DatabaseFactory {
    fun init() {
        val dir = File(System.getenv("WORKHUB_DATA_DIR")?.takeIf { it.isNotBlank() } ?: "server/data")
        dir.mkdirs()
        val dbPath = File(dir, "workhub").absolutePath.replace('\\', '/')
        Database.connect(
            url = "jdbc:h2:file:$dbPath;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Companies,
                Users,
                Memberships,
                Divisions,
                Departments,
                Teams,
                Tasks,
                Notes,
                Channels,
                ChannelMembers,
                Messages,
                Notifications,
                Spaces,
                SpaceMembers,
                Folders,
                WorkLists,
                TaskComments,
                TaskAssignees,
            )
            backfillAssignees()
            backfillMemberships()
            Companies.selectAll().forEach { seedWorkspaceWork(it[Companies.id]) }
        }
    }
}

object Memberships : Table("memberships") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val workspaceId = varchar("workspace_id", 36).index()
    val role = varchar("member_role", 20)
    val divisionId = varchar("division_id", 36).nullable()
    val departmentId = varchar("department_id", 36).nullable()
    val teamId = varchar("team_id", 36).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, workspaceId)
    }
}

object Companies : Table("companies") {
    val id = varchar("id", 36)
    val name = varchar("name", 120)
    val inviteCode = varchar("invite_code", 12).uniqueIndex()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val name = varchar("name", 80)
    val username = varchar("username", 30).nullable().uniqueIndex()
    val email = varchar("email", 160).uniqueIndex()
    val passwordHash = varchar("password_hash", 80)
    val role = varchar("user_role", 20).default("MEMBER")
    val divisionId = varchar("division_id", 36).nullable()
    val departmentId = varchar("department_id", 36).nullable()
    val teamId = varchar("team_id", 36).nullable()
    /** data:image/...;base64,... (nén phía client). */
    val avatarDataUrl = text("avatar_data_url").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Divisions : Table("divisions") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val name = varchar("name", 120)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Departments : Table("departments") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val divisionId = varchar("division_id", 36).index()
    val name = varchar("name", 120)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TaskAssignees : Table("task_assignees") {
    val taskId = varchar("task_id", 36)
    val userId = varchar("user_id", 36).index()
    override val primaryKey = PrimaryKey(taskId, userId)
}

object Teams : Table("teams") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val departmentId = varchar("department_id", 36).index()
    val name = varchar("name", 120)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val listId = varchar("list_id", 36).nullable()
    val parentId = varchar("parent_id", 36).nullable()
    val title = varchar("title", 200)
    val description = varchar("description", 4000)
    val status = varchar("status", 20)
    val priority = varchar("priority", 20).nullable().default("NORMAL")
    val assigneeId = varchar("assignee_id", 36).nullable()
    val creatorId = varchar("creator_id", 36)
    val startAt = long("start_at").nullable()
    val dueAt = long("due_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Notes : Table("notes") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val authorId = varchar("author_id", 36)
    val title = varchar("title", 200)
    val body = varchar("body", 20000)
    val dueAt = long("due_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Channels : Table("channels") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val name = varchar("name", 80)
    val isPrivate = bool("is_private").default(false)
    val kind = varchar("kind", 16).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).index()
    val workspaceId = varchar("workspace_id", 36).index()
    val kind = varchar("kind", 32)
    val title = varchar("title", 200)
    val body = varchar("body", 500)
    val channelId = varchar("channel_id", 36).nullable()
    val createdAt = long("created_at")
    val seen = bool("is_read").default(false)
    override val primaryKey = PrimaryKey(id)
}

object ChannelMembers : Table("channel_members") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36).index()
    val userId = varchar("user_id", 36)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(channelId, userId)
    }
}

object Messages : Table("messages") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36).index()
    val parentId = varchar("parent_id", 36).nullable()
    val senderId = varchar("sender_id", 36)
    val body = varchar("body", 2000)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Spaces : Table("spaces") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val name = varchar("name", 120)
    val isPrivate = bool("is_private").default(false)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SpaceMembers : Table("space_members") {
    val id = varchar("id", 36)
    val spaceId = varchar("space_id", 36).index()
    val userId = varchar("user_id", 36)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(spaceId, userId)
    }
}

object Folders : Table("folders") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val spaceId = varchar("space_id", 36).index()
    val name = varchar("name", 120)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WorkLists : Table("work_lists") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val spaceId = varchar("space_id", 36).index()
    val folderId = varchar("folder_id", 36).nullable()
    val name = varchar("name", 120)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TaskComments : Table("task_comments") {
    val id = varchar("id", 36)
    val taskId = varchar("task_id", 36).index()
    val authorId = varchar("author_id", 36)
    val body = varchar("body", 2000)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

private fun backfillAssignees() {
    Tasks.selectAll().forEach { task ->
        val memberId = task[Tasks.assigneeId] ?: return@forEach
        val ownerId = task[Tasks.id]
        val exists = TaskAssignees.select {
            (TaskAssignees.taskId eq ownerId) and (TaskAssignees.userId eq memberId)
        }.any()
        if (!exists) {
            TaskAssignees.insert {
                it[taskId] = ownerId
                it[userId] = memberId
            }
        }
    }
}

private fun backfillMemberships() {
    if (Memberships.selectAll().any()) return
    Users.selectAll().forEach { user ->
        val companyId = user[Users.companyId]
        if (companyId.isBlank() || companyId == "platform" || companyId == "none") return@forEach
        if (Companies.select { Companies.id eq companyId }.singleOrNull() == null) return@forEach
        val userId = user[Users.id]
        val exists = Memberships.select {
            (Memberships.userId eq userId) and (Memberships.workspaceId eq companyId)
        }.any()
        if (exists) return@forEach
        Memberships.insert {
            it[id] = UUID.randomUUID().toString()
            it[Memberships.userId] = userId
            it[workspaceId] = companyId
            it[role] = if (user[Users.role] == "ADMIN") "ADMIN" else "MEMBER"
            it[divisionId] = user[Users.divisionId]
            it[departmentId] = user[Users.departmentId]
            it[teamId] = user[Users.teamId]
            it[createdAt] = user[Users.createdAt]
        }
    }
    Companies.selectAll().forEach { company ->
        val companyId = company[Companies.id]
        val rows = Memberships.select { Memberships.workspaceId eq companyId }.toList()
        if (rows.isEmpty() || rows.any { it[Memberships.role] == "ADMIN" }) return@forEach
        val first = rows.minBy { it[Memberships.createdAt] }
        Memberships.update({ Memberships.id eq first[Memberships.id] }) {
            it[role] = "ADMIN"
        }
    }
}

fun seedWorkspaceWork(companyId: String) {
    val space = Spaces.select { Spaces.companyId eq companyId }
        .orderBy(Spaces.createdAt to SortOrder.ASC)
        .firstOrNull()
        ?: run {
            val spaceId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            Spaces.insert {
                it[id] = spaceId
                it[Spaces.companyId] = companyId
                it[name] = "Chung"
                it[isPrivate] = false
                it[createdAt] = now
            }
            Spaces.select { Spaces.id eq spaceId }.single()
        }
    val spaceId = space[Spaces.id]
    val list = WorkLists.select { WorkLists.companyId eq companyId }.firstOrNull()
        ?: run {
            val listId = UUID.randomUUID().toString()
            WorkLists.insert {
                it[id] = listId
                it[WorkLists.companyId] = companyId
                it[WorkLists.spaceId] = spaceId
                it[name] = "Việc chung"
                it[createdAt] = System.currentTimeMillis()
            }
            WorkLists.select { WorkLists.id eq listId }.single()
        }
    Tasks.update({ (Tasks.companyId eq companyId) and Tasks.listId.isNull() }) {
        it[listId] = list[WorkLists.id]
    }
}

fun <T> db(block: org.jetbrains.exposed.sql.Transaction.() -> T): T =
    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        transaction(statement = block)
    }
