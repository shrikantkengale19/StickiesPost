package com.example.stickiespost.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.reminder.ReminderScheduler

class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(item: NoteItem) {
        val reminderAt = requireNotNull(item.reminderAtMillis) {
            "A reminder time is required to schedule this note."
        }
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_ITEM_ID, item.id)
            .putExtra(ReminderReceiver.EXTRA_TEXT, item.text)
            .putExtra(ReminderReceiver.EXTRA_CATEGORY, item.category.label)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, pendingIntent)
    }
}
