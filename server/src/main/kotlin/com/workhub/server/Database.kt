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
import java.net.URI

object DatabaseFactory {
    fun init() {
        val databaseUrl = System.getenv("DATABASE_URL")?.trim()?.takeIf { it.isNotBlank() }
            ?: System.getenv("JDBC_DATABASE_URL")?.trim()?.takeIf { it.isNotBlank() }
        if (databaseUrl != null) {
            connectPostgres(databaseUrl)
            println("WorkHub DB: PostgreSQL (persistent across deploys)")
        } else {
            connectH2()
            println("WorkHub DB: H2 file — set DATABASE_URL (Neon) to keep data after Render redeploy")
        }
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                IdCounters,
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
            ensureIdCounter()
            backfillAssignees()
            backfillMemberships()
            backfillUpdatedAtFromCreatedAt()
            Companies.selectAll().forEach { seedWorkspaceWork(it[Companies.id]) }
        }
    }

    private fun connectH2() {
        val dir = File(System.getenv("WORKHUB_DATA_DIR")?.takeIf { it.isNotBlank() } ?: "server/data")
        dir.mkdirs()
        val dbPath = File(dir, "workhub").absolutePath.replace('\\', '/')
        Database.connect(
            url = "jdbc:h2:file:$dbPath;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        )
    }

    /**
     * Neon / Render Postgres URLs:
     * postgresql://user:pass@host/db?sslmode=require
     * jdbc:postgresql://host/db?...
     */
    private fun connectPostgres(raw: String) {
        val parsed = parsePostgresUrl(raw)
        Database.connect(
            url = parsed.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = parsed.user,
            password = parsed.password,
        )
    }

    internal data class PgConnect(val jdbcUrl: String, val user: String, val password: String)

    internal fun parsePostgresUrl(raw: String): PgConnect {
        val value = raw.trim()
        if (value.startsWith("jdbc:postgresql://", ignoreCase = true)) {
            val uri = URI(value.removePrefix("jdbc:"))
            val userInfo = uri.userInfo
            val user: String
            val password: String
            if (!userInfo.isNullOrBlank()) {
                val parts = userInfo.split(":", limit = 2)
                user = parts[0]
                password = parts.getOrElse(1) { "" }
            } else {
                val q = uri.query.orEmpty().split('&').associate { pair ->
                    val kv = pair.split('=', limit = 2)
                    kv[0] to (kv.getOrNull(1) ?: "")
                }
                user = q["user"].orEmpty()
                password = q["password"].orEmpty()
            }
            val host = uri.host ?: error("DATABASE_URL thiếu host")
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = uri.path?.ifBlank { "/postgres" } ?: "/postgres"
            val query = uri.query?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
            val jdbc = "jdbc:postgresql://$host$port$path$query"
                .replace(Regex("[?&]user=[^&]*"), "")
                .replace(Regex("[?&]password=[^&]*"), "")
                .replace("?&", "?")
                .trimEnd('?', '&')
            return PgConnect(jdbc, user, password)
        }

        val normalized = when {
            value.startsWith("postgres://", true) ->
                "postgresql://" + value.substringAfter("://")
            value.startsWith("postgresql://", true) -> value
            else -> "postgresql://$value"
        }
        val uri = URI(normalized)
        val userInfo = uri.userInfo ?: error("DATABASE_URL thiếu user:password")
        val parts = userInfo.split(":", limit = 2)
        val user = java.net.URLDecoder.decode(parts[0], Charsets.UTF_8)
        val password = java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
        val host = uri.host ?: error("DATABASE_URL thiếu host")
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = uri.path?.ifBlank { "/postgres" } ?: "/postgres"
        var query = uri.query.orEmpty()
        if (!query.contains("sslmode=", ignoreCase = true)) {
            query = if (query.isBlank()) "sslmode=require" else "$query&sslmode=require"
        }
        return PgConnect("jdbc:postgresql://$host$port$path?$query", user, password)
    }
}

/** Shared numeric ID sequence for every entity table ("1", "2", …). */
object IdCounters : Table("id_counters") {
    val name = varchar("name", 40)
    val nextValue = long("next_value")
    override val primaryKey = PrimaryKey(name)
}

/**
 * Next shared ID as a decimal string starting at 1.
 * Must run inside an Exposed transaction (e.g. `db { }`).
 */
fun nextId(): String {
    val key = "global"
    val row = IdCounters.select { IdCounters.name eq key }.forUpdate().singleOrNull()
    if (row == null) {
        IdCounters.insert {
            it[name] = key
            it[nextValue] = 2L
        }
        return "1"
    }
    val current = row[IdCounters.nextValue]
    require(current >= 1L) { "ID sequence invalid" }
    require(current.toString().length <= ID_LEN) { "ID sequence vượt quá $ID_LEN ký tự" }
    IdCounters.update({ IdCounters.name eq key }) {
        it[nextValue] = current + 1L
    }
    return current.toString()
}

