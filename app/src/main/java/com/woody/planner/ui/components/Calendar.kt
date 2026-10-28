package com.woody.planner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woody.planner.domain.DaySummary
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val RuLocale: Locale = Locale.forLanguageTag("ru")

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val WEEK_PAGES = 4000
private const val WEEK_START_PAGE = WEEK_PAGES / 2

/**
 * Лента недели. Листается горизонтально по неделям, выбранный день отмечен «жидкой» каплей,
 * которая перетекает между днями.
 */
@Composable
fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    summaries: @Composable (weekStart: LocalDate) -> List<DaySummary>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseWeek = remember { today.weekStart() }
    fun pageOf(day: LocalDate) = WEEK_START_PAGE + ChronoUnit.WEEKS.between(baseWeek, day.weekStart()).toInt()
    val pager = rememberPagerState(initialPage = pageOf(selected)) { WEEK_PAGES }

    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (pager.currentPage != target) pager.animateScrollToPage(target, animationSpec = Motion.soft())
    }

    HorizontalPager(state = pager, modifier = modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
        val weekStart = baseWeek.plusWeeks((page - WEEK_START_PAGE).toLong())
        WeekRow(weekStart, selected, today, summaries(weekStart), onSelect)
    }
}

@Composable
private fun WeekRow(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    summaries: List<DaySummary>,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = WoodyTheme.colors
    val index = ChronoUnit.DAYS.between(weekStart, selected).toInt()
    val inWeek = index in 0..6
    val target = index.coerceIn(0, 6).toFloat()
    val lead = animateFloatAsState(target, Motion.leading(), label = "lead")
    val lag = animateFloatAsState(target, Motion.lagging(), label = "lag")
    val visible = animateFloatAsState(if (inWeek) 1f else 0f, Motion.snappy(), label = "pill")
    val haptics = LocalHapticFeedback.current

    Row(
        Modifier
            .fillMaxWidth()
            .liquidIndicator(
                count = 7,
                leading = { lead.value },
                lagging = { lag.value },
                visibility = { visible.value },
                color = colors.textPrimary,
                inset = 3.dp,
                verticalInset = 0.dp,
            ),
    ) {
        repeat(7) { i ->
            val day = weekStart.plusDays(i.toLong())
            val isSelected = day == selected
            val summary = summaries.getOrNull(i)
            val textColor by animateColorAsState(
                when {
                    isSelected -> colors.background
                    day == today -> colors.accent
                    else -> colors.textPrimary
                },
                Motion.snappy(),
                label = "dayText",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(interactionSource = null, indication = null) {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(day)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, RuLocale),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                )
                Text(
                    "${day.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (day == today || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                )
                DayDots(summary, if (isSelected) colors.background else null)
            }
        }
    }
}

/** Точки под датой: цвета категорий; когда всё выполнено — одна зелёная. */
@Composable
private fun DayDots(summary: DaySummary?, override: Color?) {
    val colors = WoodyTheme.colors
    Row(
        Modifier
            .height(8.dp)
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (summary == null || summary.total == 0) return@Row
        val dots = when {
            summary.done == summary.total -> listOf(colors.success)
            summary.colors.isEmpty() -> listOf(colors.textTertiary)
            else -> summary.colors.map { Color(it) }
        }
        dots.forEach { c ->
            Box(
                Modifier
                    .size(5.dp)
                    .background(override ?: c, CircleShape),
            )
        }
    }
}

/** Месячный календарь, разворачивается из ленты недели. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    summaries: @Composable (gridStart: LocalDate) -> List<DaySummary>,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WoodyTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                WoodyIcons.ChevronLeft,
                contentDescription = "Предыдущий месяц",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onMonthChange(month.minusMonths(1)) }
                    .padding(6.dp),
            )
            AnimatedContent(
                targetState = month,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(Motion.soft()) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally(Motion.soft()) { if (forward) -it / 2 else it / 2 } + fadeOut())
                },
                contentAlignment = Alignment.Center,
                label = "monthTitle",
            ) { m ->
                Text(
                    m.month.getDisplayName(TextStyle.FULL_STANDALONE, RuLocale).replaceFirstChar { it.titlecase(RuLocale) } +
                        if (m.year != today.year) " ${m.year}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Icon(
                WoodyIcons.ChevronRight,
                contentDescription = "Следующий месяц",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onMonthChange(month.plusMonths(1)) }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, RuLocale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(Motion.soft()) { if (forward) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally(Motion.soft()) { if (forward) -it else it } + fadeOut())
            },
            label = "monthGrid",
        ) { m ->
            val gridStart = m.atDay(1).weekStart()
            val data = summaries(gridStart)
            val weeks = ((ChronoUnit.DAYS.between(gridStart, m.atEndOfMonth()) / 7) + 1).toInt()
            Column {
                repeat(weeks) { w ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { d ->
                            val offset = w * 7 + d
                            val day = gridStart.plusDays(offset.toLong())
                            MonthDayCell(
                                day = day,
                                inMonth = day.month == m.month,
                                selected = day == selected,
                                today = day == today,
                                summary = data.getOrNull(offset),
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    summary: DaySummary?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = WoodyTheme.colors
    val bg by animateColorAsState(if (selected) colors.textPrimary else Color.Transparent, Motion.snappy(), label = "cellBg")
    val fg = when {
        selected -> colors.background
        today -> colors.accent
        inMonth -> colors.textPrimary
        else -> colors.textTertiary.copy(alpha = 0.6f)
    }
    Column(
        modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(bg)
            .bouncyClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
        )
        DayDots(summary, if (selected) colors.background else null)
    }
}
