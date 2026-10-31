package com.woody.planner.ui.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.R
import com.woody.planner.data.AppLanguage
import com.woody.planner.ui.i18n.LocalLanguage
import com.woody.planner.ui.i18n.appLocale
import com.woody.planner.ui.i18n.dayMonth
import com.woody.planner.ui.i18n.tr
import com.woody.planner.domain.DayAgenda
import com.woody.planner.domain.TaskItem
import com.woody.planner.ui.components.FitText
import com.woody.planner.ui.components.MonthCalendar
import com.woody.planner.ui.components.ProgressRing
import com.woody.planner.ui.components.SwipeAction
import com.woody.planner.ui.components.SwipeableTaskCard
import com.woody.planner.ui.components.WeekStrip
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

private const val DAY_PAGES = 20_000
private const val DAY_START_PAGE = DAY_PAGES / 2

@Composable
fun DayScreen(
    vm: DayViewModel,
    onEditTask: (Long) -> Unit,
    bottomInset: Dp,
) {
    val today by vm.today.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }

    val baseDay = remember { selected }
    fun pageOf(day: LocalDate) = DAY_START_PAGE + ChronoUnit.DAYS.between(baseDay, day).toInt()
    val pager = rememberPagerState(initialPage = pageOf(selected)) { DAY_PAGES }

    // Программная прокрутка (выбор в ленте/календаре) не должна менять выбранный день на промежуточных страницах.
    var programmaticScroll by remember { mutableStateOf(false) }
    // Свайп страниц → выбранный день сразу, как страница перевалила за середину: шапка отзывается мгновенно.
    LaunchedEffect(pager) {
        // drop(1): стартовая страница уже соответствует выбранному дню и не должна перебивать переход из уведомления.
        snapshotFlow { pager.currentPage }.drop(1).collect {
            if (!programmaticScroll) vm.select(baseDay.plusDays((it - DAY_START_PAGE).toLong()))
        }
    }
    // Выбор дня в ленте/календаре → плавная прокрутка страниц.
    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (pager.currentPage != target && !pager.isScrollInProgress) {
            programmaticScroll = true
            try {
                pager.animateScrollToPage(target, animationSpec = Motion.soft())
            } finally {
                programmaticScroll = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            val day = baseDay.plusDays((page - DAY_START_PAGE).toLong())
            DayPage(
                day = day,
                vm = vm,
                topPadding = with(density) { headerHeight.toDp() },
                bottomPadding = bottomInset,
                onEditTask = onEditTask,
            )
        }

        DayHeader(
            vm = vm,
            selected = selected,
            today = today,
            // Размер меряем снаружи отступов: в высоту шапки входит и статус-бар.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeight = it.height }
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DayHeader(vm: DayViewModel, selected: LocalDate, today: LocalDate, modifier: Modifier) {
    val colors = WoodyTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val agenda by remember(selected, today) { vm.agenda(selected) }.collectAsStateWithLifecycle(null)
    val chevron = animateFloatAsState(if (expanded) 180f else 0f, Motion.bouncy(), label = "headerChevron")

    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(Shapes.Panel)
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    // Без «вдавливания»: панель целиком плавно раскрывает календарь.
                    .clickable(interactionSource = null, indication = null) {
                        if (!expanded) month = YearMonth.from(selected)
                        expanded = !expanded
                    },
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInVertically(Motion.soft()) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically(Motion.soft()) { if (forward) -it / 2 else it / 2 } + fadeOut()) using
                            SizeTransform(clip = false)
                    },
                    label = "title",
                ) { day ->
                    Column {
                        FitText(dayTitle(day, today, LocalLanguage.current), style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Рядом может стоять кнопка «Сегодня»: дата с днём недели ужимается шрифтом, а не обрезается.
                            FitText(
                                daySubtitle(day, today, LocalLanguage.current),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                minSize = 9.sp,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                WoodyIcons.ChevronDown,
                                contentDescription = if (expanded) tr("Свернуть календарь", "Collapse calendar") else tr("Открыть календарь", "Open calendar"),
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = chevron.value },
                            )
                            AnimatedVisibility(
                                visible = day != today,
                                enter = scaleIn(Motion.bouncy()) + fadeIn(),
                                exit = scaleOut(Motion.snappy()) + fadeOut(),
                            ) {
                                Text(
                                    tr("Сегодня", "Today"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .bouncyClickable { vm.select(today) }
                                        .clip(Shapes.Pill)
                                        .background(colors.accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            ProgressRing(done = agenda?.doneCount ?: 0, total = agenda?.total ?: 0)
        }
        Spacer(Modifier.height(10.dp))
        // Высота панели меняется пружиной от верхнего края, а уходящий вид обрезается по панели —
        // стекло плавно растёт и сжимается, без перекосов.
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) using
                    SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) }
            },
            contentAlignment = Alignment.TopCenter,
            label = "calendar",
        ) { showMonth ->
            if (showMonth) {
                MonthCalendar(
                    month = month,
                    selected = selected,
                    today = today,
                    summaries = { start ->
                        val data by remember(start) { vm.summaries(start, 42) }.collectAsStateWithLifecycle(emptyList())
                        data
                    },
                    onSelect = {
                        vm.select(it)
                        expanded = false
                    },
                    onMonthChange = { month = it },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else {
                WeekStrip(
                    selected = selected,
                    today = today,
                    summaries = { start ->
                        val data by remember(start) { vm.summaries(start, 7) }.collectAsStateWithLifecycle(emptyList())
                        data
                    },
                    onSelect = vm::select,
                )
            }
        }
    }
}

