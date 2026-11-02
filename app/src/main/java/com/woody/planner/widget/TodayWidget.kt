package com.woody.planner.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
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
 * Рисует его [WidgetUpdater] под точный размер на рабочем столе (Glance здесь — только разметка).
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

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

/**
 * Размеры элементов — от реального размера виджета, чтобы содержимое заполняло всё место:
 * в одну строку всё считается от высоты, у растянутого — крупные строки списка.
 */
private class Metrics(size: DpSize, private val fontScale: Float) {
    val tall = size.height >= TodayWidget.Tall.height
    val padH = if (tall) 16.dp else (size.height * 0.16f).coerceIn(10.dp, 18.dp)
    val padV = if (tall) 14.dp else (size.height * 0.07f).coerceIn(3.dp, 10.dp)
    private val inner = size.height - padV * 2

    /** Кружок-счётчик и «+». */
    val button = if (tall) 52.dp else (inner * 0.86f).coerceIn(36.dp, 84.dp)
    val check = if (tall) 34.dp else button * 0.52f
    val gap = (button * 0.2f).coerceIn(8.dp, 12.dp)
    val checkGap = gap * 0.7f

    /** Место под название ближайшей задачи в однострочном виджете. */
    val textWidth = size.width - padH * 2 - button * 2 - check - gap * 2 - checkGap
    val textHeight = inner

    /** Высота под список задач растянутого виджета (под заголовком). */
    val listHeight = inner - button - 10.dp

    /** Кегль в dp → sp: системный масштаб шрифта не должен ломать подобранную по месту вёрстку. */
    fun sp(dp: Float): TextUnit = (dp / fontScale).sp
}

@Composable
private fun WidgetContent(data: WidgetData, palette: WidgetPalette, lang: AppLanguage) {
    val m = Metrics(LocalSize.current, LocalContext.current.resources.configuration.fontScale)
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(palette.background)
            .clickable(actionStartActivity(openApp(data.day, newTask = false)))
            .padding(horizontal = m.padH, vertical = m.padV),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (m.tall) TallLayout(data, palette, lang, m) else OneRowLayout(data, palette, lang, m)
    }
}

/** Одна строка: счётчик, ближайшая задача, «+». */
@Composable
private fun OneRowLayout(data: WidgetData, palette: WidgetPalette, lang: AppLanguage, m: Metrics) {
    val next = data.open.firstOrNull()
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Counter(data, palette, m)
        Spacer(GlanceModifier.width(m.gap))
        if (next != null) {
            CheckCircle(next, palette, m)
            Spacer(GlanceModifier.width(m.checkGap))
            TitleBlock(next.title, meta(next, data, lang), palette, m, GlanceModifier.defaultWeight())
        } else {
            TitleBlock(
                if (data.total == 0) lang.pick("Свободный день", "A free day") else lang.pick("Всё сделано!", "All done!"),
                lang.pick("Сегодня", "Today"),
                palette,
                m,
                GlanceModifier.defaultWeight(),
            )
        }
        Spacer(GlanceModifier.width(m.gap))
        AddButton(data, palette, lang, m)
    }
}

/** Название с подписью: кегль и число строк подбираются под длину и место ([WidgetText]). */
@Composable
private fun TitleBlock(title: String, meta: String, palette: WidgetPalette, m: Metrics, modifier: GlanceModifier) {
    val fit = WidgetText.fit(title, m.textWidth.value, m.textHeight.value)
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            fit.text,
            style = TextStyle(color = palette.text, fontSize = m.sp(fit.size), fontWeight = FontWeight.Medium),
            maxLines = fit.lines,
        )
        if (fit.meta) {
            Text(meta, style = TextStyle(color = palette.secondary, fontSize = m.sp(fit.metaSize)), maxLines = 1)
        }
    }
}

