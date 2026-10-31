package com.woody.planner.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.woody.planner.AppContainer
import com.woody.planner.data.TaskDraft
import com.woody.planner.domain.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class EditorViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    /**
     * Открытый черновик со всеми правками; null — редактор закрыт. Правки хранятся здесь, а не в UI:
     * введённый текст не теряется, даже если экран пересоздан системой.
     */
    private val _draft = MutableStateFlow<TaskDraft?>(null)
    val draft: StateFlow<TaskDraft?> = _draft.asStateFlow()

    /** Номер открытия редактора: по нему UI понимает, что открыт новый черновик, а не правка текущего. */
    private val _session = MutableStateFlow(0L)
    val session: StateFlow<Long> = _session.asStateFlow()

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canScheduleExact: Boolean get() = container.reminders.canScheduleExact

    fun openNew(day: LocalDate) {
        // Повторное нажатие «+» при открытом редакторе не стирает набранное.
        if (_draft.value != null) return
        _session.value++
        _draft.value = TaskDraft(date = day)
    }

    fun openEdit(taskId: Long) = viewModelScope.launch {
        if (_draft.value != null) return@launch
        repository.loadDraft(taskId)?.let {
            _session.value++
            _draft.value = it
        }
    }

    fun update(draft: TaskDraft) {
        if (_draft.value != null) _draft.value = draft
    }

    fun close() {
        _draft.value = null
    }

    fun save(draft: TaskDraft) {
        if (draft.title.isBlank()) return
        close()
        viewModelScope.launch { repository.save(draft) }
    }
}
