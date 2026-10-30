package com.woody.planner.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.AppContainer
import com.woody.planner.R
import com.woody.planner.data.SwipeStyle
import com.woody.planner.data.ThemeMode
import com.woody.planner.domain.Category
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.categories.CategoriesViewModel
import com.woody.planner.ui.categories.CategoryEditorSheet
import com.woody.planner.ui.categories.CategoryEmoji
import com.woody.planner.ui.categories.CategoryPalette
import com.woody.planner.ui.categories.tasksWord
import com.woody.planner.ui.components.AmbientBackground
import com.woody.planner.ui.components.FitText
import com.woody.planner.ui.components.LiquidSegmented
import com.woody.planner.ui.components.SwipeAction
import com.woody.planner.ui.components.SwipeableTaskCard
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.glass.FrostedCard
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран настроек: выезжает справа поверх дня, закрывается стрелкой или жестом «назад»
 * (с предпросмотром — экран тянется за пальцем). [focusCategories] — сразу прокрутить к категориям.
 */
@Composable
fun SettingsPage(
    visible: Boolean,
    focusCategories: Boolean,
    container: AppContainer,
    categoriesVm: CategoriesViewModel,
    onClose: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(Motion.soft()) { it } + fadeIn(Motion.snappy()),
        exit = slideOutHorizontally(Motion.soft()) { it } + fadeOut(Motion.snappy()),
    ) {
        // Своё «стекло»: панели настроек преломляют только содержимое настроек, а не день под ними.
        val hazeState = rememberHazeState()
        var backProgress by remember { mutableFloatStateOf(0f) }
        var editingCategory by remember { mutableStateOf<Category?>(null) }

        PredictiveBackHandler(enabled = visible && editingCategory == null) { events ->
            try {
                events.collect { backProgress = it.progress }
                onClose()
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
        }

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = backProgress * size.width * 0.18f
                        val s = 1f - backProgress * 0.08f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        shape = Shapes.Panel
                        clip = backProgress > 0f
                    },
            ) {
                AmbientBackground(
                    colors = emptyList(),
                    seed = 11,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState, zIndex = 0f),
                )
                SettingsContent(
                    container = container,
                    categoriesVm = categoriesVm,
                    focusCategories = focusCategories,
                    onClose = onClose,
                    onEditCategory = { editingCategory = it },
                    onRequestNotifications = onRequestNotifications,
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
}

@Composable
private fun SettingsContent(
    container: AppContainer,
    categoriesVm: CategoriesViewModel,
    focusCategories: Boolean,
    onClose: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val colors = WoodyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
    val swipeStyle by container.settings.swipeStyle.collectAsStateWithLifecycle(SwipeStyle.Classic)
    val categories by categoriesVm.categories.collectAsStateWithLifecycle()
    val counts by categoriesVm.counts.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var categoriesTop by remember { mutableIntStateOf(0) }

    // Статусы разрешений перечитываем при каждом возвращении на экран.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val notificationsAllowed = remember(lifecycleState) { Notifications.canPost(context) }
    val exactAllowed = remember(lifecycleState) { container.reminders.canScheduleExact }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(focusCategories) {
        if (focusCategories) {
            delay(350)
            // Раздел «Категории» — прямо под шапку.
            val gap = with(density) { 8.dp.roundToPx() }
            scrollState.animateScrollTo((categoriesTop - headerHeight - gap).coerceAtLeast(0), Motion.soft())
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp)
                .padding(top = with(density) { headerHeight.toDp() } + 4.dp, bottom = bottomInset + 24.dp),
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
                    FitText(
                        listOf("Системная", "Светлая", "Тёмная")[index],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.background else colors.textPrimary,
                    )
                }
            }

            SettingsCard("Анимация свайпа") {
                LiquidSegmented(
                    count = SwipeStyle.entries.size,
                    selectedIndex = swipeStyle.ordinal,
                    onSelect = { index -> scope.launch { container.settings.setSwipeStyle(SwipeStyle.entries[index]) } },
                    pillColor = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(Shapes.Pill)
                        .background(colors.chip),
                ) { index, selected ->
                    FitText(
                        SwipeStyle.entries[index].title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else colors.textPrimary,
                    )
                }
                AnimatedContent(
                    targetState = swipeStyle,
                    transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) },
                    label = "swipeDescription",
                ) { style ->
                    Text(
                        style.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, start = 4.dp),
                    )
                }
                SwipeDemo(swipeStyle)
            }

            SettingsCard("Категории", Modifier.onGloballyPositioned { categoriesTop = it.positionInParent().y.toInt() }) {
                categories.forEach { category ->
                    CategoryRow(category, counts[category.id] ?: 0) { onEditCategory(category) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .bouncyClickable(pressedScale = 0.97f) {
                            onEditCategory(Category(0, "", CategoryPalette.first(), CategoryEmoji.first(), 0))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(colors.accent.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(WoodyIcons.Plus, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("Новая категория", style = MaterialTheme.typography.labelLarge, color = colors.accent, maxLines = 1)
                }
            }

            SettingsCard("Напоминания") {
                PermissionRow(
                    title = "Уведомления",
                    status = if (notificationsAllowed) "Разрешены, со стуком дятла" else "Выключены",
                    ok = notificationsAllowed,
                    action = "Разрешить",
                    onAction = onRequestNotifications,
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    title = "Точное время",
                    status = if (exactAllowed) "Минута в минуту" else "Возможна задержка",
                    ok = exactAllowed,
                    action = "Открыть",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }

            SettingsCard("Батарея") {
                Text(
                    "Woody ничего не делает в фоне: нет служб, синхронизации и периодических задач. " +
                        "Для каждого напоминания ставится один системный будильник — телефон разбудит " +
                        "приложение только в нужную минуту.",
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

        // Шапка: размер меряем снаружи отступов, чтобы учесть статус-бар.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeight = it.height }
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .liquidGlass(Shapes.Panel)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                WoodyIcons.ChevronLeft,
                contentDescription = "Назад",
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bouncyClickable(onClick = onClose)
                    .padding(12.dp),
            )
            FitText("Настройки", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        }
    }
}

/** Живая карточка для пробы выбранной анимации. */
@Composable
private fun SwipeDemo(style: SwipeStyle) {
    val colors = WoodyTheme.colors
    SwipeableTaskCard(
        startAction = SwipeAction(WoodyIcons.Check, "Готово", colors.success, dismiss = false) {},
        endAction = SwipeAction(WoodyIcons.ArrowRight, "На завтра", colors.warning, dismiss = false) {},
        style = style,
    ) {
        FrostedCard(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            color = colors.card,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WoodyIcons.ChevronLeft, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                FitText(
                    "Свайпните меня",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Icon(WoodyIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, count: Int, onClick: () -> Unit) {
    val colors = WoodyTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color(category.colorArgb).copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(category.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            FitText(category.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(tasksWord(count), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1)
        }
        Box(
            Modifier
                .size(10.dp)
                .background(Color(category.colorArgb), CircleShape),
        )
        Icon(
            WoodyIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FrostedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = WoodyTheme.colors.textSecondary,
                maxLines = 1,
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
            FitText(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            FitText(status, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        if (!ok) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .bouncyClickable(onClick = onAction)
                    .clip(Shapes.Pill)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
