package com.woody.planner.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.woody.planner.AppContainer
import com.woody.planner.domain.DayAgenda
import com.woody.planner.domain.DaySummary
import com.woody.planner.domain.SubtaskItem
import com.woody.planner.domain.TaskItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Событие для плашки «Отменить». */
data class UndoEvent(val id: Long, val message: String, val undo: suspend () -> Unit)

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val today: StateFlow<LocalDate> = container.today

    private val _selected = MutableStateFlow(container.today.value)
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    private val _undo = MutableStateFlow<UndoEvent?>(null)
    val undo: StateFlow<UndoEvent?> = _undo.asStateFlow()

    /** Цвета категорий выбранного дня — для фонового градиента. */
    val ambientColors: StateFlow<List<Int>> = combine(_selected, today) { day, t -> day to t }
        .flatMapLatest { (day, t) -> repository.observeDay(day, t) }
        .map { agenda ->
            (agenda.overdue + agenda.timed + agenda.untimed + agenda.done)
                .mapNotNull { it.category?.colorArgb }
                .distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(day: LocalDate) {
        _selected.value = day
    }

    fun agenda(day: LocalDate): Flow<DayAgenda> = today.flatMapLatest { repository.observeDay(day, it) }

    fun summaries(from: LocalDate, days: Int): Flow<List<DaySummary>> = repository.observeRange(from, days)

    fun toggleDone(item: TaskItem) = viewModelScope.launch {
        repository.setDone(item.taskId, item.day, !item.done)
    }

    fun toggleSubtask(item: TaskItem, subtask: SubtaskItem) = viewModelScope.launch {
        repository.setSubtaskDone(subtask.id, item.day, !subtask.done)
    }

    /** Свайп влево: разовую задачу — на завтра (просроченную — на сегодня), повторяющуюся — пропустить. */
    fun postpone(item: TaskItem) = viewModelScope.launch {
        if (item.isRecurring) {
            repository.setSkipped(item.taskId, item.day, skipped = true)
            offerUndo("Пропущено: ${item.title}") { repository.setSkipped(item.taskId, item.day, skipped = false) }
        } else {
            val target = if (item.overdue) today.value else item.day.plusDays(1)
            repository.moveTo(item.taskId, target)
            val message = if (item.overdue) "Перенесено на сегодня" else "Перенесено на завтра"
            offerUndo(message) { repository.moveTo(item.taskId, item.day) }
        }
    }

    fun delete(taskId: Long) = viewModelScope.launch {
        val deleted = repository.delete(taskId) ?: return@launch
        offerUndo("Удалено: ${deleted.title}") { repository.restore(deleted) }
    }

    fun performUndo() {
        val event = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { event.undo() }
    }

    fun dismissUndo(id: Long) {
        if (_undo.value?.id == id) _undo.value = null
    }

    private fun offerUndo(message: String, undo: suspend () -> Unit) {
        _undo.value = UndoEvent(System.nanoTime(), message, undo)
    }
}
