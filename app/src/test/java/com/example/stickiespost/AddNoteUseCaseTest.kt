package com.example.stickiespost

import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.repository.NotesRepository
import com.example.stickiespost.domain.usecase.AddNoteUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddNoteUseCaseTest {
    @Test
    fun savesTrimmedNoteDirectlyToSelectedCategory() = runBlocking {
        val repository = FakeNotesRepository()

        val saved = AddNoteUseCase(repository)("  Call the supplier  ", NoteCategory.WORK)

        assertEquals("Call the supplier", saved.text)
        assertEquals(NoteCategory.WORK, saved.category)
        assertNull(saved.reminderAtMillis)
        assertEquals(listOf(saved), repository.savedItems)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAllNotesAsSaveCategory() {
        runBlocking {
            AddNoteUseCase(FakeNotesRepository())("Call the supplier", NoteCategory.ALL)
        }
    }

    private class FakeNotesRepository : NotesRepository {
        var savedItems = emptyList<NoteItem>()

        override fun observeAll(): Flow<List<NoteItem>> = flowOf(savedItems)

        override suspend fun getItems(category: NoteCategory): List<NoteItem> =
            savedItems.filter { category == NoteCategory.ALL || it.category == category }

        override suspend fun save(items: List<NoteItem>): List<NoteItem> {
            savedItems = items.mapIndexed { index, item -> item.copy(id = index + 1L) }
            return savedItems
        }

        override suspend fun advanceStatus(id: Long) = Unit

        override suspend fun setReminderAt(id: Long, reminderAtMillis: Long) = Unit

        override suspend fun deleteNote(id: Long) = Unit
    }
}
