package com.woody.planner.ui.settings

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.AppContainer
import com.woody.planner.R
import com.woody.planner.data.AppLanguage
import com.woody.planner.data.SwipeStyle
import com.woody.planner.data.ThemeMode
import com.woody.planner.domain.Category
import com.woody.planner.reminders.Notifications
import com.woody.planner.reminders.OemPower
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
import com.woody.planner.ui.i18n.LocalLanguage
import com.woody.planner.ui.i18n.tr
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
 * Экран настроек. Вырастает из кнопки «+» (её границы — [origin]) и сворачивается обратно в неё;
 * закрывается стрелкой или жестом «назад» с предпросмотром.
 */
@Composable
fun SettingsPage(
    visible: Boolean,
    origin: Rect,
    container: AppContainer,
    categoriesVm: CategoriesViewModel,
    onClose: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val colors = WoodyTheme.colors
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            if (visible) 1f else 0f,
            // Равномерное движение: мягко трогается и мягко встаёт, без затяжного торможения во второй половине.
            if (visible) tween(RevealMs, easing = EvenEasing) else tween(CollapseMs, easing = EvenEasing),
        )
    }
    val isVisible by rememberUpdatedState(visible)
    val shown by remember { derivedStateOf { isVisible || progress.value > 0.001f } }
    if (!shown) return

    // Своё «стекло»: панели настроек преломляют только содержимое настроек, а не день под ними.
    val hazeState = rememberHazeState()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    val accent = colors.accent

    PredictiveBackHandler(enabled = visible && editingCategory == null) { events ->
        try {
            events.collect { backProgress = it.progress }
            onClose()
            backProgress = 0f
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    /** Границы кнопки «+» внутри страницы; если ещё не измерены — правый нижний угол. */
    fun Density.source(size: Size): Rect = if (origin.isEmpty) {
        val fab = 66.dp.toPx()
        val inset = 20.dp.toPx()
        Rect(size.width - inset - fab, size.height - inset * 3 - fab, size.width - inset, size.height - inset * 3)
    } else {
        origin
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val from = source(size)
                    clip = true
                    shape = RevealShape(from, p, extraCorner = backProgress * 32.dp.toPx())
                    val s = 1f - backProgress * 0.08f
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(from.center.x / size.width, from.center.y / size.height)
                }
                .drawWithContent {
                    drawContent()
                    // В начале раскрытия (и в конце сворачивания) страница в цвете кнопки — «+» перетекает в экран.
                    val tint = (1f - progress.value / 0.5f).coerceIn(0f, 1f)
                    if (tint > 0f) drawRect(accent.copy(alpha = tint))
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        val from = source(size)
                        val s = 0.9f + 0.1f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(from.center.x / size.width, from.center.y / size.height)
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
                    onClose = onClose,
                    onEditCategory = { editingCategory = it },
                    onRequestNotifications = onRequestNotifications,
                )
            }
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

/** Почти линейная кривая: скорость в середине лишь на треть выше, чем у краёв. */
private val EvenEasing = CubicBezierEasing(0.3f, 0.15f, 0.7f, 0.85f)
private const val RevealMs = 360
private const val CollapseMs = 300

/** Скруглённый прямоугольник, растущий из [from] (кнопка) до размеров экрана по [progress]. */
private class RevealShape(private val from: Rect, private val progress: Float, private val extraCorner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun lerp(a: Float, b: Float) = a + (b - a) * progress
        val rect = Rect(lerp(from.left, 0f), lerp(from.top, 0f), lerp(from.right, size.width), lerp(from.bottom, size.height))
        val radius = lerp(from.height / 2f, 0f) + extraCorner
        return Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun SettingsContent(
    container: AppContainer,
    categoriesVm: CategoriesViewModel,
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
    val counts by categoriesVm.counts.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Статусы разрешений перечитываем при каждом возвращении на экран.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val notificationsAllowed = remember(lifecycleState) { Notifications.canPost(context) }
    val exactAllowed = remember(lifecycleState) { container.reminders.canScheduleExact }
    val batteryUnrestricted = remember(lifecycleState) { OemPower.isBatteryUnrestricted(context) }
    val autostart = remember(lifecycleState) { OemPower.autostartAllowed(context) }
    val alarmClock by container.settings.alarmClockReminders.collectAsStateWithLifecycle(true)
    val lang = LocalLanguage.current
    // Обратный отсчёт пробного напоминания; 0 — не отправлено.
    var testLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(testLeft > 0) {
        while (testLeft > 0) {
            delay(1_000)
            testLeft--
        }
    }
    val ordered by categoriesVm.recentFirst.collectAsStateWithLifecycle()
    var categoriesExpanded by rememberSaveable { mutableStateOf(false) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            SettingsCard(
                title = tr("Категории", "Categories"),
                onHeaderClick = if (ordered.size > RecentCount) ({ categoriesExpanded = !categoriesExpanded }) else null,
            ) {
                // Свёрнуто — три последних изменённых; по нажатию раскрывается весь список.
                Column(Modifier.animateContentSize(Motion.soft())) {
                    val shownCategories = if (categoriesExpanded) ordered else ordered.take(RecentCount)
                    shownCategories.forEach { category ->
                        CategoryRow(category, counts[category.id] ?: 0) { onEditCategory(category) }
                    }
                    if (ordered.size > RecentCount) {
                        ExpandRow(categoriesExpanded, ordered.size) { categoriesExpanded = !categoriesExpanded }
                    }
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
                    Text(tr("Новая категория", "New category"), style = MaterialTheme.typography.labelLarge, color = colors.accent, maxLines = 1)
                }
            }

            SettingsCard(tr("Оформление", "Appearance")) {
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
                        listOf(tr("Системная", "System"), tr("Светлая", "Light"), tr("Тёмная", "Dark"))[index],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.background else colors.textPrimary,
                    )
                }
            }

            SettingsCard(tr("Язык", "Language")) {
                LiquidSegmented(
                    count = AppLanguage.entries.size,
                    selectedIndex = lang.ordinal,
                    onSelect = { index ->
                        val language = AppLanguage.entries[index]
                        scope.launch {
                            container.settings.setLanguage(language)
                            container.repository.translateDefaultCategories(language)
                        }
                    },
                    pillColor = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(Shapes.Pill)
                        .background(colors.chip),
                ) { index, selected ->
                    FitText(
                        AppLanguage.entries[index].title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.background else colors.textPrimary,
                    )
                }
            }

            SettingsCard(tr("Анимация свайпа", "Swipe animation")) {
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
                        SwipeStyle.entries[index].title(lang),
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
                        style.description(lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                    )
                }
                SwipeDemo(swipeStyle)
            }

            SettingsCard(tr("Напоминания", "Reminders")) {
                PermissionRow(
                    title = tr("Уведомления", "Notifications"),
                    status = if (notificationsAllowed) tr("Разрешены, со стуком дятла", "Allowed, with a woodpecker knock") else tr("Выключены", "Turned off"),
                    ok = notificationsAllowed,
                    action = tr("Разрешить", "Allow"),
                    onAction = onRequestNotifications,
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    title = tr("Точное время", "Exact time"),
                    status = if (exactAllowed) tr("Минута в минуту", "To the minute") else tr("Возможна задержка", "May be late"),
                    ok = exactAllowed,
                    action = tr("Открыть", "Open"),
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    title = tr("Как будильник", "As an alarm"),
                    status = if (alarmClock) {
                        tr("Надёжнее всего · значок будильника в строке состояния", "Most reliable · alarm icon in the status bar")
                    } else {
                        tr("Обычный точный будильник, без значка", "Regular exact alarm, no icon")
                    },
                    checked = alarmClock,
                    onChange = { enabled ->
                        scope.launch {
                            container.settings.setAlarmClockReminders(enabled)
                            container.reminders.rescheduleAll()
                        }
                    },
                )
                if (OemPower.needsAutostart) {
                    // Xiaomi, Huawei, Oppo, Vivo: без автозапуска закрытое приложение не получает будильники.
                    Spacer(Modifier.height(12.dp))
                    PermissionRow(
                        title = tr("Автозапуск", "Autostart"),
                        status = when (autostart) {
                            true -> tr("Включён", "Enabled")
                            false -> tr("Выключен — напоминания не придут", "Off — reminders won't arrive")
                            null -> tr("Включите для Woody в настройках телефона", "Turn it on for Woody in phone settings")
                        },
                        ok = autostart == true,
                        optional = autostart == null,
                        action = tr("Открыть", "Open"),
                        onAction = { OemPower.openAutostart(context) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    title = tr("Без ограничений", "Unrestricted"),
                    status = when {
                        batteryUnrestricted -> tr("Экономия заряда не мешает напоминаниям", "Battery saver won't block reminders")
                        OemPower.vendor == OemPower.Vendor.Xiaomi -> tr("Выберите «Нет ограничений»", "Choose \"No restrictions\"")
                        OemPower.needsAutostart -> tr("Снимите ограничения батареи", "Remove battery restrictions")
                        else -> tr("Если напоминания опаздывают", "If reminders come late")
                    },
                    ok = batteryUnrestricted,
                    // На «агрессивных» прошивках без этого напоминания закрытого приложения не приходят.
                    optional = !OemPower.needsAutostart,
                    action = tr("Открыть", "Open"),
                    onAction = { OemPower.openBatterySettings(context) },
                )
                Spacer(Modifier.height(14.dp))
                TestReminderButton(
                    secondsLeft = testLeft,
                    onClick = {
                        if (!notificationsAllowed) {
                            onRequestNotifications()
                        } else {
                            scope.launch { container.reminders.scheduleTest(TestDelayMs) }
                            testLeft = (TestDelayMs / 1000).toInt()
                        }
                    },
                )
                AnimatedVisibility(
                    visible = testLeft > 0,
                    enter = expandVertically(Motion.soft()) + fadeIn(Motion.snappy()),
                    exit = shrinkVertically(Motion.soft()) + fadeOut(Motion.snappy()),
                ) {
                    Text(
                        tr(
                            "Смахните Woody из недавних приложений — так проверится, придёт ли напоминание при закрытом приложении.",
                            "Swipe Woody away from recent apps to check that reminders arrive while the app is closed.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }

            SettingsCard(tr("Батарея", "Battery")) {
                Text(
                    tr(
                        "Woody ничего не делает в фоне: нет служб, синхронизации и периодических проверок. " +
                            "На каждое напоминание ставится один системный будильник — телефон просыпается " +
                            "на долю секунды только в нужную минуту. Так напоминания приходят вовремя, " +
                            "а расход заряда остаётся незаметным.",
                        "Woody does nothing in the background: no services, sync or periodic checks. " +
                            "Each reminder sets a single system alarm — the phone wakes for a split second " +
                            "only at the right minute. Reminders arrive on time and the battery use is negligible.",
                    ),
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
                        Text(tr("Ежедневник · версия ", "Daily planner · version ") + version, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
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
                contentDescription = tr("Назад", "Back"),
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bouncyClickable(onClick = onClose)
                    .padding(12.dp),
            )
            FitText(tr("Настройки", "Settings"), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        }
    }
}

/** Живая карточка для пробы выбранной анимации. */
@Composable
private fun SwipeDemo(style: SwipeStyle) {
    val colors = WoodyTheme.colors
    SwipeableTaskCard(
        startAction = SwipeAction(WoodyIcons.Check, tr("Готово", "Done"), colors.success, dismiss = false) {},
        endAction = SwipeAction(WoodyIcons.ArrowRight, tr("На завтра", "Tomorrow"), colors.warning, dismiss = false) {},
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
                    tr("Свайпните меня", "Swipe me"),
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
            Text(tasksWord(count, LocalLanguage.current), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1)
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
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    FrostedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = WoodyTheme.colors.textSecondary,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onHeaderClick != null) Modifier.bouncyClickable(pressedScale = 0.98f, onClick = onHeaderClick) else Modifier)
                    .padding(bottom = 12.dp),
            )
            content()
        }
    }
}

/** Сколько недавно изменённых категорий видно в свёрнутом списке. */
private const val RecentCount = 3

/** Через сколько придёт пробное напоминание: хватает, чтобы закрыть приложение. */
private const val TestDelayMs = 30_000L

@Composable
private fun ExpandRow(expanded: Boolean, total: Int, onClick: () -> Unit) {
    val colors = WoodyTheme.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.bouncy(), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) tr("Свернуть", "Collapse") else tr("Все категории", "All categories") + " · $total",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 52.dp)
                .weight(1f),
        )
        Icon(
            WoodyIcons.ChevronDown,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** Пробное напоминание: проверяет разрешения, канал и звук, не дожидаясь настоящей задачи. */
@Composable
private fun TestReminderButton(secondsLeft: Int, onClick: () -> Unit) {
    val sent = secondsLeft > 0
    val colors = WoodyTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .bouncyClickable(enabled = !sent, glowColor = colors.accent, glowShape = Shapes.Pill, onClick = onClick)
            .clip(Shapes.Pill)
            .background(colors.accent.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(WoodyIcons.Bell, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        AnimatedContent(sent, transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) }, label = "test") { done ->
            FitText(
                if (done) tr("Придёт через $secondsLeft с", "Arrives in $secondsLeft s") else tr("Проверить напоминание", "Test a reminder"),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

/** Строка с переключателем: капля-бегунок перетекает пружиной. */
@Composable
private fun ToggleRow(title: String, status: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = WoodyTheme.colors
    val track by animateColorAsState(if (checked) colors.success else colors.chip, Motion.snappy(), label = "track")
    val knob by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncy(), label = "knob")
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f) { onChange(!checked) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (checked) colors.success else colors.textTertiary, Shapes.Pill),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            FitText(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 2)
        }
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(width = 52.dp, height = 32.dp)
                .clip(Shapes.Pill)
                .background(track)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset { IntOffset((20.dp.toPx() * knob).roundToInt(), 0) }
                    .size(26.dp)
                    .graphicsLayer {
                        // Бегунок слегка растягивается в движении — как капля.
                        val stretch = 1f + 0.25f * (1f - kotlin.math.abs(knob * 2f - 1f))
                        scaleX = stretch
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    status: String,
    ok: Boolean,
    action: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    val colors = WoodyTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    when {
                        ok -> colors.success
                        optional -> colors.textTertiary
                        else -> colors.warning
                    },
                    Shapes.Pill,
                ),
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
