package com.example.stickiespost.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stickiespost.domain.model.RecurringTask

@Entity(tableName = "recurring_tasks")
data class RecurringTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val frequency: String,
    val hour: Int,
    val minute: Int,
    val weekday: Int?,
    val dayOfMonth: Int?,
    val nextReminderAtMillis: Long,
    val isFulfilled: Boolean
) {
    fun toDomain() = RecurringTask(
        id = id,
        name = name,
        frequency = RecurringTask.Frequency.valueOf(frequency),
        hour = hour,
        minute = minute,
        weekday = weekday,
        dayOfMonth = dayOfMonth,
        nextReminderAtMillis = nextReminderAtMillis,
        isFulfilled = isFulfilled
    )

    companion object {
        fun fromDomain(task: RecurringTask) = RecurringTaskEntity(
            id = task.id,
            name = task.name,
            frequency = task.frequency.name,
            hour = task.hour,
            minute = task.minute,
            weekday = task.weekday,
            dayOfMonth = task.dayOfMonth,
            nextReminderAtMillis = task.nextReminderAtMillis,
            isFulfilled = task.isFulfilled
        )
    }
}
