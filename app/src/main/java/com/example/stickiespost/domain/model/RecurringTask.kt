package com.example.stickiespost.domain.model

data class RecurringTask(
    val id: Long = 0,
    val name: String,
    val frequency: Frequency,
    val hour: Int,
    val minute: Int,
    val weekday: Int? = null,
    val dayOfMonth: Int? = null,
    val nextReminderAtMillis: Long,
    val isFulfilled: Boolean = false
) {
    enum class Frequency(val label: String) {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly")
    }
}
