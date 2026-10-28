package com.woody.planner.ui.day

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woody.planner.domain.SubtaskItem
import com.woody.planner.domain.TaskItem
import com.woody.planner.ui.components.AnimatedCheckbox
import com.woody.planner.ui.components.MetaTag
import com.woody.planner.ui.components.RuLocale
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.glass.FrostedCard
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import java.time.format.DateTimeFormatter
import kotlin.math.min

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val ShortDateFormat = DateTimeFormatter.ofPattern("d MMM", RuLocale)

@Composable
fun TaskCard(
    item: TaskItem,
    onToggle: () -> Unit,
    onToggleSubtask: (SubtaskItem) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WoodyTheme.colors
    val haptics = LocalHapticFeedback.current
    val categoryColor = item.category?.let { Color(it.colorArgb) } ?: colors.accent
    val expandable = item.subtasks.isNotEmpty() || item.note.isNotBlank()
    var expanded by rememberSaveable(item.taskId, item.day) { mutableStateOf(false) }
    val alpha = animateFloatAsState(if (item.done) 0.6f else 1f, Motion.soft(), label = "doneAlpha")
    val chevron = animateFloatAsState(if (expanded) 180f else 0f, Motion.bouncy(), label = "chevron")

    FrostedCard(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value }) {
        Column(
            Modifier
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit()
                    },
                    onClick = { if (expandable) expanded = !expanded else onEdit() },
                )
                .animateContentSize(Motion.soft())
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedCheckbox(checked = item.done, color = categoryColor, onToggle = onToggle)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StrikeText(item.title, struck = item.done, style = MaterialTheme.typography.titleMedium)
                    TaskMeta(item)
                }
                if (expandable) {
                    Icon(
                        WoodyIcons.ChevronDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevron.value },
                    )
                }
            }
            if (expanded) {
                Column(Modifier.padding(start = 44.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.note.isNotBlank()) {
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    item.subtasks.forEach { sub ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCheckbox(
                                checked = sub.done,
                                color = categoryColor,
                                onToggle = { onToggleSubtask(sub) },
                                size = 18.dp,
                                touchSize = 34.dp,
                            )
                            StrikeText(sub.title, struck = sub.done, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        "Изменить",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .bouncyClickable(onClick = onEdit)
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskMeta(item: TaskItem) {
    val colors = WoodyTheme.colors
    val hasMeta = item.time != null || item.overdue || item.category != null || item.isRecurring ||
        item.hasReminder || item.subtasks.isNotEmpty()
    if (!hasMeta) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (item.overdue) {
            MetaTag(item.day.format(ShortDateFormat), icon = WoodyIcons.Calendar, color = colors.warning)
        }
        item.time?.let { MetaTag(it.format(TimeFormat), icon = WoodyIcons.Clock) }
        item.category?.let { MetaTag("${it.emoji} ${it.name}") }
        if (item.subtasks.isNotEmpty()) {
            MetaTag("${item.subtasksDone}/${item.subtasks.size}", icon = WoodyIcons.Checklist)
        }
        if (item.isRecurring) MetaTag("", icon = WoodyIcons.Repeat)
        if (item.hasReminder) MetaTag("", icon = WoodyIcons.Bell)
    }
}

/** Текст, который зачёркивается слева направо, строка за строкой. */
@Composable
fun StrikeText(text: String, struck: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = WoodyTheme.colors
    val progress = animateFloatAsState(if (struck) 1f else 0f, Motion.soft(), label = "strike")
    val color by animateColorAsState(if (struck) colors.textTertiary else colors.textPrimary, Motion.snappy(), label = "strikeColor")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            val p = progress.value
            if (p <= 0f) return@drawWithContent
            val widths = (0 until result.lineCount).map { result.getLineRight(it) - result.getLineLeft(it) }
            var remaining = widths.sum() * p
            for (line in 0 until result.lineCount) {
                if (remaining <= 0f) break
                val left = result.getLineLeft(line)
                val w = min(widths[line], remaining)
                val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2f
                drawLine(color, Offset(left, y), Offset(left + w, y), 1.6.dp.toPx(), StrokeCap.Round)
                remaining -= w
            }
        },
    )
}
