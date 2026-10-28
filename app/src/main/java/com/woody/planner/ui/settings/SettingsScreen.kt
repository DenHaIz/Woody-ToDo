package com.woody.planner.ui.settings

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.AppContainer
import com.woody.planner.R
import com.woody.planner.data.ThemeMode
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.components.LiquidSegmented
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.glass.FrostedCard
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, bottomInset: Dp, onRequestNotifications: () -> Unit) {
    val colors = WoodyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)

    // Статусы разрешений перечитываем при каждом возвращении на экран.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val notificationsAllowed = remember(lifecycleState) { Notifications.canPost(context) }
    val exactAllowed = remember(lifecycleState) { container.reminders.canScheduleExact }
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = with(density) { headerHeight.toDp() } + 12.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("Оформление") {
                LiquidSegmented(
                    count = ThemeMode.entries.size,
                    selectedIndex = theme.ordinal,
                    onSelect = { index -> scope.launch { container.settings.setThemeMode(ThemeMode.entries[index]) } },
                    pillColor = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(Shapes.Pill)
                        .background(colors.chip),
                ) { index, selected ->
                    Text(
                        listOf("Системная", "Светлая", "Тёмная")[index],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.background else colors.textPrimary,
                    )
                }
            }

            SettingsCard("Напоминания") {
                PermissionRow(
                    title = "Уведомления",
                    status = if (notificationsAllowed) "Разрешены" else "Выключены — напоминания не будут видны",
                    ok = notificationsAllowed,
                    action = "Разрешить",
                    onAction = onRequestNotifications,
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    title = "Точное время",
                    status = if (exactAllowed) "Напоминания приходят минута в минуту" else "Без разрешения возможна задержка в несколько минут",
                    ok = exactAllowed,
                    action = "Открыть",
                    onAction = {
                        if (resumed) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                            )
                        }
                    },
                )
            }

            SettingsCard("Батарея") {
                Text(
                    "Woody ничего не делает в фоне: нет служб, синхронизации и периодических задач. " +
                        "Для каждого напоминания ставится один системный будильник — телефон разбудит приложение " +
                        "только в нужную минуту, а анимации работают лишь пока экран открыт.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            FrostedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.woody_mark), contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Woody", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                        Text("Ежедневник · версия $version", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { headerHeight = it.height }
                .fillMaxWidth()
                .liquidGlass(Shapes.Panel)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    FrostedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = WoodyTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun PermissionRow(title: String, status: String, ok: Boolean, action: String, onAction: () -> Unit) {
    val colors = WoodyTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (ok) colors.success else colors.warning, Shapes.Pill),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        if (!ok) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .bouncyClickable(onClick = onAction)
                    .clip(Shapes.Pill)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
