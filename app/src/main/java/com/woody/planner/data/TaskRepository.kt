package com.woody.planner.data

import androidx.room.withTransaction
import com.woody.planner.domain.AgendaBuilder
import com.woody.planner.domain.AgendaSnapshot
import com.woody.planner.domain.Category
import com.woody.planner.domain.DayAgenda
import com.woody.planner.domain.DayKey
import com.woody.planner.domain.DaySummary
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.Task
import com.woody.planner.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

/** Черновик задачи для редактора. */
data class TaskDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val date: LocalDate,
    val time: LocalTime? = null,
    val reminderOffsetMin: Int? = null,
    val recurrence: Recurrence = Recurrence.None,
    val subtasks: List<DraftSubtask> = emptyList(),
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
) {
    val isNew: Boolean get() = id == 0L
}

/** [key] — стабильный ключ для анимаций списка, [id] = 0 для новых пунктов. */
data class DraftSubtask(val key: Long, val id: Long, val title: String)

/** Всё, что нужно, чтобы вернуть удалённую задачу кнопкой «Отменить». */
class DeletedTask internal constructor(
    internal val task: TaskEntity,
    internal val subtasks: List<SubtaskEntity>,
    internal val completions: List<CompletionEntity>,
    internal val skips: List<SkipEntity>,
    internal val checks: List<SubtaskCheckEntity>,
) {
    val title: String get() = task.title
}

