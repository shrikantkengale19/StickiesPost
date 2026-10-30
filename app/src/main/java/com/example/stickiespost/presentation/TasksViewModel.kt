package com.example.stickiespost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.domain.repository.RecurringTaskRepository
import com.example.stickiespost.domain.usecase.AddRecurringTaskUseCase
import com.example.stickiespost.reminders.AndroidTaskReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<RecurringTask> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val isSaving: Boolean = false,
    val notificationPermissionRequestId: Int = 0
)

class TasksViewModel(
    private val repository: RecurringTaskRepository,
    private val addTaskUseCase: AddRecurringTaskUseCase,
    private val reminderScheduler: AndroidTaskReminderScheduler
) : ViewModel() {
    private val mutableState = MutableStateFlow(TasksUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { tasks ->
                mutableState.value = mutableState.value.copy(tasks = tasks)
            }
        }
    }

    fun addTask(
        name: String,
        frequency: RecurringTask.Frequency,
        hour: Int,
        minute: Int,
        weekday: Int?,
        dayOfMonth: Int?,
        onSuccess: () -> Unit
    ) {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            try {
                val task = addTaskUseCase(name, frequency, hour, minute, weekday, dayOfMonth)
                reminderScheduler.schedule(task)
                mutableState.value = mutableState.value.copy(
                    message = "Task reminder scheduled.",
                    error = null,
                    notificationPermissionRequestId =
                        mutableState.value.notificationPermissionRequestId + 1
                )
                onSuccess()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not add this task."
                )
            } finally {
                mutableState.value = mutableState.value.copy(isSaving = false)
            }
        }
    }

    fun setFulfilled(task: RecurringTask, fulfilled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setFulfilled(task.id, fulfilled)
                mutableState.value = mutableState.value.copy(error = null)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = exception.localizedMessage ?: "Could not update this task."
                )
            }
        }
    }

    fun onNotificationPermissionDenied() {
        mutableState.value = mutableState.value.copy(
            message = null,
            error = "Notification permission is off. Enable it in system settings to receive task reminders."
        )
    }

    fun clearScheduledMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    companion object {
        fun factory(
            repository: RecurringTaskRepository,
            addTaskUseCase: AddRecurringTaskUseCase,
            reminderScheduler: AndroidTaskReminderScheduler
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TasksViewModel::class.java))
                return TasksViewModel(repository, addTaskUseCase, reminderScheduler) as T
            }
        }
    }
}
