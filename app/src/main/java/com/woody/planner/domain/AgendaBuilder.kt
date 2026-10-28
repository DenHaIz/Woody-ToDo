package com.woody.planner.domain

import java.time.LocalDate
import java.time.LocalTime

/** Всё, что нужно, чтобы собрать задачи на диапазон дней. Чистые данные, без Room. */
data class AgendaSnapshot(
    val tasks: List<Task>,
    val categories: Map<Long, Category>,
    val subtasks: Map<Long, List<Subtask>>,
    val completions: Set<DayKey>,
    val skips: Set<DayKey>,
    /** Отметки подзадач: subtaskId + день. */
    val subtaskChecks: Set<DayKey>,
    /** Невыполненные разовые задачи из прошлого (показываются только на «сегодня»). */
    val overdue: List<Task> = emptyList(),
)

object AgendaBuilder {

    fun build(snapshot: AgendaSnapshot, day: LocalDate, today: LocalDate): DayAgenda {
        val epochDay = day.toEpochDay()
        val items = snapshot.tasks
            .filter { RecurrenceRules.occursOn(it, day) && DayKey(it.id, epochDay) !in snapshot.skips }
            .map { it.toItem(snapshot, day, done = DayKey(it.id, epochDay) in snapshot.completions, overdue = false) }

        val overdue = if (day == today) {
            snapshot.overdue
                .filter { it.startDate < today }
                .sortedWith(compareBy<Task> { it.startDate }.thenBy(nullsLast()) { it.time }.thenBy { it.sortOrder })
                .map { it.toItem(snapshot, it.startDate, done = false, overdue = true) }
        } else {
            emptyList()
        }

        val sortOrder = snapshot.tasks.associate { it.id to (it.sortOrder to it.createdAt) }
        val (done, open) = items.partition { it.done }
        val (timed, untimed) = open.partition { it.time != null }
        return DayAgenda(
            day = day,
            overdue = overdue,
            timed = timed.sortedWith(compareBy<TaskItem> { it.time }.thenBy { it.title }),
            untimed = untimed.sortedWith(
                compareBy<TaskItem> { sortOrder[it.taskId]?.first ?: 0 }.thenBy { sortOrder[it.taskId]?.second ?: 0L },
            ),
            done = done.sortedWith(compareBy<TaskItem, LocalTime?>(nullsLast()) { it.time }.thenBy { it.title }),
        )
    }

    fun summary(snapshot: AgendaSnapshot, day: LocalDate): DaySummary {
        val epochDay = day.toEpochDay()
        val tasks = snapshot.tasks.filter {
            RecurrenceRules.occursOn(it, day) && DayKey(it.id, epochDay) !in snapshot.skips
        }
        return DaySummary(
            day = day,
            total = tasks.size,
            done = tasks.count { DayKey(it.id, epochDay) in snapshot.completions },
            colors = tasks.mapNotNull { t -> t.categoryId?.let { snapshot.categories[it]?.colorArgb } }.distinct().take(3),
        )
    }

    private fun Task.toItem(snapshot: AgendaSnapshot, day: LocalDate, done: Boolean, overdue: Boolean): TaskItem {
        val epochDay = day.toEpochDay()
        return TaskItem(
            taskId = id,
            day = day,
            title = title,
            note = note,
            category = categoryId?.let { snapshot.categories[it] },
            time = time,
            hasReminder = reminderOffsetMin != null,
            isRecurring = recurrence.isRecurring,
            done = done,
            overdue = overdue,
            subtasks = snapshot.subtasks[id].orEmpty()
                .sortedBy { it.sortOrder }
                .map { SubtaskItem(it.id, it.title, DayKey(it.id, epochDay) in snapshot.subtaskChecks) },
        )
    }
}
