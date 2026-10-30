package com.example.stickiespost.domain.usecase

import com.example.stickiespost.domain.assistant.CommandInterpreter
import com.example.stickiespost.domain.model.AssistantCommand
import com.example.stickiespost.domain.model.CommandResult
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.reminder.ReminderScheduler
import com.example.stickiespost.domain.repository.NotesRepository
import java.time.Instant
import kotlinx.coroutines.flow.first

class ProcessCommandUseCase(
    private val interpreter: CommandInterpreter,
    private val repository: NotesRepository,
    private val reminderScheduler: ReminderScheduler
) {
    suspend operator fun invoke(utterance: String): CommandResult {
        require(utterance.isNotBlank()) { "Say or type something first." }
        val command = interpreter.interpret(utterance)

        return when (command.action) {
            AssistantCommand.Action.SAVE -> save(command)
            AssistantCommand.Action.LIST -> list(command)
        }
    }

    private suspend fun save(command: AssistantCommand): CommandResult {
        require(command.category != NoteCategory.ALL) {
            "Gemini could not decide which category to use. Please try again."
        }
        val itemTexts = command.items.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(itemTexts.isNotEmpty()) { "I couldn't find any note items to save." }
        command.reminderAtMillis?.let { reminderAt ->
            require(reminderAt > Instant.now().toEpochMilli()) {
                "That reminder time has already passed. Please specify a future time."
            }
        }

        val now = Instant.now().toEpochMilli()
        val savedItems = repository.save(
            itemTexts.map { text ->
                NoteItem(
                    text = text,
                    category = command.category,
                    createdAtMillis = now,
                    reminderAtMillis = command.reminderAtMillis
                )
            }
        )
        savedItems.filter { it.reminderAtMillis != null }.forEach(reminderScheduler::schedule)
        val reminderText = if (command.reminderAtMillis == null) {
            ""
        } else {
            " Reminder set for ${command.reminderAtMillis}."
        }
        val message = command.reply.ifBlank {
            "Saved ${savedItems.size} ${command.category.label.lowercase()} item(s).$reminderText"
        }
        return CommandResult(command.action, command.category, savedItems, message)
    }

    private suspend fun list(command: AssistantCommand): CommandResult {
        val items = repository.getItems(command.category)
        val message = command.reply.ifBlank {
            if (items.isEmpty()) {
                "There are no saved ${command.category.label.lowercase()} items yet."
            } else {
                "Here are your ${command.category.label.lowercase()} items."
            }
        }
        return CommandResult(command.action, command.category, items, message)
    }
}
