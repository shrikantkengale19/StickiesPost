package com.example.stickiespost.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.stickiespost.R
import com.example.stickiespost.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping reminder notification because notification permission is not granted.")
            return
        }

        val text = intent.getStringExtra(EXTRA_TEXT)
            ?: throw IllegalArgumentException("Reminder notification is missing note text.")
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (category.isBlank()) "Reminder" else category)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(itemId.hashCode(), notification)
    }

    companion object {
        const val EXTRA_ITEM_ID = "reminder_item_id"
        const val EXTRA_TEXT = "reminder_text"
        const val EXTRA_CATEGORY = "reminder_category"
        private const val CHANNEL_ID = "note_reminders"
        private const val TAG = "ReminderReceiver"
    }
}