private fun ensureIdCounter() {
    val key = "global"
    if (IdCounters.select { IdCounters.name eq key }.any()) return
    val existing = buildList {
        addAll(Users.selectAll().map { it[Users.id] })
        addAll(Companies.selectAll().map { it[Companies.id] })
        addAll(Memberships.selectAll().map { it[Memberships.id] })
        addAll(Tasks.selectAll().map { it[Tasks.id] })
        addAll(Notes.selectAll().map { it[Notes.id] })
        addAll(Channels.selectAll().map { it[Channels.id] })
        addAll(Messages.selectAll().map { it[Messages.id] })
        addAll(Notifications.selectAll().map { it[Notifications.id] })
        addAll(Spaces.selectAll().map { it[Spaces.id] })
        addAll(Folders.selectAll().map { it[Folders.id] })
        addAll(WorkLists.selectAll().map { it[WorkLists.id] })
        addAll(Divisions.selectAll().map { it[Divisions.id] })
        addAll(Departments.selectAll().map { it[Departments.id] })
        addAll(Teams.selectAll().map { it[Teams.id] })
        addAll(TaskComments.selectAll().map { it[TaskComments.id] })
        addAll(ChannelMembers.selectAll().map { it[ChannelMembers.id] })
        addAll(SpaceMembers.selectAll().map { it[SpaceMembers.id] })
    }
    val maxNumeric = existing.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
    IdCounters.insert {
        it[name] = key
        it[nextValue] = maxNumeric + 1L
    }
}

object Memberships : Table("memberships") {
    val id = varchar("id", ID_LEN)
    val userId = varchar("user_id", ID_LEN).index()
    val workspaceId = varchar("workspace_id", ID_LEN).index()
    val role = varchar("member_role", 20)
    val divisionId = varchar("division_id", ID_LEN).nullable()
    val departmentId = varchar("department_id", ID_LEN).nullable()
    val teamId = varchar("team_id", ID_LEN).nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, workspaceId)
    }
}

object Companies : Table("companies") {
    val id = varchar("id", ID_LEN)
    val name = varchar("name", 120)
    val inviteCode = varchar("invite_code", 12).uniqueIndex()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val name = varchar("name", 80)
    val username = varchar("username", 30).nullable().uniqueIndex()
    val email = varchar("email", 160).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("user_role", 20).default("MEMBER")
    val divisionId = varchar("division_id", ID_LEN).nullable()
    val departmentId = varchar("department_id", ID_LEN).nullable()
    val teamId = varchar("team_id", ID_LEN).nullable()
    /**
     * Temporary: inline avatar data-URL. Production nên đẩy file lên object storage
     * và chỉ lưu URL VARCHAR(URL_LEN).
     */
    val avatarDataUrl = text("avatar_data_url").nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Divisions : Table("divisions") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val name = varchar("name", 120)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Departments : Table("departments") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val divisionId = varchar("division_id", ID_LEN).index()
    val name = varchar("name", 120)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

/** Junction: hard-replace on assignee sync; still tracks created_at. */
object TaskAssignees : Table("task_assignees") {
    val taskId = varchar("task_id", ID_LEN).index()
    val userId = varchar("user_id", ID_LEN).index()
    val createdAt = long("created_at").default(0L)
    override val primaryKey = PrimaryKey(taskId, userId)
}

object Teams : Table("teams") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val departmentId = varchar("department_id", ID_LEN).index()
    val name = varchar("name", 120)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Tasks : Table("tasks") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val listId = varchar("list_id", ID_LEN).nullable()
    val parentId = varchar("parent_id", ID_LEN).nullable()
    val title = varchar("title", 200)
    val description = varchar("description", 4000)
    /** Business workflow status (TODO / DONE / …), not soft-delete. */
    val status = varchar("status", 20).index()
    val priority = varchar("priority", 20).nullable().default("NORMAL")
    val assigneeId = varchar("assignee_id", ID_LEN).nullable()
    val creatorId = varchar("creator_id", ID_LEN)
    val startAt = long("start_at").nullable()
    val dueAt = long("due_at").nullable()
    val locationUrl = varchar("location_url", URL_LEN).nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Notes : Table("notes") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val authorId = varchar("author_id", ID_LEN).index()
    val title = varchar("title", 200)
    val body = varchar("body", 8000)
    val dueAt = long("due_at").nullable()
    val locationUrl = varchar("location_url", URL_LEN).nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Channels : Table("channels") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val name = varchar("name", 80)
    val isPrivate = bool("is_private").default(false)
    val kind = varchar("kind", 16).nullable().default("CHANNEL")
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = varchar("id", ID_LEN)
    val userId = varchar("user_id", ID_LEN).index()
    val workspaceId = varchar("workspace_id", ID_LEN)
    val kind = varchar("kind", 32)
    val title = varchar("title", 200)
    val body = varchar("body", 500)
    val channelId = varchar("channel_id", ID_LEN).nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    val seen = bool("is_read").default(false)
    override val primaryKey = PrimaryKey(id)
}

