package com.woody.planner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.woody.planner.domain.Category
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.RecurrenceType
import com.woody.planner.domain.Subtask
import com.woody.planner.domain.Task
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val emoji: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "tasks",
    indices = [Index("categoryId"), Index("startDate")],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val categoryId: Long?,
    /** LocalDate.toEpochDay() */
    val startDate: Long,
    /** Минуты от полуночи. */
    val timeMinutes: Int?,
    val reminderOffsetMin: Int?,
    val recurrenceType: String,
    val recurrenceInterval: Int,
    val recurrenceDaysMask: Int,
    val recurrenceEndDate: Long?,
    val createdAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "completions",
    primaryKeys = ["taskId", "epochDay"],
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class CompletionEntity(val taskId: Long, val epochDay: Long)

/** Пропущенное вхождение повторяющейся задачи. */
@Entity(
    tableName = "skips",
    primaryKeys = ["taskId", "epochDay"],
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class SkipEntity(val taskId: Long, val epochDay: Long)

@Entity(
    tableName = "subtasks",
    indices = [Index("taskId")],
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "subtask_checks",
    primaryKeys = ["subtaskId", "epochDay"],
    foreignKeys = [ForeignKey(SubtaskEntity::class, ["id"], ["subtaskId"], onDelete = ForeignKey.CASCADE)],
)
data class SubtaskCheckEntity(val subtaskId: Long, val epochDay: Long)

fun CategoryEntity.toDomain() = Category(id, name, colorArgb, emoji, sortOrder)
fun Category.toEntity() = CategoryEntity(id, name, colorArgb, emoji, sortOrder)

fun SubtaskEntity.toDomain() = Subtask(id, taskId, title, sortOrder)

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    note = note,
    categoryId = categoryId,
    startDate = LocalDate.ofEpochDay(startDate),
    time = timeMinutes?.let { LocalTime.of(it / 60, it % 60) },
    reminderOffsetMin = reminderOffsetMin,
    recurrence = Recurrence(
        type = runCatching { RecurrenceType.valueOf(recurrenceType) }.getOrDefault(RecurrenceType.NONE),
        interval = recurrenceInterval,
        daysMask = recurrenceDaysMask,
        endDate = recurrenceEndDate?.let(LocalDate::ofEpochDay),
    ),
    createdAt = createdAt,
    sortOrder = sortOrder,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    note = note,
    categoryId = categoryId,
    startDate = startDate.toEpochDay(),
    timeMinutes = time?.let { it.hour * 60 + it.minute },
    reminderOffsetMin = reminderOffsetMin,
    recurrenceType = recurrence.type.name,
    recurrenceInterval = recurrence.interval,
    recurrenceDaysMask = recurrence.daysMask,
    recurrenceEndDate = recurrence.endDate?.toEpochDay(),
    createdAt = createdAt,
    sortOrder = sortOrder,
)
