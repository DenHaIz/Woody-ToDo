package com.woody.planner.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

@Immutable
data class Category(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val emoji: String,
    val sortOrder: Int,
)

@Immutable
data class Task(
    val id: Long,
    val title: String,
    val note: String,
    val categoryId: Long?,
    val startDate: LocalDate,
    val time: LocalTime?,
    /** Минуты до начала задачи, когда нужно напомнить. null — без напоминания. */
    val reminderOffsetMin: Int?,
    val recurrence: Recurrence,
    val createdAt: Long,
    val sortOrder: Int,
)

@Immutable
data class Subtask(
    val id: Long,
    val taskId: Long,
    val title: String,
    val sortOrder: Int,
)

/** Одна задача на конкретный день — то, что видит пользователь в списке. */
@Immutable
data class TaskItem(
    val taskId: Long,
    /** День вхождения задачи: для просроченных — исходная дата. */
    val day: LocalDate,
    val title: String,
    val note: String,
    val category: Category?,
    val time: LocalTime?,
    val hasReminder: Boolean,
    val isRecurring: Boolean,
    val done: Boolean,
    val overdue: Boolean,
    val subtasks: List<SubtaskItem>,
) {
    val subtasksDone: Int get() = subtasks.count { it.done }
}

@Immutable
data class SubtaskItem(
    val id: Long,
    val title: String,
    val done: Boolean,
)

@Immutable
data class DayAgenda(
    val day: LocalDate,
    val overdue: List<TaskItem>,
    val timed: List<TaskItem>,
    val untimed: List<TaskItem>,
    val done: List<TaskItem>,
) {
    val total: Int get() = timed.size + untimed.size + done.size
    val doneCount: Int get() = done.size
    val isEmpty: Boolean get() = total == 0 && overdue.isEmpty()

    companion object {
        fun empty(day: LocalDate) = DayAgenda(day, emptyList(), emptyList(), emptyList(), emptyList())
    }
}

@Immutable
data class DaySummary(
    val day: LocalDate,
    val total: Int,
    val done: Int,
    /** Цвета категорий задач дня (до трёх) — для точек под датой. */
    val colors: List<Int>,
)

/** Пара (задача или подзадача, день): отметки выполнения и пропуски. */
data class DayKey(val id: Long, val epochDay: Long)
