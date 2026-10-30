package com.example.stickiespost

import com.example.stickiespost.domain.assistant.CommandInterpreter
import com.example.stickiespost.domain.model.AssistantCommand
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.reminder.ReminderScheduler
import com.example.stickiespost.domain.repository.NotesRepository
import com.example.stickiespost.domain.usecase.ProcessCommandUseCase
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessCommandUseCaseTest {
    @Test
    fun savesEachParsedItemAndSchedulesTimedReminders() = runBlocking {
        val reminderAt = Instant.now().plusSeconds(3_600).toEpochMilli()
        val repository = FakeNotesRepository()
        val scheduler = FakeReminderScheduler()
        val useCase = ProcessCommandUseCase(
            interpreter = FixedCommandInterpreter(
                AssistantCommand(
                    action = AssistantCommand.Action.SAVE,
                    category = NoteCategory.GROCERIES,
                    items = listOf("Milk", "Eggs"),
                    reminderAtMillis = reminderAt
                )
            ),
            repository = repository,
            reminderScheduler = scheduler
        )

        val result = useCase("Remember milk and eggs for tomorrow")

        assertEquals(listOf("Milk", "Eggs"), result.items.map(NoteItem::text))
        assertEquals(listOf(reminderAt, reminderAt), scheduler.scheduledItems.map(NoteItem::reminderAtMillis))
        assertEquals(NoteCategory.GROCERIES, result.category)
    }

    @Test
    fun listCommandRetrievesOnlyMatchingDatabaseCategory() = runBlocking {
        val repository = FakeNotesRepository()
        repository.save(
            listOf(
                note("Milk", NoteCategory.GROCERIES),
                note("Email team", NoteCategory.WORK)
            )
        )
        val useCase = ProcessCommandUseCase(
            interpreter = FixedCommandInterpreter(
                AssistantCommand(
                    action = AssistantCommand.Action.LIST,
                    category = NoteCategory.GROCERIES
                )
            ),
            repository = repository,
            reminderScheduler = FakeReminderScheduler()
        )

        val result = useCase("What groceries do I need?")

        assertEquals(listOf("Milk"), result.items.map(NoteItem::text))
        assertEquals(NoteCategory.GROCERIES, result.category)
    }

    private fun note(text: String, category: NoteCategory) = NoteItem(
        text = text,
        category = category,
        createdAtMillis = Instant.now().toEpochMilli()
    )

    private class FixedCommandInterpreter(private val command: AssistantCommand) : CommandInterpreter {
        override suspend fun interpret(utterance: String) = command
    }

    private class FakeReminderScheduler : ReminderScheduler {
        val scheduledItems = mutableListOf<NoteItem>()

        override fun schedule(item: NoteItem) {
            scheduledItems += item
        }
    }

    private class FakeNotesRepository : NotesRepository {
        private val items = mutableListOf<NoteItem>()
        private var nextId = 1L

        override fun observeAll(): Flow<List<NoteItem>> = flowOf(items.toList())

        override suspend fun getItems(category: NoteCategory): List<NoteItem> =
            items.filter { category == NoteCategory.ALL || it.category == category }

        override suspend fun save(items: List<NoteItem>): List<NoteItem> {
            val savedItems = items.map { it.copy(id = nextId++) }
            this.items += savedItems
            return savedItems
        }

        override suspend fun advanceStatus(id: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(status = items[index].status.next())
        }

        override suspend fun setReminderAt(id: Long, reminderAtMillis: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(reminderAtMillis = reminderAtMillis)
        }

        override suspend fun deleteNote(id: Long) {
            items.removeAll { it.id == id }
        }
    }
}