object ChannelMembers : Table("channel_members") {
    val id = varchar("id", ID_LEN)
    val channelId = varchar("channel_id", ID_LEN).index()
    val userId = varchar("user_id", ID_LEN).index()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(channelId, userId)
    }
}

object Messages : Table("messages") {
    val id = varchar("id", ID_LEN)
    val channelId = varchar("channel_id", ID_LEN).index()
    val parentId = varchar("parent_id", ID_LEN).nullable()
    val senderId = varchar("sender_id", ID_LEN).index()
    val body = varchar("body", 2000)
    val locationUrl = varchar("location_url", URL_LEN).nullable()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object Spaces : Table("spaces") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val name = varchar("name", 120)
    val isPrivate = bool("is_private").default(false)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object SpaceMembers : Table("space_members") {
    val id = varchar("id", ID_LEN)
    val spaceId = varchar("space_id", ID_LEN).index()
    val userId = varchar("user_id", ID_LEN).index()
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(spaceId, userId)
    }
}

object Folders : Table("folders") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val spaceId = varchar("space_id", ID_LEN).index()
    val name = varchar("name", 120)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object WorkLists : Table("work_lists") {
    val id = varchar("id", ID_LEN)
    val companyId = varchar("company_id", ID_LEN).index()
    val spaceId = varchar("space_id", ID_LEN).index()
    val folderId = varchar("folder_id", ID_LEN).nullable()
    val name = varchar("name", 120)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

object TaskComments : Table("task_comments") {
    val id = varchar("id", ID_LEN)
    val taskId = varchar("task_id", ID_LEN).index()
    val authorId = varchar("author_id", ID_LEN).index()
    val body = varchar("body", 2000)
    val createdAt = sysCreatedAt()
    val updatedAt = sysUpdatedAt()
    val isActive = sysIsActive()
    val deletedAt = sysDeletedAt()
    override val primaryKey = PrimaryKey(id)
}

/** Copy created_at → updated_at for rows that still have default 0 after schema upgrade. */
private fun backfillUpdatedAtFromCreatedAt() {
    Users.select { Users.updatedAt eq 0L }.forEach { row ->
        Users.update({ Users.id eq row[Users.id] }) { it[updatedAt] = row[Users.createdAt] }
    }
    Companies.select { Companies.updatedAt eq 0L }.forEach { row ->
        Companies.update({ Companies.id eq row[Companies.id] }) { it[updatedAt] = row[Companies.createdAt] }
    }
    Memberships.select { Memberships.updatedAt eq 0L }.forEach { row ->
        Memberships.update({ Memberships.id eq row[Memberships.id] }) { it[updatedAt] = row[Memberships.createdAt] }
    }
    Channels.select { Channels.updatedAt eq 0L }.forEach { row ->
        Channels.update({ Channels.id eq row[Channels.id] }) { it[updatedAt] = row[Channels.createdAt] }
    }
    Spaces.select { Spaces.updatedAt eq 0L }.forEach { row ->
        Spaces.update({ Spaces.id eq row[Spaces.id] }) { it[updatedAt] = row[Spaces.createdAt] }
    }
    WorkLists.select { WorkLists.updatedAt eq 0L }.forEach { row ->
        WorkLists.update({ WorkLists.id eq row[WorkLists.id] }) { it[updatedAt] = row[WorkLists.createdAt] }
    }
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
            it[id] = nextId()
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
            val spaceId = nextId()
            val now = System.currentTimeMillis()
            Spaces.insert {
                it[id] = spaceId
                it[Spaces.companyId] = companyId
                it[name] = "Chung"
                it[isPrivate] = false
                it[createdAt] = now
                it[updatedAt] = now
            }
            Spaces.select { Spaces.id eq spaceId }.single()
        }
    val spaceId = space[Spaces.id]
    val list = WorkLists.select {
        (WorkLists.companyId eq companyId) and usable(WorkLists.isActive, WorkLists.deletedAt)
    }.firstOrNull()
        ?: run {
            val listId = nextId()
            val listNow = System.currentTimeMillis()
            WorkLists.insert {
                it[id] = listId
                it[WorkLists.companyId] = companyId
                it[WorkLists.spaceId] = spaceId
                it[name] = "Việc chung"
                it[createdAt] = listNow
                it[updatedAt] = listNow
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
