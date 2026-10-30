package com.example.stickiespost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stickiespost.data.security.ApiKeyStore
import com.example.stickiespost.domain.model.CommandResult
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.reminder.ReminderScheduler
import com.example.stickiespost.domain.repository.NotesRepository
import com.example.stickiespost.domain.usecase.AddNoteUseCase
import com.example.stickiespost.domain.usecase.ProcessCommandUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<NoteItem> = emptyList(),
    val selectedCategory: NoteCategory = NoteCategory.ALL,
    val message: String? = null,
    val error: String? = null,
    val isProcessing: Boolean = false,
    val isSavingNote: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val reminderPermissionRequestId: Int = 0
)

class NotesViewModel(
    private val processCommandUseCase: ProcessCommandUseCase,
    private val repository: NotesRepository,
    private val apiKeyStore: ApiKeyStore,
    private val addNoteUseCase: AddNoteUseCase,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotesUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(apiKeyConfigured = apiKeyStore.get() != null)
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not read the saved Gemini API key."
                )
            }
            repository.observeAll().collect { items ->
                mutableState.value = mutableState.value.copy(notes = items)
            }
        }
    }

    fun processCommand(utterance: String, onSuccess: (() -> Unit)? = null) {
        if (mutableState.value.isProcessing) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isProcessing = true, error = null)
            try {
                val result = processCommandUseCase(utterance)
                handleResult(result)
                onSuccess?.invoke()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "The request could not be completed."
                )
            } finally {
                mutableState.value = mutableState.value.copy(isProcessing = false)
            }
        }
    }

    fun addNote(text: String, onSuccess: (() -> Unit)? = null) {
        if (mutableState.value.isProcessing) return
        val category = mutableState.value.selectedCategory
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isProcessing = true,
                isSavingNote = true,
                error = null
            )
            try {
                val note = addNoteUseCase(text, category)
                mutableState.value = mutableState.value.copy(
                    message = "Saved to ${note.category.label}.",
                    error = null
                )
                onSuccess?.invoke()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not save this note."
                )
            } finally {
                mutableState.value = mutableState.value.copy(
                    isProcessing = false,
                    isSavingNote = false
                )
            }
        }
    }

    fun selectCategory(category: NoteCategory) {
        mutableState.value = mutableState.value.copy(selectedCategory = category)
    }

    fun advanceStatus(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.advanceStatus(noteId)
                mutableState.value = mutableState.value.copy(error = null)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not update this note's status."
                )
            }
        }
    }

    fun setReminder(noteId: Long, reminderAtMillis: Long) {
        if (reminderAtMillis <= System.currentTimeMillis()) {
            showError("Choose a reminder time in the future.")
            return
        }
        viewModelScope.launch {
            try {
                val note = mutableState.value.notes.firstOrNull { it.id == noteId }
                    ?: throw IllegalStateException("Could not find this note to set its reminder.")
                repository.setReminderAt(noteId, reminderAtMillis)
                reminderScheduler.schedule(note.copy(reminderAtMillis = reminderAtMillis))
                mutableState.value = mutableState.value.copy(
                    error = null,
                    message = "Reminder set for ${java.text.DateFormat.getDateTimeInstance().format(
                        java.util.Date(reminderAtMillis)
                    )}.",
                    reminderPermissionRequestId = mutableState.value.reminderPermissionRequestId + 1
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not set this reminder."
                )
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteNote(noteId)
                mutableState.value = mutableState.value.copy(
                    error = null,
                    message = "Note deleted."
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not delete this note."
                )
            }
        }
    }

    fun saveApiKey(value: String): Boolean {
        try {
            apiKeyStore.save(value)
            mutableState.value = mutableState.value.copy(
                apiKeyConfigured = true,
                error = null,
                message = "Gemini API key saved securely on this device."
            )
            return true
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.localizedMessage ?: "Could not save the Gemini API key."
            )
            return false
        }
    }

    fun removeApiKey() {
        try {
            apiKeyStore.clear()
            mutableState.value = mutableState.value.copy(
                apiKeyConfigured = false,
                error = null,
                message = "Gemini API key removed."
            )
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.localizedMessage ?: "Could not remove the Gemini API key."
            )
        }
    }

    fun dismissMessage() {
        mutableState.value = mutableState.value.copy(message = null, error = null)
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun onNotificationPermissionDenied() {
        mutableState.value = mutableState.value.copy(
            message = "Your note and reminder are saved, but notifications are disabled. Enable them in system settings to receive reminders."
        )
    }

    private fun handleResult(result: CommandResult) {
        mutableState.value = mutableState.value.copy(
            selectedCategory = result.category,
            message = result.message,
            error = null,
            reminderPermissionRequestId = if (result.items.any { it.reminderAtMillis != null }) {
                mutableState.value.reminderPermissionRequestId + 1
            } else {
                mutableState.value.reminderPermissionRequestId
            }
        )
    }

    companion object {
        fun factory(
            processCommand: ProcessCommandUseCase,
            repository: NotesRepository,
            apiKeyStore: ApiKeyStore,
            addNoteUseCase: AddNoteUseCase,
            reminderScheduler: ReminderScheduler
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(NotesViewModel::class.java))
                return NotesViewModel(
                    processCommand,
                    repository,
                    apiKeyStore,
                    addNoteUseCase,
                    reminderScheduler
                ) as T
            }
        }
    }
}
