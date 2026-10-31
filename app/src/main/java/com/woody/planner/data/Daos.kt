package com.woody.planner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS count FROM tasks WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun observeTaskCounts(): Flow<List<CategoryCount>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE categories SET name = :to WHERE name = :from")
    suspend fun rename(from: String, to: String)
}

data class CategoryCount(val categoryId: Long, val count: Int)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("UPDATE tasks SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** Задачи, которым могут понадобиться будильники: с напоминанием или со временем. */
    @Query("SELECT * FROM tasks WHERE reminderOffsetMin IS NOT NULL OR timeMinutes IS NOT NULL")
    suspend fun withAlarms(): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks t
        WHERE t.recurrenceType = 'NONE' AND t.startDate < :today
          AND NOT EXISTS (SELECT 1 FROM completions c WHERE c.taskId = t.id AND c.epochDay = t.startDate)
        """,
    )
    fun observeOverdue(today: Long): Flow<List<TaskEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET startDate = :epochDay WHERE id = :id")
    suspend fun moveTo(id: Long, epochDay: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Подзадачи ---

    @Query("SELECT * FROM subtasks ORDER BY sortOrder")
    fun observeSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder")
    suspend fun subtasksOf(taskId: Long): List<SubtaskEntity>

    @Upsert
    suspend fun upsertSubtasks(subtasks: List<SubtaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId AND id NOT IN (:keep)")
    suspend fun deleteSubtasksExcept(taskId: Long, keep: List<Long>)

    @Transaction
    suspend fun save(task: TaskEntity, subtasks: List<SubtaskEntity>): Long {
        val id = if (task.id == 0L) insert(task) else task.id.also { upsert(task) }
        val bound = subtasks.map { it.copy(taskId = id) }
        deleteSubtasksExcept(id, bound.map { it.id }.filter { it != 0L })
        upsertSubtasks(bound)
        return id
    }

    // --- Выполнение и пропуски ---

    @Query("SELECT * FROM completions WHERE epochDay BETWEEN :from AND :to")
    fun observeCompletions(from: Long, to: Long): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE taskId = :taskId")
    suspend fun completionsOf(taskId: Long): List<CompletionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(completions: List<CompletionEntity>)

    @Delete
    suspend fun deleteCompletion(completion: CompletionEntity)

    @Query("SELECT * FROM skips WHERE epochDay BETWEEN :from AND :to")
    fun observeSkips(from: Long, to: Long): Flow<List<SkipEntity>>

    @Query("SELECT * FROM skips WHERE taskId = :taskId")
    suspend fun skipsOf(taskId: Long): List<SkipEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkip(skip: SkipEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkips(skips: List<SkipEntity>)

    @Delete
    suspend fun deleteSkip(skip: SkipEntity)

    @Query("SELECT * FROM subtask_checks WHERE epochDay BETWEEN :from AND :to")
    fun observeSubtaskChecks(from: Long, to: Long): Flow<List<SubtaskCheckEntity>>

    @Query("SELECT sc.* FROM subtask_checks sc JOIN subtasks s ON s.id = sc.subtaskId WHERE s.taskId = :taskId")
    suspend fun subtaskChecksOf(taskId: Long): List<SubtaskCheckEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubtaskCheck(check: SubtaskCheckEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubtaskChecks(checks: List<SubtaskCheckEntity>)

    @Delete
    suspend fun deleteSubtaskCheck(check: SubtaskCheckEntity)
}
