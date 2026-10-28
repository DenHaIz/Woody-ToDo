package com.woody.planner.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderTrigger(val occurrence: LocalDate, val at: LocalDateTime)

object ReminderMath {

    /** Время напоминания для задач без конкретного времени. */
    val DefaultTime: LocalTime = LocalTime.of(9, 0)

    /**
     * Ближайшее будущее напоминание задачи.
     * [isHandled] — true, если вхождение на этот день уже выполнено или пропущено.
     */
    fun nextTrigger(task: Task, now: LocalDateTime, isHandled: (LocalDate) -> Boolean): ReminderTrigger? {
        val offset = task.reminderOffsetMin ?: return null
        val time = task.time ?: DefaultTime
        var day = RecurrenceRules.nextOccurrence(task.startDate, task.recurrence, now.toLocalDate())
        var guard = 0
        while (day != null && guard++ < 1000) {
            val at = day.atTime(time).minusMinutes(offset.toLong())
            if (at.isAfter(now) && !isHandled(day)) return ReminderTrigger(day, at)
            if (!task.recurrence.isRecurring) return null
            day = RecurrenceRules.nextOccurrence(task.startDate, task.recurrence, day.plusDays(1))
        }
        return null
    }
}
