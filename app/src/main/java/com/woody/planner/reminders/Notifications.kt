package com.woody.planner.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.woody.planner.MainActivity
import com.woody.planner.R
import com.woody.planner.domain.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Notifications {
    private const val CHANNEL_ID = "reminders"
    const val EXTRA_OPEN_DAY = "open_day"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Напоминания о задачах", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления в заданное время"
            },
        )
    }

    /** Учитывает и runtime-разрешение (Android 13+), и выключенные в системе уведомления (любая версия). */
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Разрешение можно запросить диалогом только на Android 13+, на 12 — только через настройки. */
    val canRequestPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

    fun show(context: Context, task: Task, occurrence: LocalDate) {
        if (!canPost(context)) return
        ensureChannel(context)
        val id = task.id.toInt()
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_DAY, occurrence.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = buildString {
            task.time?.let { append(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            if (task.note.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(task.note)
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_woody)
            .setColor(0xFFE63946.toInt())
            .setContentTitle(task.title)
            .setContentText(text.ifEmpty { "Пора заняться задачей" })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Готово", actionIntent(context, ReminderReceiver.ACTION_DONE, task.id, occurrence, 1))
            .addAction(0, "Через 15 мин", actionIntent(context, ReminderReceiver.ACTION_SNOOZE, task.id, occurrence, 2))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто не показываем.
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun actionIntent(context: Context, action: String, taskId: Long, day: LocalDate, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt() * 4 + slot,
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderReceiver.EXTRA_EPOCH_DAY, day.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