@Composable
private fun DayPage(
    day: LocalDate,
    vm: DayViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    onEditTask: (Long) -> Unit,
) {
    val agenda by remember(day) { vm.agenda(day) }.collectAsStateWithLifecycle(null)
    var doneExpanded by rememberSaveable(day) { mutableStateOf(true) }
    val current = agenda ?: return
    val warning = WoodyTheme.colors.warning
    // Мало задач — карточки крупнее, места на экране хватает.
    val large = current.total + current.overdue.size <= 3
    val lang = LocalLanguage.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topPadding + 8.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (current.isEmpty) {
            item(key = "empty") { EmptyDay(Modifier.animateItem()) }
        }
        taskSection("overdue", lang.pick("Просрочено", "Overdue"), current.overdue, vm, onEditTask, large, warning)
        taskSection("timed", lang.pick("По расписанию", "Scheduled"), current.timed, vm, onEditTask, large)
        taskSection("untimed", if (current.timed.isEmpty()) lang.pick("Задачи", "Tasks") else lang.pick("В течение дня", "Anytime today"), current.untimed, vm, onEditTask, large)
        if (current.done.isNotEmpty()) {
            item(key = "header-done") {
                DoneHeader(current, doneExpanded, { doneExpanded = !doneExpanded }, Modifier.animateItem())
            }
            if (doneExpanded) taskItems(current.done, vm, onEditTask, large)
        }
    }
}

private fun LazyListScope.taskSection(
    key: String,
    title: String,
    tasks: List<TaskItem>,
    vm: DayViewModel,
    onEditTask: (Long) -> Unit,
    large: Boolean,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$key") {
        SectionHeader(title, Modifier.animateItem(), accent)
    }
    taskItems(tasks, vm, onEditTask, large)
}

private fun LazyListScope.taskItems(tasks: List<TaskItem>, vm: DayViewModel, onEditTask: (Long) -> Unit, large: Boolean) {
    items(tasks, key = { "task-${it.taskId}-${it.day.toEpochDay()}" }, contentType = { "task" }) { item ->
        val colors = WoodyTheme.colors
        SwipeableTaskCard(
            modifier = Modifier.animateItem(
                fadeInSpec = Motion.snappy(),
                placementSpec = Motion.soft(),
                fadeOutSpec = Motion.snappy(),
            ),
            startAction = SwipeAction(
                icon = if (item.done) WoodyIcons.Undo else WoodyIcons.Check,
                label = if (item.done) tr("Вернуть", "Undo") else tr("Готово", "Done"),
                color = colors.success,
                dismiss = false,
                onTrigger = { vm.toggleDone(item) },
            ),
            endAction = when {
                item.done -> SwipeAction(WoodyIcons.Trash, tr("Удалить", "Delete"), colors.accent, dismiss = true) { vm.delete(item.taskId) }
                item.isRecurring -> SwipeAction(WoodyIcons.Skip, tr("Пропустить", "Skip"), colors.warning, dismiss = true) { vm.postpone(item) }
                item.overdue -> SwipeAction(WoodyIcons.ArrowRight, tr("На сегодня", "To today"), colors.warning, dismiss = true) { vm.postpone(item) }
                else -> SwipeAction(WoodyIcons.ArrowRight, tr("На завтра", "Tomorrow"), colors.warning, dismiss = true) { vm.postpone(item) }
            },
        ) {
            TaskCard(
                item = item,
                onToggle = { vm.toggleDone(item) },
                onToggleSubtask = { vm.toggleSubtask(item, it) },
                onEdit = { onEditTask(item.taskId) },
                large = large,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier, accent: androidx.compose.ui.graphics.Color? = null) {
    Text(
        title.uppercase(appLocale),
        style = MaterialTheme.typography.labelSmall,
        color = accent ?: WoodyTheme.colors.textSecondary,
        modifier = modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun DoneHeader(agenda: DayAgenda, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = WoodyTheme.colors
    val rotation = animateFloatAsState(if (expanded) 0f else -90f, Motion.bouncy(), label = "doneChevron")
    Row(
        modifier
            .padding(top = 8.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onToggle)
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("ВЫПОЛНЕНО", "DONE") + " · ${agenda.doneCount}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.success,
        )
        Icon(
            WoodyIcons.ChevronDown,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(14.dp)
                .graphicsLayer { rotationZ = rotation.value },
        )
    }
}

@Composable
private fun EmptyDay(modifier: Modifier) {
    val colors = WoodyTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear = animateFloatAsState(if (shown) 1f else 0f, Motion.bouncy(), label = "empty")
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 56.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                scaleX = 0.85f + 0.15f * appear.value
                scaleY = 0.85f + 0.15f * appear.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.woody_mark), contentDescription = null, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(16.dp))
        Text(tr("Свободный день", "A free day"), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Нажмите +, чтобы добавить задачу", "Tap + to add a task"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(240.dp),
        )
    }
}

private fun dayTitle(day: LocalDate, today: LocalDate, lang: AppLanguage): String = when (ChronoUnit.DAYS.between(today, day)) {
    0L -> lang.pick("Сегодня", "Today")
    1L -> lang.pick("Завтра", "Tomorrow")
    -1L -> lang.pick("Вчера", "Yesterday")
    else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, lang.locale).replaceFirstChar { it.titlecase(lang.locale) }
}

private fun daySubtitle(day: LocalDate, today: LocalDate, lang: AppLanguage): String {
    val date = lang.dayMonth(day) + if (day.year != today.year) lang.pick(" ${day.year}", ", ${day.year}") else ""
    val delta = ChronoUnit.DAYS.between(today, day)
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, lang.locale)
    return if (delta in -1L..1L) "$date, $weekday" else date
}
