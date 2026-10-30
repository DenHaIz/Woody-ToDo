package com.woody.planner.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.woody.planner.WoodyApp
import com.woody.planner.data.TaskDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun reminderFiresAtExactMinuteAndPostsNotification() {
        val at = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
        runBlocking {
            app.container.repository.save(
                TaskDraft(title = "Спортзал", date = at.toLocalDate(), time = at.toLocalTime(), reminderOffsetMin = 10),
            )
        }

        // Ровно один будильник, точный, за 10 минут до задачи.
        val scheduled = alarms.scheduledAlarms
        assertEquals(1, scheduled.size)
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        val expected = at.minusMinutes(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, alarm!!.triggerAtTime)
        assertTrue("будильник должен будить устройство", alarm.allowWhileIdle)

        // Срабатывание будильника → уведомление.
        alarm.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        val posted = notifications.activeNotifications.single()
        assertEquals("Спортзал", posted.notification.extras.getString("android.title"))
        assertEquals("reminders_knock", posted.notification.channelId)
        assertNotNull(notifications.getNotificationChannel("reminders_knock").sound)
    }

    @Test
    fun testReminderFromSettingsPostsNotification() {
        app.container.reminders.scheduleTest(5_000)
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        alarm!!.operation!!.send()
        waitFor { notifications.activeNotifications.isNotEmpty() }
        assertEquals("Woody на связи", notifications.activeNotifications.single().notification.extras.getString("android.title"))
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
