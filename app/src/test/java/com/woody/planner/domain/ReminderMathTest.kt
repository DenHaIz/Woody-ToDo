package com.woody.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderMathTest {
    private val today = LocalDate.of(2026, 10, 28) // среда

    private fun task(
        date: LocalDate = today,
        time: LocalTime? = LocalTime.of(19, 0),
        offset: Int? = 30,
        recurrence: Recurrence = Recurrence.None,
    ) = Task(1, "Спортзал", "", null, date, time, offset, recurrence, 0, 0)

    @Test
    fun `no reminder when offset is null`() {
        assertNull(ReminderMath.nextTrigger(task(offset = null), today.atTime(8, 0)) { false })
    }

    @Test
    fun `one-off reminder fires offset minutes before`() {
        val trigger = ReminderMath.nextTrigger(task(), today.atTime(8, 0)) { false }
        assertEquals(LocalDateTime.of(today, LocalTime.of(18, 30)), trigger?.at)
        assertEquals(today, trigger?.occurrence)
    }

    @Test
    fun `passed one-off reminder is not rescheduled`() {
        assertNull(ReminderMath.nextTrigger(task(), today.atTime(18, 45)) { false })
    }

    @Test
    fun `task without time uses default morning time`() {
        val trigger = ReminderMath.nextTrigger(task(time = null, offset = 0), today.atTime(7, 0)) { false }
        assertEquals(today.atTime(ReminderMath.DefaultTime), trigger?.at)
    }

    @Test
    fun `recurring reminder moves to next occurrence after it passed`() {
        val gym = task(
            date = today.minusWeeks(1),
            recurrence = Recurrence(RecurrenceType.WEEKLY, daysMask = Recurrence.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        )
        val trigger = ReminderMath.nextTrigger(gym, today.atTime(20, 0)) { false }
        assertEquals(LocalDate.of(2026, 11, 2), trigger?.occurrence)
    }

    @Test
    fun `completed occurrence is skipped`() {
        val daily = task(date = today.minusDays(3), recurrence = Recurrence(RecurrenceType.DAILY))
        val trigger = ReminderMath.nextTrigger(daily, today.atTime(8, 0)) { it == today }
        assertEquals(today.plusDays(1), trigger?.occurrence)
    }

    @Test
    fun `late reminder of a just saved task falls back to task time`() {
        // 18:45, задача в 19:00 «за 30 минут»: 18:30 прошло — напомнить в 19:00, но только при сохранении.
        val now = today.atTime(18, 45)
        assertEquals(today.atTime(19, 0), ReminderMath.nextTrigger(task(), now, lateFallback = true) { false }?.at)
        assertNull(ReminderMath.nextTrigger(task(), now) { false })
    }

    @Test
    fun `missed check after midnight survives`() {
        // Задача в 23:50, проверка через 30 минут — в 00:20 следующего дня; в 00:10 она ещё впереди.
        val late = task(time = LocalTime.of(23, 50), offset = null)
        val check = ReminderMath.nextMissedCheck(late, 30, today.plusDays(1).atTime(0, 10)) { false }
        assertEquals(today.plusDays(1).atTime(0, 20), check?.at)
        assertEquals(today, check?.occurrence)
    }
}
