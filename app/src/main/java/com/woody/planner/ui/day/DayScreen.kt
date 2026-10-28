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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woody.planner.R
import com.woody.planner.domain.DayAgenda
import com.woody.planner.domain.TaskItem
import com.woody.planner.ui.components.MonthCalendar
import com.woody.planner.ui.components.ProgressRing
import com.woody.planner.ui.components.RuLocale
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

private const val DAY_PAGES = 20_000
private const val DAY_START_PAGE = DAY_PAGES / 2
private val DayMonthFormat = DateTimeFormatter.ofPattern("d MMMM", RuLocale)

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

    // Свайп страниц → выбранный день (после остановки, чтобы не дёргать шапку на полпути).
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.select(baseDay.plusDays((it - DAY_START_PAGE).toLong())) }
    }
    // Выбор дня в ленте/календаре → плавная прокрутка страниц.
    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (pager.currentPage != target) pager.animateScrollToPage(target, animationSpec = Motion.soft())
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { headerHeight = it.height },
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
                    .bouncyClickable(pressedScale = 0.97f) {
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
                        Text(dayTitle(day, today), style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(daySubtitle(day, today), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Icon(
                                WoodyIcons.ChevronDown,
                                contentDescription = if (expanded) "Свернуть календарь" else "Открыть календарь",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = chevron.value },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = selected != today,
                enter = scaleIn(Motion.bouncy()) + fadeIn(),
                exit = scaleOut(Motion.snappy()) + fadeOut(),
            ) {
                Text(
                    "Сегодня",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .bouncyClickable { vm.select(today) }
                        .clip(Shapes.Pill)
                        .background(colors.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            ProgressRing(done = agenda?.doneCount ?: 0, total = agenda?.total ?: 0)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(Motion.snappy()) + scaleIn(Motion.soft(), initialScale = 0.96f)) togetherWith
                    fadeOut(Motion.snappy()) using SizeTransform(clip = false) { _, _ -> Motion.soft() }
            },
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topPadding + 8.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (current.isEmpty) {
            item(key = "empty") { EmptyDay(Modifier.animateItem()) }
        }
        taskSection("overdue", "Просрочено", current.overdue, vm, onEditTask, warning)
        taskSection("timed", "По расписанию", current.timed, vm, onEditTask)
        taskSection("untimed", if (current.timed.isEmpty()) "Задачи" else "В течение дня", current.untimed, vm, onEditTask)
        if (current.done.isNotEmpty()) {
            item(key = "header-done") {
                DoneHeader(current, doneExpanded, { doneExpanded = !doneExpanded }, Modifier.animateItem())
            }
            if (doneExpanded) taskItems(current.done, vm, onEditTask)
        }
    }
}

private fun LazyListScope.taskSection(
    key: String,
    title: String,
    tasks: List<TaskItem>,
    vm: DayViewModel,
    onEditTask: (Long) -> Unit,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$key") {
        SectionHeader(title, Modifier.animateItem(), accent)
    }
    taskItems(tasks, vm, onEditTask)
}

private fun LazyListScope.taskItems(tasks: List<TaskItem>, vm: DayViewModel, onEditTask: (Long) -> Unit) {
    items(tasks, key = { "task-${it.taskId}-${it.day.toEpochDay()}" }) { item ->
        val colors = WoodyTheme.colors
        SwipeableTaskCard(
            modifier = Modifier.animateItem(
                fadeInSpec = Motion.snappy(),
                placementSpec = Motion.soft(),
                fadeOutSpec = Motion.snappy(),
            ),
            startAction = SwipeAction(
                icon = if (item.done) WoodyIcons.Undo else WoodyIcons.Check,
                label = if (item.done) "Вернуть" else "Готово",
                color = colors.success,
                dismiss = false,
                onTrigger = { vm.toggleDone(item) },
            ),
            endAction = when {
                item.done -> SwipeAction(WoodyIcons.Trash, "Удалить", colors.accent, dismiss = true) { vm.delete(item.taskId) }
                item.isRecurring -> SwipeAction(WoodyIcons.Skip, "Пропустить", colors.warning, dismiss = true) { vm.postpone(item) }
                item.overdue -> SwipeAction(WoodyIcons.ArrowRight, "На сегодня", colors.warning, dismiss = true) { vm.postpone(item) }
                else -> SwipeAction(WoodyIcons.ArrowRight, "На завтра", colors.warning, dismiss = true) { vm.postpone(item) }
            },
        ) {
            TaskCard(
                item = item,
                onToggle = { vm.toggleDone(item) },
                onToggleSubtask = { vm.toggleSubtask(item, it) },
                onEdit = { onEditTask(item.taskId) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier, accent: androidx.compose.ui.graphics.Color? = null) {
    Text(
        title.uppercase(RuLocale),
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
            "ВЫПОЛНЕНО · ${agenda.doneCount}",
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
        Text("Свободный день", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Нажмите +, чтобы добавить задачу",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(240.dp),
        )
    }
}

private fun dayTitle(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, day)) {
    0L -> "Сегодня"
    1L -> "Завтра"
    -1L -> "Вчера"
    else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, RuLocale).replaceFirstChar { it.titlecase(RuLocale) }
}

private fun daySubtitle(day: LocalDate, today: LocalDate): String {
    val date = day.format(DayMonthFormat) + if (day.year != today.year) " ${day.year}" else ""
    val delta = ChronoUnit.DAYS.between(today, day)
    return if (delta in -1L..1L) "$date, ${day.dayOfWeek.getDisplayName(TextStyle.FULL, RuLocale)}" else date
}
