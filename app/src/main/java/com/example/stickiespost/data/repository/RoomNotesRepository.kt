package com.example.stickiespost.data.repository

import com.example.stickiespost.data.local.NoteDao
import com.example.stickiespost.data.local.NoteEntity
import com.example.stickiespost.data.remote.FirebaseNoteStore
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi

class RoomNotesRepository(
    private val noteDao: NoteDao,
    private val firebaseNoteStore: FirebaseNoteStore? = null
) : NotesRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<NoteItem>> {
        val notes = firebaseNoteStore?.user?.flatMapLatest { user ->
            user?.let { noteDao.observeForUser(it.uid) } ?: flowOf(emptyList())
        } ?: noteDao.observeAll()
        return notes.map { items -> items.map(NoteEntity::toDomain) }
    }

    override suspend fun getItems(category: NoteCategory): List<NoteItem> =
        if (firebaseNoteStore == null) {
            noteDao.getItems(category.name).map(NoteEntity::toDomain)
        } else {
            val userId = firebaseNoteStore.currentUser?.uid
                ?: throw IllegalStateException("Sign in to access your notes.")
            noteDao.getItemsForUser(category.name, userId).map(NoteEntity::toDomain)
        }

    override suspend fun save(items: List<NoteItem>): List<NoteItem> {
        val entities = items.map { item ->
            val userId = firebaseNoteStore?.currentUser?.uid
                ?: if (firebaseNoteStore != null) {
                    throw IllegalStateException("Sign in before saving notes.")
                } else {
                    null
                }
            NoteEntity.fromDomain(
                item,
                remoteId = userId?.let { UUID.randomUUID().toString() },
                ownerUserId = userId
            )
        }
        val ids = noteDao.insert(entities)
        entities.zip(ids).forEach { (entity, id) ->
            firebaseNoteStore?.upsert(entity.copy(id = id))
        }
        return items.zip(ids) { item, id -> item.copy(id = id) }
    }

    override suspend fun advanceStatus(id: Long) {
        verifyOwnership(id)
        check(noteDao.advanceStatus(id) == 1) { "Could not find this note to update its status." }
        noteDao.getById(id)?.let { firebaseNoteStore?.upsert(it) }
    }

    override suspend fun setReminderAt(id: Long, reminderAtMillis: Long) {
        verifyOwnership(id)
        check(noteDao.setReminderAt(id, reminderAtMillis) == 1) {
            "Could not find this note to set its reminder."
        }
        noteDao.getById(id)?.let { firebaseNoteStore?.upsert(it) }
    }

    override suspend fun deleteNote(id: Long) {
        val note = noteDao.getById(id)
            ?: throw IllegalStateException("Could not find this note to delete.")
        verifyOwnership(id)
        check(noteDao.deleteNote(id) == 1) { "Could not find this note to delete." }
        note.remoteId?.let { remoteId ->
            note.ownerUserId?.let { ownerUserId ->
                firebaseNoteStore?.delete(remoteId, ownerUserId)
            }
        }
    }

    private suspend fun verifyOwnership(id: Long) {
        if (firebaseNoteStore != null) {
            val userId = firebaseNoteStore.currentUser?.uid
                ?: throw IllegalStateException("Sign in to access your notes.")
            check(noteDao.getById(id)?.ownerUserId == userId) {
                "Could not find this note in the signed-in account."
            }
        }
    }
}
