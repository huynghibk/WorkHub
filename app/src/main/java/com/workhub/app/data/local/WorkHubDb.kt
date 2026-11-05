package com.workhub.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.workhub.app.BuildConfig
import kotlinx.coroutines.flow.Flow

data class MessagePeek(
    val channelId: String,
    val createdAt: Long,
    val senderId: String,
)

/**
 * Local copy of a task. [payload] is the last known TaskDto (JSON); [pendingWrite] is the
 * TaskWrite (JSON) still waiting to be uploaded while [syncPending] is true.
 * [deleted] rows are hidden from the UI and removed once the server confirms the delete.
 */
@Entity(tableName = "tasks", indices = [Index("workspaceId"), Index("parentId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val parentId: String?,
    val payload: String,
    val pendingWrite: String? = null,
    val syncPending: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "notes", indices = [Index("workspaceId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val payload: String,
    val pendingWrite: String? = null,
    val syncPending: Boolean = false,
    val deleted: Boolean = false,
)

/** [position] keeps the server's channel order. */
@Entity(tableName = "channels", indices = [Index("workspaceId")])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val position: Int,
    val payload: String,
)

/**
 * Chat history cache. [syncPending] rows were typed offline and are still "sending";
 * [pendingWrite] is the MessageWrite (JSON) to upload. [failed] rows were refused by the
 * server and kept so the user can retry or copy the text.
 */
@Entity(tableName = "messages", indices = [Index(value = ["channelId", "parentId", "createdAt"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val channelId: String,
    val parentId: String?,
    val createdAt: Long,
    val payload: String,
    val pendingWrite: String? = null,
    val syncPending: Boolean = false,
    val failed: Boolean = false,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM channels WHERE workspaceId = :workspaceId ORDER BY position")
    fun observeChannels(workspaceId: String): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE workspaceId = :workspaceId")
    suspend fun deleteChannels(workspaceId: String)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannel(id: String)

    @Query("DELETE FROM messages WHERE channelId = :channelId")
    suspend fun deleteChannelMessages(channelId: String)

    @Upsert
    suspend fun upsertChannels(rows: List<ChannelEntity>)

    @Transaction
    suspend fun replaceChannels(workspaceId: String, rows: List<ChannelEntity>) {
        deleteChannels(workspaceId)
        upsertChannels(rows)
    }

    /** `IS` matches NULL too, so the same query serves the channel root and a thread. */
    @Query("SELECT * FROM messages WHERE channelId = :channelId AND parentId IS :parentId ORDER BY createdAt")
    fun observeMessages(channelId: String, parentId: String?): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE workspaceId = :workspaceId AND parentId IS NULL ORDER BY createdAt DESC")
    fun observeRoots(workspaceId: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM channels WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun channelCount(workspaceId: String, id: String): Int

    @Query("SELECT id FROM messages WHERE syncPending = 1")
    fun observePendingIds(): Flow<List<String>>

    @Query("SELECT id FROM messages WHERE failed = 1")
    fun observeFailedIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM messages WHERE syncPending = 1 OR failed = 1")
    suspend fun unsentCount(): Int

    @Query("UPDATE messages SET syncPending = 0, failed = 1 WHERE id = :id")
    suspend fun markFailed(id: String)

    @Query("UPDATE messages SET syncPending = 1, failed = 0 WHERE id = :id AND failed = 1")
    suspend fun retry(id: String)

    @Query("DELETE FROM messages WHERE createdAt < :cutoff AND syncPending = 0 AND failed = 0")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query("SELECT MAX(createdAt) FROM messages WHERE channelId = :channelId AND parentId IS :parentId")
    suspend fun maxCreatedAt(channelId: String, parentId: String?): Long?

    /** Oldest first so a thread root is uploaded before its replies. */
    @Query("SELECT * FROM messages WHERE syncPending = 1 ORDER BY createdAt")
    suspend fun pending(): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE syncPending = 1")
    suspend fun pendingIds(): List<String>

    @Upsert
    suspend fun upsert(row: MessageEntity)

    @Upsert
    suspend fun upsertAll(rows: List<MessageEntity>)

    @Query("UPDATE messages SET payload = :payload, createdAt = :createdAt, pendingWrite = NULL, syncPending = 0 WHERE id = :id AND pendingWrite = :sent")
    suspend fun markSynced(id: String, sent: String, payload: String, createdAt: Long): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun remove(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE workspaceId = :workspaceId AND deleted = 0")
    fun observe(workspaceId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE syncPending = 1")
    suspend fun pending(): List<TaskEntity>

    @Upsert
    suspend fun upsert(row: TaskEntity)

    @Upsert
    suspend fun upsertAll(rows: List<TaskEntity>)

    @Query("UPDATE tasks SET deleted = 1 WHERE parentId = :parentId")
    suspend fun hideChildren(parentId: String)

    /** Only clears the pending flag if no newer local edit arrived while uploading. */
    @Query("UPDATE tasks SET payload = :payload, parentId = :parentId, pendingWrite = NULL, syncPending = 0 WHERE id = :id AND pendingWrite = :sent AND deleted = 0")
    suspend fun markSynced(id: String, sent: String, payload: String, parentId: String?): Int

    @Query("UPDATE tasks SET pendingWrite = NULL, syncPending = 0, deleted = 0 WHERE id = :id")
    suspend fun dropPending(id: String)

    @Query("DELETE FROM tasks WHERE id = :id OR parentId = :id")
    suspend fun removeTree(id: String)

    @Query("DELETE FROM tasks WHERE workspaceId = :workspaceId AND syncPending = 0")
    suspend fun deleteSynced(workspaceId: String)

    @Query("SELECT id FROM tasks WHERE workspaceId = :workspaceId AND syncPending = 1")
    suspend fun pendingIds(workspaceId: String): List<String>

    /** Replace the server-backed rows of a workspace, keeping local edits not yet uploaded. */
    @Transaction
    suspend fun replaceSynced(workspaceId: String, rows: List<TaskEntity>) {
        val keep = pendingIds(workspaceId).toSet()
        deleteSynced(workspaceId)
        upsertAll(rows.filter { it.id !in keep })
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE workspaceId = :workspaceId AND deleted = 0")
    fun observe(workspaceId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE syncPending = 1")
    suspend fun pending(): List<NoteEntity>

    @Upsert
    suspend fun upsert(row: NoteEntity)

    @Upsert
    suspend fun upsertAll(rows: List<NoteEntity>)

    @Query("UPDATE notes SET payload = :payload, pendingWrite = NULL, syncPending = 0 WHERE id = :id AND pendingWrite = :sent AND deleted = 0")
    suspend fun markSynced(id: String, sent: String, payload: String): Int

    @Query("UPDATE notes SET pendingWrite = NULL, syncPending = 0, deleted = 0 WHERE id = :id")
    suspend fun dropPending(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM notes WHERE workspaceId = :workspaceId AND syncPending = 0")
    suspend fun deleteSynced(workspaceId: String)

    @Query("SELECT id FROM notes WHERE workspaceId = :workspaceId AND syncPending = 1")
    suspend fun pendingIds(workspaceId: String): List<String>

    @Transaction
    suspend fun replaceSynced(workspaceId: String, rows: List<NoteEntity>) {
        val keep = pendingIds(workspaceId).toSet()
        deleteSynced(workspaceId)
        upsertAll(rows.filter { it.id !in keep })
    }
}

@Database(
    entities = [TaskEntity::class, NoteEntity::class, ChannelEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WorkHubDb : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun notes(): NoteDao
    abstract fun chat(): ChatDao

    companion object {
        fun create(context: Context): WorkHubDb =
            Room.databaseBuilder(context.applicationContext, WorkHubDb::class.java, "workhub.db")
                .apply {
                    // Dev builds may carry an older schema; release builds must ship real migrations
                    // because a wipe would also discard unsynced work.
                    if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
                }
                .build()
    }
}