class TaskRepository(
    private val db: WoodyDatabase,
    private val reminders: ReminderScheduler,
    /** Задачи изменились — например, перерисовать виджет на рабочем столе. */
    private val onChanged: () -> Unit = {},
) {
    private val tasks = db.taskDao()
    private val categoryDao = db.categoryDao()

    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    val categoryTaskCounts: Flow<Map<Long, Int>> =
        categoryDao.observeTaskCounts().map { list -> list.associate { it.categoryId to it.count } }

    fun observeDay(day: LocalDate, today: LocalDate): Flow<DayAgenda> =
        observeSnapshot(day, day, withOverdueFor = today.takeIf { it == day })
            .map { AgendaBuilder.build(it, day, today) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeRange(from: LocalDate, days: Int): Flow<List<DaySummary>> =
        observeSnapshot(from, from.plusDays(days - 1L), withOverdueFor = null)
            .map { snapshot -> List(days) { AgendaBuilder.summary(snapshot, from.plusDays(it.toLong())) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    private fun observeSnapshot(from: LocalDate, to: LocalDate, withOverdueFor: LocalDate?): Flow<AgendaSnapshot> {
        val f = from.toEpochDay()
        val t = to.toEpochDay()
        val base = combine(tasks.observeAll(), categoryDao.observeAll(), tasks.observeSubtasks()) { tasks, cats, subs ->
            Triple(
                tasks.map { it.toDomain() },
                cats.associate { it.id to it.toDomain() },
                subs.groupBy({ it.taskId }, { it.toDomain() }),
            )
        }
        val overdue: Flow<List<Task>> = withOverdueFor
            ?.let { day -> tasks.observeOverdue(day.toEpochDay()).map { list -> list.map { it.toDomain() } } }
            ?: flowOf(emptyList())
        return combine(
            base,
            tasks.observeCompletions(f, t),
            tasks.observeSkips(f, t),
            tasks.observeSubtaskChecks(f, t),
            overdue,
        ) { (taskList, cats, subs), completions, skips, checks, overdueList ->
            AgendaSnapshot(
                tasks = taskList,
                categories = cats,
                subtasks = subs,
                completions = completions.mapTo(HashSet()) { DayKey(it.taskId, it.epochDay) },
                skips = skips.mapTo(HashSet()) { DayKey(it.taskId, it.epochDay) },
                subtaskChecks = checks.mapTo(HashSet()) { DayKey(it.subtaskId, it.epochDay) },
                overdue = overdueList,
            )
        }
    }

    // --- Действия с задачами ---

    suspend fun setDone(taskId: Long, day: LocalDate, done: Boolean) {
        val entity = CompletionEntity(taskId, day.toEpochDay())
        if (done) tasks.insertCompletion(entity) else tasks.deleteCompletion(entity)
        // Выполнили в приложении — висящее напоминание о задаче больше не нужно.
        if (done) reminders.dismissNotification(taskId)
        reminders.schedule(taskId)
        onChanged()
    }

    suspend fun setSubtaskDone(subtaskId: Long, day: LocalDate, done: Boolean) {
        val entity = SubtaskCheckEntity(subtaskId, day.toEpochDay())
        if (done) tasks.insertSubtaskCheck(entity) else tasks.deleteSubtaskCheck(entity)
    }

    /** Перенос разовой задачи на другой день. */
    suspend fun moveTo(taskId: Long, day: LocalDate) {
        tasks.moveTo(taskId, day.toEpochDay())
        reminders.schedule(taskId)
        onChanged()
    }

    /**
     * Новый порядок задач дня (перетаскиванием). Задачам раздаются их же прежние значения порядка
     * в новой последовательности — так не сдвигается порядок относительно задач других дней.
     */
    suspend fun reorder(ids: List<Long>) {
        db.withTransaction {
            val orders = ids.mapNotNull { tasks.get(it)?.sortOrder }.sorted()
            if (orders.size != ids.size) return@withTransaction
            var previous = Int.MIN_VALUE
            ids.forEachIndexed { index, id ->
                // Одинаковые значения разводим, иначе новый порядок не сохранится.
                val order = maxOf(orders[index], previous + 1)
                previous = order
                tasks.setSortOrder(id, order)
            }
        }
        onChanged()
    }

    /** Пропуск одного вхождения повторяющейся задачи. */
    suspend fun setSkipped(taskId: Long, day: LocalDate, skipped: Boolean) {
        val entity = SkipEntity(taskId, day.toEpochDay())
        if (skipped) tasks.insertSkip(entity) else tasks.deleteSkip(entity)
        reminders.schedule(taskId)
        onChanged()
    }

    suspend fun delete(taskId: Long): DeletedTask? {
        val task = tasks.get(taskId) ?: return null
        val snapshot = DeletedTask(
            task = task,
            subtasks = tasks.subtasksOf(taskId),
            completions = tasks.completionsOf(taskId),
            skips = tasks.skipsOf(taskId),
            checks = tasks.subtaskChecksOf(taskId),
        )
        tasks.delete(taskId)
        reminders.cancel(taskId)
        onChanged()
        return snapshot
    }

    suspend fun restore(deleted: DeletedTask) {
        db.withTransaction {
            tasks.insert(deleted.task)
            tasks.insertSubtasks(deleted.subtasks)
            tasks.insertCompletions(deleted.completions)
            tasks.insertSkips(deleted.skips)
            tasks.insertSubtaskChecks(deleted.checks)
        }
        reminders.schedule(deleted.task.id)
        onChanged()
    }

    suspend fun loadDraft(taskId: Long): TaskDraft? {
        val task = tasks.get(taskId)?.toDomain() ?: return null
        return TaskDraft(
            id = task.id,
            title = task.title,
            note = task.note,
            categoryId = task.categoryId,
            date = task.startDate,
            time = task.time,
            reminderOffsetMin = task.reminderOffsetMin,
            recurrence = task.recurrence,
            subtasks = tasks.subtasksOf(taskId).map { DraftSubtask(key = it.id, id = it.id, title = it.title) },
            createdAt = task.createdAt,
            sortOrder = task.sortOrder,
        )
    }

    suspend fun save(draft: TaskDraft): Long {
        val isNew = draft.isNew
        val task = Task(
            id = draft.id,
            title = draft.title.trim(),
            note = draft.note.trim(),
            categoryId = draft.categoryId,
            startDate = draft.date,
            time = draft.time,
            reminderOffsetMin = draft.reminderOffsetMin,
            recurrence = draft.recurrence,
            createdAt = if (isNew) System.currentTimeMillis() else draft.createdAt,
            sortOrder = if (isNew) tasks.maxSortOrder() + 1 else draft.sortOrder,
        )
        val subtasks = draft.subtasks
            .filter { it.title.isNotBlank() }
            .mapIndexed { index, s -> SubtaskEntity(id = s.id, taskId = draft.id, title = s.title.trim(), sortOrder = index) }
        val id = tasks.save(task.toEntity(), subtasks)
        reminders.schedule(id)
        onChanged()
        return id
    }

    // --- Категории ---

    /** Сохраняет категорию и возвращает её id (для новой — выданный базой). */
    suspend fun saveCategory(category: Category): Long {
        val order = if (category.id == 0L) categoryDao.maxSortOrder() + 1 else category.sortOrder
        val rowId = categoryDao.upsert(category.copy(sortOrder = order).toEntity())
        return if (category.id == 0L) rowId else category.id
    }

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    /** Стартовые категории, которые пользователь не переименовал, переводятся вместе с интерфейсом. */
    suspend fun translateDefaultCategories(lang: AppLanguage) {
        DefaultCategoryNames.forEach { (ru, en) ->
            if (lang == AppLanguage.English) categoryDao.rename(ru, en) else categoryDao.rename(en, ru)
        }
    }

    private companion object {
        val DefaultCategoryNames = listOf(
            "Спорт" to "Sport",
            "Покупки" to "Shopping",
            "Дом" to "Home",
            "Работа" to "Work",
            "Здоровье" to "Health",
        )
    }
}
