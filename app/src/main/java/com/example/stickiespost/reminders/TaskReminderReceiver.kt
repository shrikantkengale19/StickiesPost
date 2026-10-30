package com.example.stickiespost.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.stickiespost.R
import com.example.stickiespost.MainActivity
import com.example.stickiespost.data.local.NotesDatabase
import com.example.stickiespost.data.repository.RoomRecurringTaskRepository
import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.domain.usecase.NextTaskReminder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withRepository(context) { repository ->
                        val scheduler = AndroidTaskReminderScheduler(context)
                        repository.getAll().forEach { task ->
                            val nextReminder = if (
                                task.nextReminderAtMillis > System.currentTimeMillis()
                            ) {
                                task.nextReminderAtMillis
                            } else {
                                NextTaskReminder.calculate(
                                    frequency = task.frequency,
                                    hour = task.hour,
                                    minute = task.minute,
                                    weekday = task.weekday,
                                    dayOfMonth = task.dayOfMonth
                                ).also { repository.advanceOccurrence(task.id, it) }
                            }
                            scheduler.schedule(task.id, nextReminder)
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not restore task reminders after device startup.", exception)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        if (taskId == INVALID_TASK_ID) {
            Log.e(TAG, "Task reminder is missing its task ID.")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withRepository(context) { repository ->
                    val task = repository.getById(taskId)
                    if (task == null) {
                        Log.w(TAG, "Skipping reminder for missing task $taskId.")
                        return@withRepository
                    }

                    if (!task.isFulfilled) {
                        showNotification(context, task)
                    }
                    val nextReminder = NextTaskReminder.calculate(
                        frequency = task.frequency,
                        hour = task.hour,
                        minute = task.minute,
                        weekday = task.weekday,
                        dayOfMonth = task.dayOfMonth
                    )
                    repository.advanceOccurrence(task.id, nextReminder)
                    AndroidTaskReminderScheduler(context).schedule(task.id, nextReminder)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Could not process task reminder.", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, task: RecurringTask) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping task notification because notification permission is not granted.")
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.task_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.task_reminder_title))
            .setContentText(task.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.name))
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    task.id.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }

    private suspend fun <T> withRepository(
        context: Context,
        action: suspend (RoomRecurringTaskRepository) -> T
    ): T {
        val database = Room.databaseBuilder(
            context.applicationContext,
            NotesDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                NotesDatabase.MIGRATION_1_2,
                NotesDatabase.MIGRATION_2_3,
                NotesDatabase.MIGRATION_3_4,
                NotesDatabase.MIGRATION_4_5
            )
            .build()
        return try {
            action(RoomRecurringTaskRepository(database.recurringTaskDao()))
        } finally {
            database.close()
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "recurring_task_id"
        private const val INVALID_TASK_ID = -1L
        private const val CHANNEL_ID = "recurring_task_reminders"
        private const val TAG = "TaskReminderReceiver"
        private const val DATABASE_NAME = "stickiespost_notes.db"
    }
}
