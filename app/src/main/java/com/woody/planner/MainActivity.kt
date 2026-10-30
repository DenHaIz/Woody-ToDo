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
import androidx.lifecycle.lifecycleScope
import com.woody.planner.data.ThemeMode
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.WoodyAppShell
import com.woody.planner.ui.i18n.LocalLanguage
import com.woody.planner.ui.theme.WoodyTheme
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as WoodyApp).container

    /** День, который нужно открыть (из уведомления). */
    private val openDayRequest = mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        lifecycleScope.launch(Dispatchers.IO) {
            // Канал создаём сразу — он виден в системных настройках ещё до первого напоминания.
            Notifications.ensureChannel(this@MainActivity, container.settings.language.first())
            // Принудительная остановка (или «очистка» на некоторых прошивках) стирает будильники —
            // при каждом запуске ставим их заново. Это один запрос к базе.
            container.reminders.rescheduleAll()
        }
        setContent {
            val language by container.settings.language.collectAsStateWithLifecycle(null)
            val savedMode by container.settings.themeMode.collectAsStateWithLifecycle(null)
            // Пока настройки читаются (миллисекунды), показываем фон окна — без мелькания чужого языка и темы.
            val mode = savedMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            CompositionLocalProvider(LocalLanguage provides (language ?: return@setContent)) {
                WoodyTheme(darkTheme = dark) {
                    WoodyAppShell(
                        container = container,
                        openDayRequest = openDayRequest.value,
                        onOpenDayHandled = { openDayRequest.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.refreshToday()
    }

    private fun handleIntent(intent: Intent?) {
        val epochDay = intent?.getLongExtra(Notifications.EXTRA_OPEN_DAY, Long.MIN_VALUE) ?: return
        if (epochDay != Long.MIN_VALUE) openDayRequest.value = LocalDate.ofEpochDay(epochDay)
    }
}
