package com.woody.planner.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.woody.planner.WoodyApp
import com.woody.planner.data.AppLanguage
import com.woody.planner.data.TaskDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Вся цепочка напоминания: сохранение задачи → один точный будильник на нужную минуту →
 * срабатывание → уведомление в канале со стуком дятла.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderFlowTest {

    private val app get() = ApplicationProvider.getApplicationContext<WoodyApp>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    /** На телефоне точные будильники разрешены сразу (USE_EXACT_ALARM); в Robolectric — включаем явно. */
    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun reminderFiresAtExactMinuteAndPostsNotification() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        runBlocking {
            app.container.repository.save(
                TaskDraft(title = "Спортзал", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 10),
            )
        }

        // Ровно один будильник — системный «будильник-часы» (его не откладывает сон и не отменяют прошивки),
        // за 10 минут до задачи.
        val scheduled = alarms.scheduledAlarms
        assertEquals(1, scheduled.size)
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        val expected = at.minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, alarm!!.triggerAtTime)
        assertEquals(expected, app.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime)

        // Срабатывание будильника → уведомление.
        alarm.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        val posted = notifications.activeNotifications.single()
        assertEquals("Спортзал", posted.notification.extras.getString("android.title"))
        assertEquals("reminders_knock", posted.notification.channelId)
        assertNotNull(notifications.getNotificationChannel("reminders_knock").sound)
    }

    @Test
    fun withoutAlarmClockModeUsesExactIdleAlarm() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        runBlocking {
            app.container.settings.setAlarmClockReminders(false)
            app.container.repository.save(
                TaskDraft(title = "Курица", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 0),
            )
        }
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarm!!.triggerAtTime)
        assertTrue("будильник должен будить устройство", alarm.allowWhileIdle)
        assertNull(app.getSystemService(AlarmManager::class.java).nextAlarmClock)
        runBlocking { app.container.settings.setAlarmClockReminders(true) }
    }

    @Test
    fun testReminderFromSettingsPostsNotification() {
        runBlocking { app.container.reminders.scheduleTest(5_000) }
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        alarm!!.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        assertEquals("Woody на связи", notifications.activeNotifications.single().notification.extras.getString("android.title"))
    }

    @Test
    fun notificationFollowsAppLanguage() {
        runBlocking {
            app.container.settings.setLanguage(AppLanguage.English)
            app.container.reminders.scheduleTest(5_000)
        }
        alarms.peekNextScheduledAlarm()!!.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        assertEquals("Woody is here", notifications.activeNotifications.single().notification.extras.getString("android.title"))
        assertEquals("Task reminders", notifications.getNotificationChannel("reminders_knock").name)
        runBlocking { app.container.settings.setLanguage(AppLanguage.Russian) }
    }

    @Test
    fun missedNudgeFiresOnlyWhileTaskIsNotDone() {
        val at = LocalDateTime.now().plusMinutes(20).withSecond(0).withNano(0)
        val dueMillis = { m: Long -> at.plusMinutes(m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val id = runBlocking {
            app.container.settings.setMissedNudgeMinutes(30)
            // Задача без напоминания: будильник нужен только для проверки «не выполнено».
            app.container.repository.save(TaskDraft(title = "Тренировка", date = at.toLocalDate(), time = at.toLocalTime()))
        }
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(dueMillis(30), alarm.triggerAtTime)

        alarm.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        val text = notifications.activeNotifications.single().notification.extras.getCharSequence("android.text").toString()
        assertTrue(text, text.startsWith("Ещё не выполнено"))
        notifications.cancelAll()

        // Выполнили до проверки — будильник сработает (старый), но уведомления не будет.
        runBlocking { app.container.repository.setDone(id, at.toLocalDate(), done = true) }
        alarm.operation!!.send()
        repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertTrue(notifications.activeNotifications.isEmpty())
        runBlocking { app.container.settings.setMissedNudgeMinutes(0) }
    }

    @Test
    fun missedNudgeIsOffByDefault() {
        val at = LocalDateTime.now().plusMinutes(20).withSecond(0).withNano(0)
        runBlocking {
            app.container.repository.save(TaskDraft(title = "Без напоминаний", date = at.toLocalDate(), time = at.toLocalTime()))
        }
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun snoozeSurvivesRescheduleOnAppStart() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        val id = runBlocking {
            app.container.repository.save(
                TaskDraft(title = "Позвонить", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 10),
            )
        }
        runBlocking {
            app.container.reminders.snooze(id, at.toLocalDate(), minutes = 15)
            // Запуск приложения перепланирует всё — отложенное напоминание должно остаться.
            app.container.reminders.rescheduleAll()
        }
        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun snoozedReminderFiresAndPostsNotification() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        val id = runBlocking {
            app.container.repository.save(
                TaskDraft(title = "Отложенная", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 10),
            )
        }
        // Кнопка «Отложить» в уведомлении — как её нажимает пользователь.
        ReminderReceiver().onReceive(
            app,
            android.content.Intent(app, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_SNOOZE)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, id)
                .putExtra(ReminderReceiver.EXTRA_EPOCH_DAY, at.toLocalDate().toEpochDay()),
        )
        waitFor { alarms.scheduledAlarms.size == 2 }
        val snoozed = alarms.scheduledAlarms.minBy { it.triggerAtTime }
        val expected = System.currentTimeMillis() + 15 * 60_000L
        assertTrue(kotlin.math.abs(snoozed.triggerAtTime - expected) < 60_000)
        snoozed.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        val title = notifications.activeNotifications.single().notification.extras.getCharSequence("android.title").toString()
        assertTrue(title, title.contains("Отложенная"))
        notifications.cancelAll()
    }

    @Test
    fun doneOnNotificationOfDeletedTaskIsIgnored() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        val id = runBlocking {
            app.container.repository.save(TaskDraft(title = "Удалённая", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 0))
        }
        runBlocking { app.container.repository.delete(id) }
        ReminderReceiver().onReceive(
            app,
            android.content.Intent(app, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_DONE)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, id)
                .putExtra(ReminderReceiver.EXTRA_EPOCH_DAY, at.toLocalDate().toEpochDay()),
        )
        repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertTrue(runBlocking { app.container.database.taskDao().completionsOf(id) }.isEmpty())
    }

    @Test
    fun reminderWhoseLeadTimePassedComesAtTaskTime() {
        // До задачи 15 минут, напоминание «за 30» — уже поздно; придёт во время задачи.
        val at = LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0)
        runBlocking {
            app.container.repository.save(TaskDraft(title = "Скоро", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 30))
        }
        assertEquals(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test
    fun droppedReminderIsShownLateAndOnlyOnce() {
        val at = LocalDateTime.now().plusMinutes(40).withSecond(0).withNano(0)
        val day = at.toLocalDate()
        val id = runBlocking {
            app.container.repository.save(TaskDraft(title = "Потерянное", date = day, time = at.toLocalTime(), reminderOffsetMin = 10))
        }
        val original = alarms.peekNextScheduledAlarm()!!
        // Прошивка «заморозила» приложение, и будильник пять минут назад так и не пришёл.
        app.getSharedPreferences("reminder_alarms", android.content.Context.MODE_PRIVATE).edit()
            .putString("${ReminderReceiver.ACTION_FIRE}|$id", "${System.currentTimeMillis() - 5 * 60_000},${day.toEpochDay()}")
            .commit()
        // Процесс проснулся (разблокировка, виджет, запуск) — напоминание показано с опозданием.
        runBlocking { app.container.reminders.catchUp() }
        waitFor { notifications.activeNotifications.isNotEmpty() }
        assertEquals("Потерянное", notifications.activeNotifications.single().notification.extras.getString("android.title"))
        notifications.cancelAll()

        // Придержанный будильник всё-таки доставили — второго уведомления нет.
        original.operation!!.send()
        repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test
    fun lateFallbackSurvivesReschedule() {
        // «За 30 минут» уже прошло — напоминание во время задачи не пропадает после перезапуска приложения.
        val at = LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0)
        runBlocking {
            app.container.repository.save(TaskDraft(title = "Скоро", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 30))
            app.container.reminders.rescheduleAll()
        }
        assertEquals(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test
    fun movingTaskLaterRemindsAgainTheSameDay() {
        val at = LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0)
        val id = runBlocking {
            app.container.repository.save(TaskDraft(title = "Перенесённая", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 0))
        }
        alarms.peekNextScheduledAlarm()!!.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        notifications.cancelAll()
        // Напоминание пришло, задачу передвинули на два часа позже — напомнит и в новое время.
        val later = at.plusHours(2)
        runBlocking {
            val task = app.container.database.taskDao().get(id)!!
            app.container.repository.save(
                TaskDraft(id = id, title = task.title, date = later.toLocalDate(), time = later.toLocalTime(), reminderOffsetMin = 0, createdAt = task.createdAt),
            )
        }
        waitFor { alarms.scheduledAlarms.isNotEmpty() }
        assertEquals(later.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)
    }

    /** Приёмник работает в фоновой корутине (goAsync) — ждём результат, прокручивая главный поток. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("условие не выполнилось за 5 секунд", condition())
    }
}
