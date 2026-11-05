package com.woody.planner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.woody.planner.MainActivity
import com.woody.planner.data.SettingsRepository
import com.woody.planner.data.TaskDao
import com.woody.planner.data.toDomain
import com.woody.planner.domain.ReminderMath
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Планирует напоминания через AlarmManager: для каждой задачи — ровно один будильник
 * на ближайшее срабатывание. Никаких сервисов и периодической работы в фоне:
 * система будит приложение только в момент напоминания.
 *
 * По умолчанию будильник ставится как «будильник-часы» ([AlarmManager.setAlarmClock]): такие срабатывания
 * система не откладывает в режиме сна, а прошивки с агрессивной экономией (Xiaomi, Huawei, Oppo…)
 * не отменяют их у закрытых приложений. Расход заряда тот же — одно пробуждение на напоминание.
 *
 * Каждый поставленный будильник записывается ([records]). Если прошивка его так и не доставила
 * (vivo, Xiaomi «замораживают» закрытые приложения), [catchUp] при следующем пробуждении процесса —
 * запуск, разблокировка, ежечасное обновление виджета — покажет напоминание с опозданием, а не потеряет его.
 */
class ReminderScheduler(
    private val context: Context,
    private val taskDao: () -> TaskDao,
    private val settings: () -> SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** «действие|id задачи» → «время срабатывания,день»; время −1 — уже показано. */
    private val records = context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    /** id задачи → день, о котором уже напомнили: повторно «во время задачи» о нём не напоминаем. */
    private val shown = context.getSharedPreferences("reminder_shown", Context.MODE_PRIVATE)
    private val lock = Mutex()

    val canScheduleExact: Boolean get() = alarmManager.canScheduleExactAlarms()

    /**
     * Ставит (или снимает) оба будильника задачи: напоминание и проверку «не выполнено».
     * Если время «за N минут» уже прошло, а напоминания об этом дне ещё не было (задачу только что создали
     * или прошивка не доставила будильник), — напомнит во время самой задачи.
     */
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
        val remindedDay = shown.getLong(taskId.toString(), Long.MIN_VALUE)
        val trigger = ReminderMath.nextTrigger(task, now, lateFallback = true) {
            it.toEpochDay() in handled || it.toEpochDay() == remindedDay
        }
        if (trigger == null) {
            alarmManager.cancel(firePendingIntent(taskId, null))
            forget(ReminderReceiver.ACTION_FIRE, taskId)
        } else {
            setAlarm(taskId, trigger.occurrence, trigger.at.toMillis())
        }
        val missed = ReminderMath.nextMissedCheck(task, nudge, now) { it.toEpochDay() in handled }
        if (missed == null) {
            alarmManager.cancel(missedPendingIntent(taskId, null))
            forget(ReminderReceiver.ACTION_MISSED, taskId)
        } else {
            val at = missed.at.toMillis()
            set(at, missedPendingIntent(taskId, missed.occurrence), missed.occurrence)
            write(ReminderReceiver.ACTION_MISSED, taskId, at, missed.occurrence)
        }
    }

    suspend fun rescheduleAll() {
        // Сначала — то, что прошивка не доставила: перепланирование перезаписало бы эти будильники следующими.
        catchUp()
        // Ошибка с одной задачей не должна оставить без напоминаний остальные.
        taskDao().withAlarms().forEach { task ->
            runCatching { schedule(task.id) }.onFailure { Log.w("Woody", "schedule ${task.id}", it) }
        }
    }

    /**
     * Сработал будильник: показать напоминание (если задача ещё не отмечена) и поставить следующее.
     * Не показывает то, что уже показал [catchUp] (будильник мог прийти с опозданием следом).
     */
    suspend fun onAlarm(action: String, taskId: Long, occurrence: LocalDate) {
        val fresh = claim(action, taskId, occurrence)
        if (fresh) markShown(action, taskId, occurrence)
        // Сначала следующий будильник: если показ упадёт, цепочка повторяющихся напоминаний не оборвётся.
        runCatching { next(action, taskId) }.onFailure { Log.w("Woody", "reschedule $taskId", it) }
        if (fresh) show(action, taskId, occurrence)
    }

    private suspend fun next(action: String, taskId: Long) {
        if (action == ReminderReceiver.ACTION_SNOOZED) forget(action, taskId) else schedule(taskId)
    }

    /** Задаче поменяли время — напоминание о сегодняшнем дне снова нужно. */
    fun forgetShown(taskId: Long) {
        shown.edit().remove(taskId.toString()).apply()
    }

    private fun markShown(action: String, taskId: Long, occurrence: LocalDate) {
        if (action != ReminderReceiver.ACTION_MISSED) shown.edit().putLong(taskId.toString(), occurrence.toEpochDay()).apply()
    }

    /**
     * Будильники, время которых прошло, а приёмник так и не вызвали: показываем (если опоздание не больше
     * [CatchUpWindowMs]) и ставим следующие.
     */
    suspend fun catchUp() {
        val now = System.currentTimeMillis()
        val due = lock.withLock {
            records.all.mapNotNull { (key, value) ->
                val record = Record.parse(key, value as? String) ?: return@mapNotNull null
                if (record.at < 0 || record.at > now - GraceMs) return@mapNotNull null
                // Помечаем сразу, под замком: опоздавший будильник следом уже ничего не покажет.
                records.edit().putString(key, "-1,${record.day.toEpochDay()}").apply()
                record
            }
        }
        due.forEach { record ->
            runCatching {
                markShown(record.action, record.taskId, record.day)
                next(record.action, record.taskId)
                if (now - record.at <= CatchUpWindowMs) show(record.action, record.taskId, record.day)
            }.onFailure { Log.w("Woody", "catch up ${record.taskId}", it) }
        }
    }

    private suspend fun show(action: String, taskId: Long, occurrence: LocalDate) {
        val dao = taskDao()
        val task = dao.get(taskId)?.toDomain() ?: return
        val epochDay = occurrence.toEpochDay()
        // Задачу уже отметили или пропустили — напоминать не о чем.
        if (dao.completionsOf(taskId).any { it.epochDay == epochDay } || dao.skipsOf(taskId).any { it.epochDay == epochDay }) return
        Notifications.show(context, task, occurrence, settings().language.first(), missed = action == ReminderReceiver.ACTION_MISSED)
    }

    /** true — этот будильник ещё никто не показал (теперь он помечен показанным). */
    private suspend fun claim(action: String, taskId: Long, occurrence: LocalDate): Boolean = lock.withLock {
        val key = key(action, taskId)
        val record = Record.parse(key, records.getString(key, null))
        if (record != null && (record.at < 0 || record.day != occurrence)) return@withLock false
        // О дне уже напомнили (например, с опозданием), а прошивка всё-таки доставила придержанный будильник.
        if (action == ReminderReceiver.ACTION_FIRE && shown.getLong(taskId.toString(), Long.MIN_VALUE) == occurrence.toEpochDay()) {
            return@withLock false
        }
        records.edit().putString(key, "-1,${occurrence.toEpochDay()}").apply()
        true
    }

    private fun write(action: String, taskId: Long, at: Long, occurrence: LocalDate) {
        records.edit().putString(key(action, taskId), "$at,${occurrence.toEpochDay()}").apply()
    }

    private fun forget(action: String, taskId: Long) {
        records.edit().remove(key(action, taskId)).apply()
    }

    private class Record(val action: String, val taskId: Long, val at: Long, val day: LocalDate) {
        companion object {
            fun parse(key: String, value: String?): Record? {
                val taskId = key.substringAfter('|', "").toLongOrNull() ?: return null
                val parts = value?.split(',') ?: return null
                val at = parts.getOrNull(0)?.toLongOrNull() ?: return null
                val day = parts.getOrNull(1)?.toLongOrNull() ?: return null
                return Record(key.substringBefore('|'), taskId, at, LocalDate.ofEpochDay(day))
            }
        }
    }

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * «Отложить»: свой будильник, отдельный от основного, — его не перезапишет перепланирование
     * при запуске приложения или проверка «не выполнено».
     */
    suspend fun snooze(taskId: Long, occurrence: LocalDate, minutes: Long) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        set(at, taskPendingIntent(ReminderReceiver.ACTION_SNOOZED, taskId, occurrence), occurrence)
        write(ReminderReceiver.ACTION_SNOOZED, taskId, at, occurrence)
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
        alarmManager.cancel(taskPendingIntent(ReminderReceiver.ACTION_SNOOZED, taskId, null))
        listOf(ReminderReceiver.ACTION_FIRE, ReminderReceiver.ACTION_MISSED, ReminderReceiver.ACTION_SNOOZED)
            .forEach { forget(it, taskId) }
        forgetShown(taskId)
    }

    private companion object {
        /** Код запроса пробного будильника — вне диапазона id задач. */
        const val TEST_REQUEST_CODE = Int.MAX_VALUE - 7

        /** Код запроса окна «показать будильник» — тоже вне диапазона id задач. */
        const val SHOW_REQUEST_CODE = Int.MAX_VALUE - 8

        /** Будильник опаздывает больше чем на минуту — значит, его не доставили. */
        const val GraceMs = 60_000L

        /** Опоздание больше — уже не напоминаем: задача давно прошла. */
        const val CatchUpWindowMs = 4 * 60 * 60 * 1000L

        fun key(action: String, taskId: Long) = "$action|$taskId"
    }

    private suspend fun setAlarm(taskId: Long, occurrence: LocalDate, atMillis: Long) {
        set(atMillis, firePendingIntent(taskId, occurrence), occurrence)
        write(ReminderReceiver.ACTION_FIRE, taskId, atMillis, occurrence)
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
        day?.let {
            intent.putExtra(Notifications.EXTRA_OPEN_DAY, it.toEpochDay())
            // Свои данные у каждого дня — иначе все будильники открывали бы день последнего поставленного.
            intent.data = "woody://day/${it.toEpochDay()}".toUri()
        }
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
