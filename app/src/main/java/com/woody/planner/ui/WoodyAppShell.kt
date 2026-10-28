package com.woody.planner.ui

import androidx.core.net.toUri
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.woody.planner.AppContainer
import com.woody.planner.domain.Category
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.categories.CategoriesScreen
import com.woody.planner.ui.categories.CategoriesViewModel
import com.woody.planner.ui.categories.CategoryEditorSheet
import com.woody.planner.ui.categories.CategoryEmoji
import com.woody.planner.ui.categories.CategoryPalette
import com.woody.planner.ui.components.AmbientBackground
import com.woody.planner.ui.components.LiquidSegmented
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.day.DayScreen
import com.woody.planner.ui.day.DayViewModel
import com.woody.planner.ui.editor.EditorViewModel
import com.woody.planner.ui.editor.TaskEditorSheet
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.settings.SettingsScreen
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.time.LocalDate

private enum class Tab(val label: String) { Day("День"), Categories("Категории"), Settings("Настройки") }

/** Короткое сообщение внизу экрана с необязательным действием. */
private data class Message(val id: Long, val text: String, val action: String?, val onAction: () -> Unit)

@Composable
fun WoodyAppShell(container: AppContainer, openDayRequest: LocalDate?, onOpenDayHandled: () -> Unit) {
    val colors = WoodyTheme.colors
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    var tab by rememberSaveable { mutableStateOf(Tab.Day) }

    val dayVm: DayViewModel = viewModel { DayViewModel(container) }
    val editorVm: EditorViewModel = viewModel { EditorViewModel(container) }
    val categoriesVm: CategoriesViewModel = viewModel { CategoriesViewModel(container) }

    val draft by editorVm.draft.collectAsStateWithLifecycle()
    val categories by editorVm.categories.collectAsStateWithLifecycle()
    val selectedDay by dayVm.selected.collectAsStateWithLifecycle()
    val today by dayVm.today.collectAsStateWithLifecycle()
    val ambient by dayVm.ambientColors.collectAsStateWithLifecycle()
    val undo by dayVm.undo.collectAsStateWithLifecycle()
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var hint by remember { mutableStateOf<Message?>(null) }

    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            hint = Message(System.nanoTime(), "Уведомления выключены", "Настройки", openNotificationSettings)
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
            hint = Message(System.nanoTime(), "Разрешите точное время напоминаний", "Открыть") {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                )
            }
        }
    }

    LaunchedEffect(openDayRequest) {
        if (openDayRequest != null) {
            tab = Tab.Day
            dayVm.select(openDayRequest)
            onOpenDayHandled()
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackground(
                colors = ambient.map { Color(it) },
                seed = selectedDay.toEpochDay(),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = 0f),
            )

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(Motion.soft()) + scaleIn(Motion.soft(), initialScale = 0.96f)) togetherWith
                        (fadeOut(Motion.snappy()) + scaleOut(Motion.soft(), targetScale = 1.02f))
                },
                label = "tabs",
            ) { current ->
                when (current) {
                    Tab.Day -> DayScreen(dayVm, onEditTask = { editorVm.openEdit(it) }, bottomInset = bottomInset)
                    Tab.Categories -> CategoriesScreen(categoriesVm, bottomInset) { category ->
                        editingCategory = category ?: Category(0, "", CategoryPalette.first(), CategoryEmoji.first(), 0)
                    }
                    Tab.Settings -> SettingsScreen(container, bottomInset) {
                        if (Notifications.canPost(context)) openNotificationSettings() else requestNotifications()
                    }
                }
            }

            BottomControls(
                tab = tab,
                onTab = { tab = it },
                onAdd = {
                    when (tab) {
                        Tab.Day -> editorVm.openNew(selectedDay)
                        else -> editingCategory = Category(0, "", CategoryPalette.first(), CategoryEmoji.first(), 0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            val undoMessage = undo?.let { Message(it.id, it.message, "Отменить") { dayVm.performUndo() } }
            MessageBar(
                message = hint ?: undoMessage,
                onTimeout = { id ->
                    if (hint?.id == id) hint = null
                    dayVm.dismissUndo(id)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset),
            )

            TaskEditorSheet(
                draft = draft,
                categories = categories,
                today = today,
                onDismiss = editorVm::close,
                onSave = editorVm::save,
                onDelete = { id ->
                    editorVm.close()
                    dayVm.delete(id)
                },
                onReminderEnabled = ensureReminderPermissions,
            )

            CategoryEditorSheet(
                category = editingCategory,
                onDismiss = { editingCategory = null },
                onSave = {
                    categoriesVm.save(it)
                    editingCategory = null
                },
                onDelete = {
                    categoriesVm.delete(it)
                    editingCategory = null
                },
            )
        }
    }
}

@Composable
private fun BottomControls(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    val colors = WoodyTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(64.dp)
                .liquidGlass(Shapes.Pill),
        ) {
            LiquidSegmented(
                count = Tab.entries.size,
                selectedIndex = tab.ordinal,
                onSelect = { onTab(Tab.entries[it]) },
                pillColor = colors.textPrimary.copy(alpha = if (colors.isDark) 0.16f else 0.09f),
                inset = 6.dp,
                modifier = Modifier.fillMaxSize(),
            ) { index, selected ->
                val entry = Tab.entries[index]
                val tint = if (selected) colors.accent else colors.textSecondary
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (entry) {
                            Tab.Day -> WoodyIcons.Calendar
                            Tab.Categories -> WoodyIcons.Grid
                            Tab.Settings -> WoodyIcons.Sliders
                        },
                        contentDescription = entry.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(entry.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
        AnimatedVisibility(
            visible = tab != Tab.Settings,
            enter = scaleIn(Motion.bouncy()) + fadeIn(),
            exit = scaleOut(Motion.snappy()) + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(12.dp))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(64.dp)
                        .liquidGlass(Shapes.Pill, tint = colors.accent.copy(alpha = 0.82f), interactionSource = interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onAdd()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(WoodyIcons.Plus, contentDescription = "Добавить", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
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
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .liquidGlass(Shapes.Pill, tint = colors.textPrimary.copy(alpha = 0.78f))
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                shown.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.background,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (shown.action != null) {
                Text(
                    shown.action,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (colors.isDark) colors.accent else Color(0xFFFFB703),
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
