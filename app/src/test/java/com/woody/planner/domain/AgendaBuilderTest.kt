package com.woody.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AgendaBuilderTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val sport = Category(1, "Спорт", 0xFF2F80ED.toInt(), "🏋️", 0)

    private fun task(
        id: Long,
        title: String,
        date: LocalDate = today,
        time: LocalTime? = null,
        recurrence: Recurrence = Recurrence.None,
        categoryId: Long? = null,
    ) = Task(id, title, "", categoryId, date, time, null, recurrence, createdAt = id, sortOrder = id.toInt())

    private fun snapshot(
        tasks: List<Task>,
        completions: Set<DayKey> = emptySet(),
        skips: Set<DayKey> = emptySet(),
        overdue: List<Task> = emptyList(),
        subtasks: Map<Long, List<Subtask>> = emptyMap(),
        checks: Set<DayKey> = emptySet(),
    ) = AgendaSnapshot(tasks, mapOf(sport.id to sport), subtasks, completions, skips, checks, overdue)

    @Test
    fun `splits tasks into timed, untimed and done`() {
        val gym = task(1, "Спортзал", time = LocalTime.of(19, 0), categoryId = 1)
        val chicken = task(2, "Купить курицу")
        val call = task(3, "Позвонить", time = LocalTime.of(9, 0))
        val agenda = AgendaBuilder.build(
            snapshot(listOf(gym, chicken, call), completions = setOf(DayKey(3, today.toEpochDay()))),
            today,
            today,
        )
        assertEquals(listOf("Спортзал"), agenda.timed.map { it.title })
        assertEquals(listOf("Купить курицу"), agenda.untimed.map { it.title })
        assertEquals(listOf("Позвонить"), agenda.done.map { it.title })
        assertEquals(sport, agenda.timed.single().category)
        assertEquals(3, agenda.total)
        assertEquals(1, agenda.doneCount)
    }

    @Test
    fun `timed tasks are sorted by time`() {
        val agenda = AgendaBuilder.build(
            snapshot(listOf(task(1, "B", time = LocalTime.of(18, 0)), task(2, "A", time = LocalTime.of(8, 30)))),
            today,
            today,
        )
        assertEquals(listOf("A", "B"), agenda.timed.map { it.title })
    }

    @Test
    fun `skipped recurring occurrence is hidden`() {
        val daily = task(1, "Зарядка", date = today.minusDays(10), recurrence = Recurrence(RecurrenceType.DAILY))
        val agenda = AgendaBuilder.build(snapshot(listOf(daily), skips = setOf(DayKey(1, today.toEpochDay()))), today, today)
        assertTrue(agenda.isEmpty)
        val tomorrow = AgendaBuilder.build(snapshot(listOf(daily)), today.plusDays(1), today)
        assertEquals(1, tomorrow.untimed.size)
    }

    @Test
    fun `overdue tasks appear only on today`() {
        val old = task(5, "Старое", date = today.minusDays(2))
        val s = snapshot(emptyList(), overdue = listOf(old))
        val onToday = AgendaBuilder.build(s, today, today)
        assertEquals(listOf("Старое"), onToday.overdue.map { it.title })
        assertEquals(today.minusDays(2), onToday.overdue.single().day)
        assertTrue(AgendaBuilder.build(s, today.plusDays(1), today).overdue.isEmpty())
    }

    @Test
    fun `subtask checks are per day`() {
        val daily = task(1, "Уборка", date = today.minusDays(1), recurrence = Recurrence(RecurrenceType.DAILY))
        val subtasks = mapOf(1L to listOf(Subtask(10, 1, "Пол", 0), Subtask(11, 1, "Пыль", 1)))
        val s = snapshot(listOf(daily), subtasks = subtasks, checks = setOf(DayKey(10, today.toEpochDay())))
        assertEquals(1, AgendaBuilder.build(s, today, today).untimed.single().subtasksDone)
        assertEquals(0, AgendaBuilder.build(s, today.plusDays(1), today).untimed.single().subtasksDone)
    }

    @Test
    fun `summary counts done tasks and collects colors`() {
        val gym = task(1, "Спортзал", categoryId = 1)
        val chicken = task(2, "Курица")
        val summary = AgendaBuilder.summary(snapshot(listOf(gym, chicken), completions = setOf(DayKey(2, today.toEpochDay()))), today)
        assertEquals(2, summary.total)
        assertEquals(1, summary.done)
        assertEquals(listOf(sport.colorArgb), summary.colors)
    }
}
