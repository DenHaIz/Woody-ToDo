package com.woody.planner.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.core.net.toUri
import com.woody.planner.MainActivity
import com.woody.planner.R
import com.woody.planner.WoodyApp
import com.woody.planner.data.AppLanguage
import com.woody.planner.data.ThemeMode
import com.woody.planner.domain.TaskItem
import com.woody.planner.reminders.Notifications
import com.woody.planner.ui.theme.WoodyColors
import com.woody.planner.ui.theme.woodyColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Виджет «Сегодня». В одну строку (5×1, как раз под поисковую строку) — счётчик дня, ближайшая
 * задача с кружком «готово» и «+». Если растянуть выше — список задач дня с галочками.
 * Обновляется только когда меняются задачи и раз в сутки после полуночи — без периодической работы.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(OneRow, Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as WoodyApp).container
        val today = LocalDate.now()
        val agenda = container.repository.observeDay(today, today).first()
        val lang = container.settings.language.first()
        val dynamic = container.settings.dynamicColors.first()
        val mode = container.settings.themeMode.first()
        val light = woodyColors(context, dark = mode == ThemeMode.Dark, dynamic = dynamic)
        val dark = woodyColors(context, dark = mode != ThemeMode.Light, dynamic = dynamic)
        val palette = WidgetPalette(light, dark)
        val open = agenda.overdue + agenda.timed + agenda.untimed
        val data = WidgetData(
            day = today,
            open = open,
            done = agenda.done,
            total = open.size + agenda.done.size,
        )
        provideContent { WidgetContent(data, palette, lang) }
    }

    companion object {
        val OneRow = DpSize(180.dp, 40.dp)
        val Tall = DpSize(180.dp, 120.dp)
    }
}

private class WidgetData(val day: LocalDate, val open: List<TaskItem>, val done: List<TaskItem>, val total: Int)

/** Цвета приложения для дневной и ночной темы рабочего стола. */
private class WidgetPalette(light: WoodyColors, dark: WoodyColors) {
    val background = pair(light.background.copy(alpha = 0.9f), dark.background.copy(alpha = 0.88f))
    val text = pair(light.textPrimary, dark.textPrimary)
    val secondary = pair(light.textSecondary, dark.textSecondary)
    val tertiary = pair(light.textTertiary, dark.textTertiary)
    val accent = pair(light.accent, dark.accent)
    val onAccent = pair(light.onAccent, dark.onAccent)
    val accentSoft = pair(light.accent.copy(alpha = 0.14f), dark.accent.copy(alpha = 0.22f))
    val success = pair(light.success, dark.success)
    val chip = pair(light.chip, dark.chip)

