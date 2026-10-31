package com.woody.planner.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.woody.planner.AppContainer
import com.woody.planner.data.SwipeStyle
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.categories.CategoriesViewModel
import com.woody.planner.ui.components.AmbientBackground
import com.woody.planner.ui.components.LocalSwipeStyle
import com.woody.planner.ui.components.WoodyFab
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.day.DayScreen
import com.woody.planner.ui.day.DayViewModel
import com.woody.planner.ui.editor.EditorViewModel
import com.woody.planner.ui.editor.TaskEditorSheet
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.i18n.LocalLanguage
import com.woody.planner.ui.settings.SettingsPage
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Короткое сообщение внизу экрана с необязательным действием. */
private data class Message(val id: Long, val text: String, val action: String?, val onAction: () -> Unit)

/**
 * Один экран — день; одна кнопка — «+». Настройки и категории живут внутри кнопки (удержание),
 * а всё место отдано задачам.
 */
@Composable
fun WoodyAppShell(
    container: AppContainer,
    openDayRequest: LocalDate?,
    onOpenDayHandled: () -> Unit,
    /** «+» на виджете — открыть новую задачу. */
    newTaskRequest: Boolean = false,
    onNewTaskHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    val dayVm: DayViewModel = viewModel { DayViewModel(container) }
    val editorVm: EditorViewModel = viewModel { EditorViewModel(container) }
    val categoriesVm: CategoriesViewModel = viewModel { CategoriesViewModel(container) }

    val draft by editorVm.draft.collectAsStateWithLifecycle()
    val editorSession by editorVm.session.collectAsStateWithLifecycle()
    val categories by editorVm.categories.collectAsStateWithLifecycle()
    val selectedDay by dayVm.selected.collectAsStateWithLifecycle()
    val today by dayVm.today.collectAsStateWithLifecycle()
    val ambient by dayVm.ambientColors.collectAsStateWithLifecycle()
    val undo by dayVm.undo.collectAsStateWithLifecycle()
    val swipeStyle by container.settings.swipeStyle.collectAsStateWithLifecycle(SwipeStyle.Classic)
    val hintSeen by container.settings.menuHintSeen.collectAsStateWithLifecycle(true)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    var hint by remember { mutableStateOf<Message?>(null) }
    val lang = LocalLanguage.current
    var showFabHint by remember { mutableStateOf(false) }

    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            hint = Message(System.nanoTime(), lang.pick("Уведомления выключены", "Notifications are off"), lang.pick("Настройки", "Settings"), openNotificationSettings)
        }
    }
    val requestNotifications = {
        if (Notifications.canRequestPermission) notificationLauncher.launch(Notifications.PERMISSION)
        else openNotificationSettings()
    }
    val ensureReminderPermissions = {
        if (!Notifications.canPost(context)) {
            requestNotifications()
        } else if (!container.reminders.canScheduleExact) {
            hint = Message(System.nanoTime(), lang.pick("Разрешите точное время", "Allow exact timing"), lang.pick("Открыть", "Open")) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                )
            }
        }
    }

    LaunchedEffect(openDayRequest) {
        if (openDayRequest != null) {
            settingsOpen = false
            dayVm.select(openDayRequest)
            onOpenDayHandled()
        }
    }
    // «+» на виджете — сразу новая задача на сегодня.
    LaunchedEffect(newTaskRequest) {
        if (newTaskRequest) {
            settingsOpen = false
            editorVm.openNew(today)
            onNewTaskHandled()
        }
    }
    // Подсказка про удержание «+» — через пару секунд после первого запуска, пока меню ни разу не открывали.
    LaunchedEffect(hintSeen) {
        if (!hintSeen) {
            delay(1_500)
            showFabHint = true
            delay(7_000)
            showFabHint = false
        } else {
            showFabHint = false
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    CompositionLocalProvider(LocalHazeState provides hazeState, LocalSwipeStyle provides swipeStyle) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackground(
                colors = ambient.map { Color(it) },
                seed = selectedDay.toEpochDay(),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = 0f),
            )

            DayScreen(
                dayVm,
                onEditTask = { editorVm.openEdit(it) },
                bottomInset = bottomInset,
                onAddTask = { editorVm.openNew(selectedDay) },
            )

            val undoMessage = undo?.let { Message(it.id, it.message(lang), lang.pick("Отменить", "Undo")) { dayVm.performUndo() } }
            MessageBar(
                message = hint ?: undoMessage,
                onTimeout = { id ->
                    if (hint?.id == id) hint = null
                    dayVm.dismissUndo(id)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 100.dp, bottom = 27.dp),
            )

            WoodyFab(
                onAdd = { editorVm.openNew(selectedDay) },
                onOpenSettings = {
                    showFabHint = false
                    if (!hintSeen) scope.launch { container.settings.setMenuHintSeen() }
                    settingsOpen = true
                },
                showHint = showFabHint,
                onButtonBounds = { fabBounds = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding(),
            )

            TaskEditorSheet(
                draft = draft,
                session = editorSession,
                categories = categories,
                today = today,
                onChange = editorVm::update,
                onDismiss = editorVm::close,
                onSave = editorVm::save,
                onDelete = { id ->
                    editorVm.close()
                    dayVm.delete(id)
                },
                onReminderEnabled = ensureReminderPermissions,
            )

            SettingsPage(
                visible = settingsOpen,
                origin = fabBounds,
                container = container,
                categoriesVm = categoriesVm,
                onClose = { settingsOpen = false },
                onRequestNotifications = {
                    if (Notifications.canPost(context)) openNotificationSettings() else requestNotifications()
                },
            )
        }
    }
}

@Composable
private fun MessageBar(message: Message?, onTimeout: (Long) -> Unit, modifier: Modifier) {
    val colors = WoodyTheme.colors
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(4_500)
        onTimeout(id)
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically(Motion.bouncy()) { it } + fadeIn() + scaleIn(Motion.bouncy(), initialScale = 0.9f),
        exit = slideOutVertically(Motion.soft()) { it / 2 } + fadeOut(),
    ) {
        val shown = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(Shapes.Pill, tint = colors.textPrimary.copy(alpha = 0.78f))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shown.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.background,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (shown.action != null) {
                Text(
                    shown.action,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (colors.isDark) colors.accent else Color(0xFFFFB703),
                    maxLines = 1,
                    modifier = Modifier
                        .bouncyClickable {
                            shown.onAction()
                            onTimeout(shown.id)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
