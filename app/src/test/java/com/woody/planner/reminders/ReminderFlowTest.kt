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
