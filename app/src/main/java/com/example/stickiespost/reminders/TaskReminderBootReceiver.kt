package com.example.stickiespost.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.sendBroadcast(
                Intent(context, TaskReminderReceiver::class.java)
                    .setAction(Intent.ACTION_BOOT_COMPLETED)
            )
        }
    }
}