    private fun pair(day: Color, night: Color) = ColorProvider(day = day, night = night)
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun WidgetContent(data: WidgetData, palette: WidgetPalette, lang: AppLanguage) {
    val tall = LocalSize.current.height >= TodayWidget.Tall.height
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(palette.background)
            .clickable(actionStartActivity(openApp(data.day, newTask = false)))
            .padding(horizontal = 14.dp, vertical = if (tall) 12.dp else 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (tall) TallLayout(data, palette, lang) else OneRowLayout(data, palette, lang)
    }
}

/** Одна строка: счётчик, ближайшая задача, «+». */
@Composable
private fun OneRowLayout(data: WidgetData, palette: WidgetPalette, lang: AppLanguage) {
    val next = data.open.firstOrNull()
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Counter(data, palette)
        Spacer(GlanceModifier.width(12.dp))
        if (next != null) {
            CheckCircle(next, palette)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    next.title,
                    style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    meta(next, data, lang),
                    style = TextStyle(color = palette.secondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        } else {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    if (data.total == 0) lang.pick("Свободный день", "A free day") else lang.pick("Всё сделано!", "All done!"),
                    style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    lang.pick("Сегодня", "Today"),
                    style = TextStyle(color = palette.secondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        AddButton(data, palette, lang)
    }
}

/** Выше одной строки: заголовок и список задач дня. */
@Composable
private fun TallLayout(data: WidgetData, palette: WidgetPalette, lang: AppLanguage) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Counter(data, palette)
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    lang.pick("Сегодня", "Today"),
                    style = TextStyle(color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    data.day.format(DateTimeFormatter.ofPattern(lang.pick("d MMMM", "MMMM d"), lang.locale)),
                    style = TextStyle(color = palette.secondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            AddButton(data, palette, lang)
        }
        Spacer(GlanceModifier.height(8.dp))
        val items = data.open + data.done
        if (items.isEmpty()) {
            Text(
                lang.pick("Свободный день — нажмите +", "A free day — tap +"),
                style = TextStyle(color = palette.secondary, fontSize = 14.sp),
                modifier = GlanceModifier.padding(top = 6.dp),
            )
        } else {
            // Сколько строк помещается по высоте; остальное — «ещё N». Обычная колонка, без списка-коллекции:
            // так виджет одинаково рисуется на любых прошивках.
            val fit = (((LocalSize.current.height - 84.dp) / RowHeight).toInt()).coerceAtLeast(1)
            val shown = if (items.size > fit) items.take(fit - 1) else items
            Column(GlanceModifier.fillMaxWidth()) {
                shown.forEach { item -> TaskRow(item, palette) }
                if (shown.size < items.size) {
                    Text(
                        lang.pick("ещё ${items.size - shown.size}", "${items.size - shown.size} more"),
                        style = TextStyle(color = palette.secondary, fontSize = 13.sp),
                        modifier = GlanceModifier.padding(start = 40.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}

private val RowHeight = 34.dp

@Composable
private fun TaskRow(item: TaskItem, palette: WidgetPalette) {
    Row(
        GlanceModifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(item, palette)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            item.title,
            style = TextStyle(
                color = if (item.done) palette.tertiary else palette.text,
                fontSize = 15.sp,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        item.time?.let {
            Text(
                it.format(TimeFormat),
                style = TextStyle(color = if (item.overdue) palette.accent else palette.secondary, fontSize = 13.sp),
                modifier = GlanceModifier.padding(start = 8.dp),
            )
        }
    }
}

/** Кружок «сделано из стольких»: в нём число выполненных и всего. */
@Composable
private fun Counter(data: WidgetData, palette: WidgetPalette) {
    val allDone = data.total > 0 && data.open.isEmpty()
    Box(
        GlanceModifier
            .size(42.dp)
            .cornerRadius(21.dp)
            .background(if (allDone) palette.success else palette.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (allDone) {
            Image(
                ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.onAccent),
                modifier = GlanceModifier.size(22.dp),
            )
        } else {
            Text(
                "${data.done.size}/${data.total}",
                style = TextStyle(color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/** Кружок-галочка: нажатие отмечает задачу прямо с рабочего стола. */
@Composable
private fun CheckCircle(item: TaskItem, palette: WidgetPalette) {
    val toggle = actionRunCallback<ToggleTaskAction>(
        actionParametersOf(
            ToggleTaskAction.TaskId to item.taskId,
            ToggleTaskAction.Day to item.day.toEpochDay(),
            ToggleTaskAction.Done to !item.done,
        ),
    )
    Box(
        GlanceModifier.size(30.dp).clickable(toggle),
        contentAlignment = Alignment.Center,
    ) {
        if (item.done) {
            Box(
                GlanceModifier.size(24.dp).cornerRadius(12.dp).background(palette.success),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.onAccent),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        } else {
            Image(
                ImageProvider(R.drawable.ic_widget_circle),
                contentDescription = item.title,
                colorFilter = ColorFilter.tint(palette.tertiary),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AddButton(data: WidgetData, palette: WidgetPalette, lang: AppLanguage) {
    Box(
        GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .background(palette.accent)
            .clickable(actionStartActivity(openApp(data.day, newTask = true))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(R.drawable.ic_widget_plus),
            contentDescription = lang.pick("Новая задача", "New task"),
            colorFilter = ColorFilter.tint(palette.onAccent),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

private fun meta(item: TaskItem, data: WidgetData, lang: AppLanguage): String = buildString {
    item.time?.let { append(it.format(TimeFormat)) }
    if (item.overdue) {
        if (isNotEmpty()) append(" · ")
        append(lang.pick("просрочено", "overdue"))
    }
    item.category?.let {
        if (isNotEmpty()) append(" · ")
        append(it.name)
    }
    val left = data.open.size - 1
    if (left > 0) {
        if (isNotEmpty()) append(" · ")
        append(lang.pick("ещё $left", "+$left more"))
    }
    if (isEmpty()) append(lang.pick("Сегодня", "Today"))
}

private fun openApp(day: LocalDate, newTask: Boolean): Intent =
    Intent(Intent.ACTION_VIEW)
        .setClassName("com.woody.planner", MainActivity::class.java.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(Notifications.EXTRA_OPEN_DAY, day.toEpochDay())
        .putExtra(MainActivity.EXTRA_NEW_TASK, newTask)
        // Разные данные — чтобы система не склеила два PendingIntent («открыть» и «+») в один.
        .setData("woody://widget/${if (newTask) "new" else "open"}".toUri())

/** Отметка задачи из виджета: запись в базу, виджет обновится сам (через [WidgetUpdater]). */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskId] ?: return
        val day = parameters[Day]?.let(LocalDate::ofEpochDay) ?: return
        val done = parameters[Done] ?: return
        (context.applicationContext as WoodyApp).container.repository.setDone(taskId, day, done)
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val TaskId = ActionParameters.Key<Long>("task_id")
        val Day = ActionParameters.Key<Long>("epoch_day")
        val Done = ActionParameters.Key<Boolean>("done")
    }
}
