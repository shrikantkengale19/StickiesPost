package com.example.stickiespost.domain.usecase

import com.example.stickiespost.domain.model.RecurringTask
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

object NextTaskReminder {
    fun calculate(
        frequency: RecurringTask.Frequency,
        hour: Int,
        minute: Int,
        weekday: Int? = null,
        dayOfMonth: Int? = null,
        afterMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        require(hour in 0..23) { "Choose a valid reminder hour." }
        require(minute in 0..59) { "Choose a valid reminder minute." }

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zoneId)
        val time = LocalTime.of(hour, minute)
        val next = when (frequency) {
            RecurringTask.Frequency.DAILY -> {
                var candidate = now.toLocalDate().atTime(time)
                if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
                candidate
            }

            RecurringTask.Frequency.WEEKLY -> {
                val selectedWeekday = requireNotNull(weekday) {
                    "Choose a weekday for a weekly task."
                }
                require(selectedWeekday in 1..7) { "Choose a valid weekday." }
                var candidateDate = now.toLocalDate().with(
                    java.time.temporal.TemporalAdjusters.nextOrSame(
                        java.time.DayOfWeek.of(selectedWeekday)
                    )
                )
                var candidate = candidateDate.atTime(time)
                if (!candidate.isAfter(now)) {
                    candidateDate = candidateDate.plusWeeks(1)
                    candidate = candidateDate.atTime(time)
                }
                candidate
            }

            RecurringTask.Frequency.MONTHLY -> {
                val selectedDay = requireNotNull(dayOfMonth) {
                    "Choose a day of the month for a monthly task."
                }
                require(selectedDay in 1..31) { "Choose a valid day of the month." }
                var month = YearMonth.from(now)
                var candidate = month.atDay(selectedDay.coerceAtMost(month.lengthOfMonth()))
                    .atTime(time)
                if (!candidate.isAfter(now)) {
                    month = month.plusMonths(1)
                    candidate = month.atDay(selectedDay.coerceAtMost(month.lengthOfMonth()))
                        .atTime(time)
                }
                candidate
            }
        }

        return next.atZone(zoneId).toInstant().toEpochMilli()
    }
}
