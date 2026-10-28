package com.woody.planner.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.woody.planner.WoodyApp
import com.woody.planner.data.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Короткая работа в ответ на будильник или кнопку уведомления, затем процесс снова засыпает. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WoodyApp).container
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it >= 0 } ?: return
        val day = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let(LocalDate::ofEpochDay)
            ?: LocalDate.now()

        runAsync {
            when (intent.action) {
                ACTION_FIRE -> {
                    val task = container.database.taskDao().get(taskId)?.toDomain()
                    if (task != null) Notifications.show(context, task, day)
                    container.reminders.schedule(taskId)
                }
                ACTION_DONE -> {
                    Notifications.cancel(context, taskId)
                    container.repository.setDone(taskId, day, done = true)
                }
                ACTION_SNOOZE -> {
                    Notifications.cancel(context, taskId)
                    container.reminders.snooze(taskId, day, minutes = 15)
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.woody.planner.REMINDER_FIRE"
        const val ACTION_DONE = "com.woody.planner.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.woody.planner.REMINDER_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
    }
}

/** Будильники сбрасываются при перезагрузке и смене времени — восстанавливаем их. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                val container = (context.applicationContext as WoodyApp).container
                runAsync { container.reminders.rescheduleAll() }
            }
        }
    }
}
