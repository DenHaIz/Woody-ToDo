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

    /** Открытый черновик; null — редактор закрыт. */
    private val _draft = MutableStateFlow<TaskDraft?>(null)
    val draft: StateFlow<TaskDraft?> = _draft.asStateFlow()

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canScheduleExact: Boolean get() = container.reminders.canScheduleExact

    fun openNew(day: LocalDate) {
        _draft.value = TaskDraft(date = day)
    }

    fun openEdit(taskId: Long) = viewModelScope.launch {
        repository.loadDraft(taskId)?.let { _draft.value = it }
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
