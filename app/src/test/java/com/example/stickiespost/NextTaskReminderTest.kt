package com.example.stickiespost

import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.domain.usecase.NextTaskReminder
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class NextTaskReminderTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun dailyReminderUsesTodayWhenTimeHasNotPassed() {
        val now = epochMillis(2025, 1, 10, 9, 0)

        val next = NextTaskReminder.calculate(
            frequency = RecurringTask.Frequency.DAILY,
            hour = 10,
            minute = 30,
            afterMillis = now,
            zoneId = zone
        )

        assertEquals(epochMillis(2025, 1, 10, 10, 30), next)
    }

    @Test
    fun weeklyReminderUsesNextSelectedWeekday() {
        val now = epochMillis(2025, 1, 10, 11, 0) // Friday

        val next = NextTaskReminder.calculate(
            frequency = RecurringTask.Frequency.WEEKLY,
            hour = 10,
            minute = 0,
            weekday = 1, // Monday
            afterMillis = now,
            zoneId = zone
        )

        assertEquals(epochMillis(2025, 1, 13, 10, 0), next)
    }

    @Test
    fun monthlyReminderClampsToLastDayOfShortMonth() {
        val now = epochMillis(2025, 2, 10, 9, 0)

        val next = NextTaskReminder.calculate(
            frequency = RecurringTask.Frequency.MONTHLY,
            hour = 8,
            minute = 15,
            dayOfMonth = 31,
            afterMillis = now,
            zoneId = zone
        )

        assertEquals(epochMillis(2025, 2, 28, 8, 15), next)
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
