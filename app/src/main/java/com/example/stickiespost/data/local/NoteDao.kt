package com.example.stickiespost.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM note_items")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note_items WHERE ownerUserId = :ownerUserId")
    fun observeForUser(ownerUserId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note_items WHERE (:category = 'ALL' OR category = :category)")
    suspend fun getItems(category: String): List<NoteEntity>

    @Query(
        "SELECT * FROM note_items WHERE (:category = 'ALL' OR category = :category) " +
            "AND ownerUserId = :ownerUserId"
    )
    suspend fun getItemsForUser(category: String, ownerUserId: String): List<NoteEntity>

    @Query("SELECT * FROM note_items")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM note_items WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM note_items WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(items: List<NoteEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteNote(item: NoteEntity): Long

    @Query("DELETE FROM note_items WHERE id < 0")
    suspend fun deleteSupabaseNotes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupabaseNotes(items: List<NoteEntity>)

    @androidx.room.Transaction
    suspend fun replaceSupabaseNotes(items: List<NoteEntity>) {
        deleteSupabaseNotes()
        if (items.isNotEmpty()) {
            upsertSupabaseNotes(items)
        }
    }

    @androidx.room.Update
    suspend fun updateNote(item: NoteEntity)

    @androidx.room.Transaction
    suspend fun applyRemoteChanges(
        ownerUserId: String,
        upserts: List<NoteEntity>,
        deletedRemoteIds: List<String>
    ) {
        upserts.forEach { remoteNote ->
            require(remoteNote.ownerUserId == null || remoteNote.ownerUserId == ownerUserId) {
                "Firebase note belongs to a different account."
            }
            val localNote = getByRemoteId(checkNotNull(remoteNote.remoteId))
            if (localNote == null) {
                insertRemoteNote(remoteNote.copy(id = 0, ownerUserId = ownerUserId))
            } else {
                updateNote(remoteNote.copy(id = localNote.id, ownerUserId = ownerUserId))
            }
        }
        deletedRemoteIds.forEach { remoteId ->
            getByRemoteId(remoteId)
                ?.takeIf { it.ownerUserId == ownerUserId }
                ?.let { deleteNote(it.id) }
        }
    }

    @Query("UPDATE note_items SET remoteId = :remoteId, ownerUserId = :ownerUserId WHERE id = :id")
    suspend fun setRemoteIdentity(id: Long, remoteId: String, ownerUserId: String): Int

    @Query(
        "UPDATE note_items SET status = CASE status " +
            "WHEN 'OPEN' THEN 'IN_PROGRESS' " +
            "WHEN 'IN_PROGRESS' THEN 'CLOSED' " +
            "ELSE 'OPEN' END WHERE id = :id"
    )
    suspend fun advanceStatus(id: Long): Int

    @Query("UPDATE note_items SET reminderAtMillis = :reminderAtMillis WHERE id = :id")
    suspend fun setReminderAt(id: Long, reminderAtMillis: Long): Int

    @Query("DELETE FROM note_items WHERE id = :id")
    suspend fun deleteNote(id: Long): Int
}
