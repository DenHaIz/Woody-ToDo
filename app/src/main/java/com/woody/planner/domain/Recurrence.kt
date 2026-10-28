package com.woody.planner.domain

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RecurrenceType { NONE, DAILY, WEEKLY, EVERY_N_DAYS, MONTHLY }

/**
 * Правило повторения задачи.
 * [daysMask] — для WEEKLY: бит (dayOfWeek.value - 1), т.е. пн = бит 0 … вс = бит 6.
 * [interval] — для EVERY_N_DAYS: шаг в днях.
 */
@Immutable
data class Recurrence(
    val type: RecurrenceType = RecurrenceType.NONE,
    val interval: Int = 1,
    val daysMask: Int = 0,
    val endDate: LocalDate? = null,
) {
    val isRecurring: Boolean get() = type != RecurrenceType.NONE

    companion object {
        val None = Recurrence()
        fun maskOf(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or d.bit }
    }
}

val DayOfWeek.bit: Int get() = 1 shl (value - 1)

object RecurrenceRules {

    fun occursOn(start: LocalDate, rule: Recurrence, day: LocalDate): Boolean {
        if (day < start) return false
        if (rule.endDate != null && day > rule.endDate) return false
        return when (rule.type) {
            RecurrenceType.NONE -> day == start
            RecurrenceType.DAILY -> true
            RecurrenceType.EVERY_N_DAYS -> {
                val step = rule.interval.coerceAtLeast(1)
                ChronoUnit.DAYS.between(start, day) % step == 0L
            }
            RecurrenceType.WEEKLY -> {
                val mask = if (rule.daysMask == 0) start.dayOfWeek.bit else rule.daysMask
                mask and day.dayOfWeek.bit != 0
            }
            RecurrenceType.MONTHLY -> day.dayOfMonth == start.dayOfMonth.coerceAtMost(day.lengthOfMonth())
        }
    }

    fun occursOn(task: Task, day: LocalDate) = occursOn(task.startDate, task.recurrence, day)

    /** Ближайшая дата вхождения начиная с [from] включительно, либо null, если повторений больше нет. */
    fun nextOccurrence(start: LocalDate, rule: Recurrence, from: LocalDate): LocalDate? {
        if (rule.type == RecurrenceType.NONE) return start.takeIf { it >= from }
        var day = if (from < start) start else from
        // Ежемесячное правило может «перескакивать» короткие месяцы — сканируем с запасом.
        repeat(MAX_SCAN_DAYS) {
            if (rule.endDate != null && day > rule.endDate) return null
            if (occursOn(start, rule, day)) return day
            day = day.plusDays(1)
        }
        return null
    }

    private const val MAX_SCAN_DAYS = 366 * 2
}
