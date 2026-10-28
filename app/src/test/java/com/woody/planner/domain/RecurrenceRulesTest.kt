package com.woody.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceRulesTest {
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun `one-off task occurs only on its date`() {
        val rule = Recurrence.None
        assertTrue(RecurrenceRules.occursOn(monday, rule, monday))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.plusDays(1)))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.minusDays(1)))
    }

    @Test
    fun `daily never occurs before start and respects end date`() {
        val rule = Recurrence(RecurrenceType.DAILY, endDate = monday.plusDays(2))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.minusDays(1)))
        assertTrue(RecurrenceRules.occursOn(monday, rule, monday.plusDays(2)))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.plusDays(3)))
    }

    @Test
    fun `weekly uses day mask`() {
        val gym = Recurrence(RecurrenceType.WEEKLY, daysMask = Recurrence.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val hits = (0L until 7L).map { monday.plusDays(it) }.filter { RecurrenceRules.occursOn(monday, gym, it) }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), hits.map { it.dayOfWeek })
    }

    @Test
    fun `weekly with empty mask falls back to start weekday`() {
        val rule = Recurrence(RecurrenceType.WEEKLY, daysMask = 0)
        assertTrue(RecurrenceRules.occursOn(monday, rule, monday.plusWeeks(3)))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.plusDays(1)))
    }

    @Test
    fun `every n days counts from start`() {
        val rule = Recurrence(RecurrenceType.EVERY_N_DAYS, interval = 3)
        assertTrue(RecurrenceRules.occursOn(monday, rule, monday.plusDays(6)))
        assertFalse(RecurrenceRules.occursOn(monday, rule, monday.plusDays(4)))
    }

    @Test
    fun `monthly on the 31st clamps to short months`() {
        val start = LocalDate.of(2026, 1, 31)
        val rule = Recurrence(RecurrenceType.MONTHLY)
        assertTrue(RecurrenceRules.occursOn(start, rule, LocalDate.of(2026, 2, 28)))
        assertTrue(RecurrenceRules.occursOn(start, rule, LocalDate.of(2026, 4, 30)))
        assertFalse(RecurrenceRules.occursOn(start, rule, LocalDate.of(2026, 4, 29)))
    }

    @Test
    fun `next occurrence skips to matching day`() {
        val rule = Recurrence(RecurrenceType.WEEKLY, daysMask = DayOfWeek.FRIDAY.bit)
        assertEquals(LocalDate.of(2026, 10, 30), RecurrenceRules.nextOccurrence(monday, rule, monday))
        assertNull(RecurrenceRules.nextOccurrence(monday, Recurrence.None, monday.plusDays(1)))
        assertEquals(monday, RecurrenceRules.nextOccurrence(monday, Recurrence.None, monday.minusDays(5)))
    }
}
