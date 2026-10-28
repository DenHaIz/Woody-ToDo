package com.woody.planner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.woody.planner.data.TaskDao
import com.woody.planner.data.toDomain
import com.woody.planner.domain.ReminderMath
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планирует напоминания через AlarmManager: для каждой задачи — ровно один будильник
 * на ближайшее срабатывание. Никаких сервисов и периодической работы в фоне:
 * система будит приложение только в момент напоминания.
 */
class ReminderScheduler(
    private val context: Context,
    private val taskDao: () -> TaskDao,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    val canScheduleExact: Boolean get() = alarmManager.canScheduleExactAlarms()

    suspend fun schedule(taskId: Long) {
        val dao = taskDao()
        val task = dao.get(taskId)?.toDomain()
        if (task == null || task.reminderOffsetMin == null) {
            cancel(taskId)
            return
        }
        val handled = buildSet {
            dao.completionsOf(taskId).forEach { add(it.epochDay) }
            dao.skipsOf(taskId).forEach { add(it.epochDay) }
        }
        val trigger = ReminderMath.nextTrigger(task, LocalDateTime.now()) { it.toEpochDay() in handled }
        if (trigger == null) {
            cancel(taskId)
        } else {
            setAlarm(taskId, trigger.occurrence, trigger.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    suspend fun rescheduleAll() {
        taskDao().withReminders().forEach { schedule(it.id) }
    }

    fun snooze(taskId: Long, occurrence: LocalDate, minutes: Long) {
        setAlarm(taskId, occurrence, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(firePendingIntent(taskId, null))
    }

    private fun setAlarm(taskId: Long, occurrence: LocalDate, atMillis: Long) {
        val pending = firePendingIntent(taskId, occurrence)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            // Без разрешения на точные будильники система может сдвинуть срабатывание на несколько минут.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun firePendingIntent(taskId: Long, occurrence: LocalDate?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        occurrence?.let { intent.putExtra(ReminderReceiver.EXTRA_EPOCH_DAY, it.toEpochDay()) }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
