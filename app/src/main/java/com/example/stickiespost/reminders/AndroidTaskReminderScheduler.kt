package com.example.stickiespost.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.stickiespost.domain.model.RecurringTask

class AndroidTaskReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(task: RecurringTask) {
        val intent = Intent(appContext, TaskReminderReceiver::class.java)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            task.nextReminderAtMillis,
            pendingIntent(task.id, intent)
        )
    }

    fun schedule(taskId: Long, reminderAtMillis: Long) {
        val intent = Intent(appContext, TaskReminderReceiver::class.java)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAtMillis,
            pendingIntent(taskId, intent)
        )
    }

    private fun pendingIntent(taskId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
