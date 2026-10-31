package com.woody.planner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.woody.planner.MainActivity
import com.woody.planner.data.SettingsRepository
import com.woody.planner.data.TaskDao
import com.woody.planner.data.toDomain
import com.woody.planner.domain.ReminderMath
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Планирует напоминания через AlarmManager: для каждой задачи — ровно один будильник
 * на ближайшее срабатывание. Никаких сервисов и периодической работы в фоне:
 * система будит приложение только в момент напоминания.
 *
 * По умолчанию будильник ставится как «будильник-часы» ([AlarmManager.setAlarmClock]): такие срабатывания
 * система не откладывает в режиме сна, а прошивки с агрессивной экономией (Xiaomi, Huawei, Oppo…)
 * не отменяют их у закрытых приложений. Расход заряда тот же — одно пробуждение на напоминание.
 */
class ReminderScheduler(
    private val context: Context,
    private val taskDao: () -> TaskDao,
    private val settings: () -> SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    val canScheduleExact: Boolean get() = alarmManager.canScheduleExactAlarms()

    /** Ставит (или снимает) оба будильника задачи: напоминание и проверку «не выполнено». */
    suspend fun schedule(taskId: Long) {
        val dao = taskDao()
        val task = dao.get(taskId)?.toDomain()
        val nudge = settings().missedNudgeMinutes.first()
        if (task == null || (task.reminderOffsetMin == null && (nudge == 0 || task.time == null))) {
            cancel(taskId)
            return
        }
        val handled = buildSet {
            dao.completionsOf(taskId).forEach { add(it.epochDay) }
            dao.skipsOf(taskId).forEach { add(it.epochDay) }
        }
        val now = LocalDateTime.now()
        val trigger = ReminderMath.nextTrigger(task, now) { it.toEpochDay() in handled }
        if (trigger == null) {
            alarmManager.cancel(firePendingIntent(taskId, null))
        } else {
            setAlarm(taskId, trigger.occurrence, trigger.at.toMillis())
        }
        val missed = ReminderMath.nextMissedCheck(task, nudge, now) { it.toEpochDay() in handled }
        if (missed == null) {
            alarmManager.cancel(missedPendingIntent(taskId, null))
        } else {
            set(missed.at.toMillis(), missedPendingIntent(taskId, missed.occurrence), missed.occurrence)
        }
    }

    suspend fun rescheduleAll() {
        taskDao().withAlarms().forEach { schedule(it.id) }
    }

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun snooze(taskId: Long, occurrence: LocalDate, minutes: Long) {
        setAlarm(taskId, occurrence, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Пробное напоминание через [delayMs] — проверяет всю цепочку: будильник, приёмник, канал, звук. */
    suspend fun scheduleTest(delayMs: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            TEST_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_TEST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        set(System.currentTimeMillis() + delayMs, pending, openDay = null)
    }

    fun dismissNotification(taskId: Long) = Notifications.cancel(context, taskId)

    fun cancel(taskId: Long) {
        alarmManager.cancel(firePendingIntent(taskId, null))
        alarmManager.cancel(missedPendingIntent(taskId, null))
    }

    private companion object {
        /** Код запроса пробного будильника — вне диапазона id задач. */
        const val TEST_REQUEST_CODE = Int.MAX_VALUE - 7

        /** Код запроса окна «показать будильник» — тоже вне диапазона id задач. */
        const val SHOW_REQUEST_CODE = Int.MAX_VALUE - 8
    }

    private suspend fun setAlarm(taskId: Long, occurrence: LocalDate, atMillis: Long) {
        set(atMillis, firePendingIntent(taskId, occurrence), occurrence)
    }

    private suspend fun set(atMillis: Long, pending: PendingIntent, openDay: LocalDate?) {
        when {
            // Без разрешения на точные будильники система может сдвинуть срабатывание на несколько минут.
            !alarmManager.canScheduleExactAlarms() ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            settings().alarmClockReminders.first() ->
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showIntent(openDay)), pending)
            else ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    /** Нажатие на будильник в шторке открывает день напоминания. */
    private fun showIntent(day: LocalDate?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        day?.let { intent.putExtra(Notifications.EXTRA_OPEN_DAY, it.toEpochDay()) }
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun firePendingIntent(taskId: Long, occurrence: LocalDate?): PendingIntent =
        taskPendingIntent(ReminderReceiver.ACTION_FIRE, taskId, occurrence)

    /** Отдельное действие — отдельный PendingIntent: оба будильника задачи живут независимо. */
    private fun missedPendingIntent(taskId: Long, occurrence: LocalDate?): PendingIntent =
        taskPendingIntent(ReminderReceiver.ACTION_MISSED, taskId, occurrence)

    private fun taskPendingIntent(action: String, taskId: Long, occurrence: LocalDate?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
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
