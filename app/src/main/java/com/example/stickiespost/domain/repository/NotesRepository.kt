package com.example.stickiespost.domain.repository

import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeAll(): Flow<List<NoteItem>>
    suspend fun getItems(category: NoteCategory): List<NoteItem>
    suspend fun save(items: List<NoteItem>): List<NoteItem>
    suspend fun advanceStatus(id: Long)
    suspend fun setReminderAt(id: Long, reminderAtMillis: Long)
    suspend fun deleteNote(id: Long)
}
