package com.example.stickiespost.data.remote

import com.example.stickiespost.data.local.NoteDao
import com.example.stickiespost.data.local.NoteEntity
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteStatus
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import java.util.UUID

class FirebaseNoteSync(
    private val store: FirebaseNoteStore,
    private val noteDao: NoteDao,
    private val ownerUserId: String
) {
    suspend fun run() {
        seedLegacyLocalNotes()
        callbackFlow {
            val registration = store.collectionForUser(ownerUserId)
                .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    try {
                        val upserts = mutableListOf<NoteEntity>()
                        val deletedRemoteIds = mutableListOf<String>()
                        snapshot.documentChanges.forEach { change ->
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED ->
                                    deletedRemoteIds += change.document.id
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED ->
                                    upserts += change.document.toNoteEntity()
                            }
                        }
                        val result = trySend(upserts to deletedRemoteIds)
                        result.exceptionOrNull()?.let(::close)
                    } catch (exception: Exception) {
                        close(exception)
                    }
                }
            }
            awaitClose { registration.remove() }
        }.collect { (upserts, deletedRemoteIds) ->
            noteDao.applyRemoteChanges(ownerUserId, upserts, deletedRemoteIds)
        }
    }

    private suspend fun seedLegacyLocalNotes() {
        noteDao.getAll()
            .filter { it.ownerUserId == null }
            .forEach { localNote ->
                val remoteId = UUID.randomUUID().toString()
                check(noteDao.setRemoteIdentity(localNote.id, remoteId, ownerUserId) == 1) {
                    "Could not assign a Firebase ID to a saved note."
                }
                store.upsert(localNote.copy(remoteId = remoteId, ownerUserId = ownerUserId))
            }
    }

    private fun DocumentSnapshot.toNoteEntity(): NoteEntity {
        val noteOwner = getString(FirebaseNoteStore.OWNER_FIELD)
            ?: throw IllegalArgumentException(
                "Firebase note '$id' is missing ${FirebaseNoteStore.OWNER_FIELD}."
            )
        require(noteOwner == ownerUserId) {
            "Firebase returned a note owned by a different account."
        }
        val category = getString("category")
            ?: throw IllegalArgumentException("Firebase note '$id' is missing category.")
        require(NoteCategory.fromModelValue(category) != NoteCategory.ALL) {
            "Firebase notes cannot use the ALL category."
        }
        val status = getString("status")
            ?: throw IllegalArgumentException("Firebase note '$id' is missing status.")
        NoteStatus.fromStorageValue(status)
        return NoteEntity(
            text = getString("text")
                ?: throw IllegalArgumentException("Firebase note '$id' is missing text."),
            category = category,
            createdAtMillis = getLong("createdAtMillis")
                ?: throw IllegalArgumentException(
                    "Firebase note '$id' is missing createdAtMillis."
                ),
            reminderAtMillis = getLong("reminderAtMillis"),
            status = status,
            remoteId = id,
            ownerUserId = noteOwner
        )
    }
}