/** Выше одной строки: заголовок и список задач дня. */
@Composable
private fun TallLayout(data: WidgetData, palette: WidgetPalette, lang: AppLanguage, m: Metrics) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Counter(data, palette, m)
            Spacer(GlanceModifier.width(m.gap))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    lang.pick("Сегодня", "Today"),
                    style = TextStyle(color = palette.text, fontSize = m.sp(22f), fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    data.day.format(DateTimeFormatter.ofPattern(lang.pick("d MMMM", "MMMM d"), lang.locale)),
                    style = TextStyle(color = palette.secondary, fontSize = m.sp(14f)),
                    maxLines = 1,
                )
            }
            AddButton(data, palette, lang, m)
        }
        Spacer(GlanceModifier.height(10.dp))
        val items = data.open + data.done
        if (items.isEmpty()) {
            Text(
                lang.pick("Свободный день — нажмите +", "A free day — tap +"),
                style = TextStyle(color = palette.secondary, fontSize = m.sp(16f)),
                modifier = GlanceModifier.padding(top = 6.dp),
            )
        } else {
            // Сколько строк помещается по высоте; остальное — «ещё N». Строки делят всю высоту — без пустоты внизу.
            // Обычная колонка, без списка-коллекции: так виджет одинаково рисуется на любых прошивках.
            val fitsAll = MinRow * items.size <= m.listHeight
            val slots = if (fitsAll) items.size else ((m.listHeight - MoreLine) / MinRow).toInt().coerceAtLeast(1)
            val shown = items.take(slots)
            val rowHeight = if (fitsAll) minOf(MaxRow, m.listHeight / items.size) else (m.listHeight - MoreLine) / slots
            Column(GlanceModifier.fillMaxWidth()) {
                shown.forEach { item -> TaskRow(item, palette, m, rowHeight) }
                if (shown.size < items.size) {
                    Text(
                        lang.pick("ещё ${items.size - shown.size}", "${items.size - shown.size} more"),
                        style = TextStyle(color = palette.secondary, fontSize = m.sp(15f)),
                        modifier = GlanceModifier.padding(start = m.check + m.checkGap, top = 8.dp),
                    )
                }
            }
        }
    }
}

private val MinRow = 40.dp
private val MaxRow = 52.dp
/** Строка «ещё N». */
private val MoreLine = 30.dp

@Composable
private fun TaskRow(item: TaskItem, palette: WidgetPalette, m: Metrics, height: Dp) {
    Row(
        GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(item, palette, m)
        Spacer(GlanceModifier.width(m.checkGap))
        Text(
            WidgetText.shorten(item.title),
            style = TextStyle(
                color = if (item.done) palette.tertiary else palette.text,
                fontSize = m.sp(17f),
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        item.time?.let {
            Text(
                it.format(TimeFormat),
                style = TextStyle(color = if (item.overdue) palette.accent else palette.secondary, fontSize = m.sp(15f)),
                modifier = GlanceModifier.padding(start = 8.dp),
            )
        }
    }
}

/** Кружок «сделано из стольких»: в нём число выполненных и всего. */
@Composable
private fun Counter(data: WidgetData, palette: WidgetPalette, m: Metrics) {
    val allDone = data.total > 0 && data.open.isEmpty()
    val label = "${data.done.size}/${data.total}"
    Box(
        GlanceModifier
            .size(m.button)
            .cornerRadius(m.button / 2)
            .background(if (allDone) palette.success else palette.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (allDone) {
            Image(
                ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.onAccent),
                modifier = GlanceModifier.size(m.button * 0.52f),
            )
        } else {
            Text(
                label,
                style = TextStyle(
                    color = palette.accent,
                    // «12/15» шире «0/1» — чуть мельче, чтобы не вылезало из кружка.
                    fontSize = m.sp(m.button.value * if (label.length <= 3) 0.32f else 0.25f),
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Кружок-галочка: нажатие отмечает задачу прямо с рабочего стола. */
@Composable
private fun CheckCircle(item: TaskItem, palette: WidgetPalette, m: Metrics) {
    val toggle = actionSendBroadcast(
        Intent(WidgetUpdater.ACTION_TOGGLE)
            .setClassName("com.woody.planner", TodayWidgetReceiver::class.java.name)
            .putExtra(WidgetUpdater.EXTRA_TASK_ID, item.taskId)
            .putExtra(WidgetUpdater.EXTRA_DAY, item.day.toEpochDay())
            .putExtra(WidgetUpdater.EXTRA_DONE, !item.done)
            // Свои данные у каждой задачи — чтобы система не склеила кружки в один PendingIntent.
            .setData("woody://widget/toggle/${item.taskId}/${item.day.toEpochDay()}/${!item.done}".toUri()),
    )
    val circle = m.check * 0.88f
    Box(
        GlanceModifier.size(m.check).clickable(toggle),
        contentAlignment = Alignment.Center,
    ) {
        if (item.done) {
            Box(
                GlanceModifier.size(circle).cornerRadius(circle / 2).background(palette.success),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.onAccent),
                    modifier = GlanceModifier.size(circle * 0.66f),
                )
            }
        } else {
            Image(
                ImageProvider(R.drawable.ic_widget_circle),
                contentDescription = item.title,
                colorFilter = ColorFilter.tint(palette.tertiary),
                modifier = GlanceModifier.size(circle),
            )
        }
    }
}

@Composable
private fun AddButton(data: WidgetData, palette: WidgetPalette, lang: AppLanguage, m: Metrics) {
    Box(
        GlanceModifier
            .size(m.button)
            .cornerRadius(m.button / 2)
            .background(palette.accent)
            .clickable(actionStartActivity(openApp(data.day, newTask = true))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(R.drawable.ic_widget_plus),
            contentDescription = lang.pick("Новая задача", "New task"),
            colorFilter = ColorFilter.tint(palette.onAccent),
            modifier = GlanceModifier.size(m.button * 0.5f),
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
