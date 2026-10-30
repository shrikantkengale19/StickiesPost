package com.example.stickiespost.domain.usecase

import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.repository.NotesRepository
import java.time.Instant

class AddNoteUseCase(private val repository: NotesRepository) {
    suspend operator fun invoke(text: String, category: NoteCategory): NoteItem {
        val noteText = text.trim()
        require(noteText.isNotEmpty()) { "Write a note before sending." }
        require(category != NoteCategory.ALL) {
            "Choose a category other than All notes to save directly."
        }

        val note = NoteItem(
            text = noteText,
            category = category,
            createdAtMillis = Instant.now().toEpochMilli()
        )
        return repository.save(listOf(note)).single()
    }
}
