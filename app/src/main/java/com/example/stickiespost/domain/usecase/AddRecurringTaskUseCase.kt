package com.example.stickiespost.domain.usecase

import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.domain.repository.RecurringTaskRepository

class AddRecurringTaskUseCase(private val repository: RecurringTaskRepository) {
    suspend operator fun invoke(
        name: String,
        frequency: RecurringTask.Frequency,
        hour: Int,
        minute: Int,
        weekday: Int? = null,
        dayOfMonth: Int? = null
    ): RecurringTask {
        val taskName = name.trim()
        require(taskName.isNotEmpty()) { "Enter a task name." }
        when (frequency) {
            RecurringTask.Frequency.DAILY -> Unit
            RecurringTask.Frequency.WEEKLY -> requireNotNull(weekday) {
                "Choose a weekday for a weekly task."
            }
            RecurringTask.Frequency.MONTHLY -> requireNotNull(dayOfMonth) {
                "Choose a day of the month for a monthly task."
            }
        }
        val nextReminder = NextTaskReminder.calculate(
            frequency = frequency,
            hour = hour,
            minute = minute,
            weekday = weekday,
            dayOfMonth = dayOfMonth
        )
        return repository.add(
            RecurringTask(
                name = taskName,
                frequency = frequency,
                hour = hour,
                minute = minute,
                weekday = weekday,
                dayOfMonth = dayOfMonth,
                nextReminderAtMillis = nextReminder
            )
        )
    }
}
