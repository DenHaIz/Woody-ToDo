package com.woody.planner

import android.app.Application
import android.content.Context
import com.woody.planner.data.SettingsRepository
import com.woody.planner.data.TaskRepository
import com.woody.planner.data.WoodyDatabase
import com.woody.planner.reminders.ReminderScheduler
import com.woody.planner.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class WoodyApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Ручной DI: все зависимости создаются лениво, при первом обращении. */
class AppContainer(context: Context) {
    val database: WoodyDatabase by lazy { WoodyDatabase.create(context) }
    val reminders: ReminderScheduler by lazy { ReminderScheduler(context, { database.taskDao() }, { settings }) }
    val repository: TaskRepository by lazy { TaskRepository(database, reminders) { WidgetUpdater.refresh(context) } }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    private val _today = MutableStateFlow(LocalDate.now())
    /** «Сегодня» обновляется при возвращении в приложение — без таймеров в фоне. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refreshToday() {
        _today.value = LocalDate.now()
    }
}
