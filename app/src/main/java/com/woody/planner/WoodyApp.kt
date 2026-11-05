package com.woody.planner

import android.app.Application
import android.content.Context
import com.woody.planner.data.AppLanguage
import com.woody.planner.data.SettingsRepository
import com.woody.planner.data.ThemeMode
import com.woody.planner.data.TaskRepository
import com.woody.planner.data.WoodyDatabase
import com.woody.planner.reminders.ReminderScheduler
import com.woody.planner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class WoodyApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Процесс запустился (напоминание, кнопка уведомления, система) — если виджет показывает вчерашний
        // день, перерисовываем. Это одна проверка; работа — только когда день действительно сменился.
        WidgetUpdater.refreshIfStale(this)
    }
}

/** Ручной DI: все зависимости создаются лениво, при первом обращении. */
class AppContainer(context: Context) {
    val database: WoodyDatabase by lazy { WoodyDatabase.create(context) }
    val reminders: ReminderScheduler by lazy { ReminderScheduler(context, { database.taskDao() }, { settings }) }
    val repository: TaskRepository by lazy { TaskRepository(database, reminders) { WidgetUpdater.refresh(context) } }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    /** Для записи, которая должна доделаться, даже если экран закрыли (сохранение задачи, перепланирование). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Язык, тема и цвета — горячие потоки на весь процесс: при повороте или смене темы экран получает их сразу,
     * без пустого кадра, пока настройки читаются заново. null — ещё не прочитаны (первый запуск процесса).
     */
    val language: StateFlow<AppLanguage?> by lazy { settings.language.stateIn(scope, SharingStarted.Eagerly, null) }
    val themeMode: StateFlow<ThemeMode?> by lazy { settings.themeMode.stateIn(scope, SharingStarted.Eagerly, null) }
    val dynamicColors: StateFlow<Boolean?> by lazy { settings.dynamicColors.stateIn(scope, SharingStarted.Eagerly, null) }

    private val _today = MutableStateFlow(LocalDate.now())
    /** «Сегодня» обновляется при возвращении в приложение — без таймеров в фоне. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refreshToday() {
        _today.value = LocalDate.now()
    }
}
