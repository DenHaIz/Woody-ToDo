package com.woody.planner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.data.ThemeMode
import com.woody.planner.reminders.Notifications
import com.woody.planner.reminders.PinnedService
import com.woody.planner.ui.WoodyAppShell
import com.woody.planner.ui.i18n.LocalLanguage
import com.woody.planner.ui.theme.WoodyTheme
import java.time.LocalDate
import com.woody.planner.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NEW_TASK = "new_task"
    }

    private val container get() = (application as WoodyApp).container

    /** День, который нужно открыть (из уведомления или виджета). */
    private val openDayRequest = mutableStateOf<LocalDate?>(null)

    /** «+» на виджете: сразу открыть новую задачу. */
    private val newTaskRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // После поворота или смены темы активность пересоздаётся с тем же интентом — не открываем
        // редактор и день из уведомления второй раз.
        if (savedInstanceState == null) {
            handleIntent(intent)
            // После первого кадра, чтобы не отнимать время у запуска; при повороте и смене темы — не повторяем.
            window.decorView.post { container.scope.launch(Dispatchers.IO) { runCatching { startupChores() } } }
        }
        setContent {
            val language by container.language.collectAsStateWithLifecycle()
            val savedMode by container.themeMode.collectAsStateWithLifecycle()
            val dynamicColors by container.dynamicColors.collectAsStateWithLifecycle()
            // Пока настройки читаются (миллисекунды), показываем фон окна — без мелькания чужого языка и темы.
            val mode = savedMode ?: return@setContent
            val dynamic = dynamicColors ?: return@setContent
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            CompositionLocalProvider(LocalLanguage provides (language ?: return@setContent)) {
                WoodyTheme(darkTheme = dark, dynamic = dynamic) {
                    WoodyAppShell(
                        container = container,
                        openDayRequest = openDayRequest.value,
                        onOpenDayHandled = { openDayRequest.value = null },
                        newTaskRequest = newTaskRequest.value,
                        onNewTaskHandled = { newTaskRequest.value = false },
                    )
                }
            }
        }
    }

    private suspend fun startupChores() {
        val app = applicationContext
        // Канал создаём сразу — он виден в системных настройках ещё до первого напоминания.
        Notifications.ensureChannel(app, container.settings.language.first())
        // Принудительная остановка (или «очистка» на некоторых прошивках) стирает будильники —
        // при каждом запуске ставим их заново (и показываем те, что прошивка не доставила).
        container.reminders.rescheduleAll()
        // Виджет на рабочем столе — свежий при каждом запуске (в том числе сразу после обновления).
        WidgetUpdater.updateNow(app)
        PinnedService.ensure(app)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.refreshToday()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NEW_TASK, false) == true) newTaskRequest.value = true
        val epochDay = intent?.getLongExtra(Notifications.EXTRA_OPEN_DAY, Long.MIN_VALUE) ?: return
        if (epochDay != Long.MIN_VALUE) openDayRequest.value = LocalDate.ofEpochDay(epochDay)
    }
}
