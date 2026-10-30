package com.woody.planner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.data.ThemeMode
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.WoodyAppShell
import com.woody.planner.ui.theme.WoodyTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val container get() = (application as WoodyApp).container

    /** День, который нужно открыть (из уведомления). */
    private val openDayRequest = mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // Канал создаём сразу — он виден в системных настройках ещё до первого напоминания.
        Notifications.ensureChannel(this)
        setContent {
            val mode by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            WoodyTheme(darkTheme = dark) {
                WoodyAppShell(
                    container = container,
                    openDayRequest = openDayRequest.value,
                    onOpenDayHandled = { openDayRequest.value = null },
                )
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
