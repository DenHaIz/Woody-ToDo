package com.woody.planner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import com.woody.planner.WoodyApp
import com.woody.planner.reminders.PinnedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Приёмник виджета «Сегодня». Виджет рисуется прямо здесь, за доли секунды, — без сессий Glance
 * и WorkManager: на прошивках, которые не дают запускать фоновые задачи (vivo OriginOS: Glance для vivo отправляет работу в WorkManager, а прошивка его не запускает),
 * сессия не стартовала, и виджет навсегда оставался «загрузкой».
 */
class TodayWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_MIDNIGHT -> async {
                WidgetUpdater.updateNow(context)
                WidgetUpdater.wakeUp(context)
            }
            WidgetUpdater.ACTION_TOGGLE -> async {
                val taskId = intent.getLongExtra(WidgetUpdater.EXTRA_TASK_ID, -1).takeIf { it > 0 } ?: return@async
                val day = LocalDate.ofEpochDay(intent.getLongExtra(WidgetUpdater.EXTRA_DAY, LocalDate.now().toEpochDay()))
                val done = intent.getBooleanExtra(WidgetUpdater.EXTRA_DONE, true)
                // Виджет нарисован вчера (прошивка не дала обновить его в полночь) — кружок относится к вчерашнему
                // списку. Не отмечаем наугад: показываем сегодняшний список, пусть нажмут ещё раз.
                val shownDay = intent.getLongExtra(WidgetUpdater.EXTRA_SHOWN_DAY, LocalDate.now().toEpochDay())
                if (shownDay == LocalDate.now().toEpochDay()) {
                    (context.applicationContext as WoodyApp).container.repository.setDone(taskId, day, done)
                }
                WidgetUpdater.updateNow(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        async {
            WidgetUpdater.updateNow(context, appWidgetIds)
            // Система будит виджет раз в час (updatePeriodMillis) — заодно доставляем напоминания,
            // которые прошивка могла придержать.
            WidgetUpdater.wakeUp(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Виджет растянули или сжали — перерисовываем под новый размер.
        async { WidgetUpdater.updateNow(context, intArrayOf(appWidgetId)) }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelMidnight(context)
    }

    /** Короткая работа в приёмнике: процесс не убьют, пока она не закончится. */
    private fun async(block: suspend () -> Unit) {
        val pending: PendingResult? = goAsync()
        WidgetUpdater.scope.launch {
            try {
                runCatching { block() }
            } finally {
                pending?.finish()
            }
        }
    }
}

/**
 * Обновление виджета по событиям: изменились задачи, язык, цвета или наступил новый день.
 * Если виджета на рабочем столе нет — ничего не делает.
 */
object WidgetUpdater {
    const val ACTION_MIDNIGHT = "com.woody.planner.WIDGET_MIDNIGHT"
    const val ACTION_TOGGLE = "com.woody.planner.WIDGET_TOGGLE"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_DAY = "epoch_day"
    const val EXTRA_DONE = "done"
    /** День, на который нарисован виджет, — чтобы не отметить задачу во «вчерашнем» виджете. */
    const val EXTRA_SHOWN_DAY = "shown_day"

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { updateNow(app) } }
    }

    /** Виджет показывает не сегодняшний день (или ещё ни разу не рисовался в этой версии). */
    fun isStale(context: Context): Boolean =
        prefs(context).getLong(KeyDay, Long.MIN_VALUE) != LocalDate.now().toEpochDay()

    /** Перерисовать, только если виджет устарел: дёшево, можно звать при каждом запуске процесса и разблокировке. */
    fun refreshIfStale(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { if (isStale(app) && hasWidgets(app)) updateNow(app) }
        }
    }

    private fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java)).isNotEmpty()

    /**
     * Процесс разбудили по расписанию: доставить напоминания, которые прошивка могла придержать,
     * и при необходимости вернуть постоянное уведомление.
     */
    internal suspend fun wakeUp(context: Context) {
        (context.applicationContext as WoodyApp).container.reminders.catchUp()
        PinnedService.ensure(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private const val KeyDay = "shown_day"

    /** Перерисовать виджеты [ids] (по умолчанию — все) под их текущий размер. */
    suspend fun updateNow(context: Context, ids: IntArray? = null) = lock.withLock {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        // То же «сегодня» — в постоянном уведомлении, если оно включено.
        PinnedService.refresh(app)
        val widgetIds = ids ?: manager.getAppWidgetIds(ComponentName(app, TodayWidgetReceiver::class.java))
        if (widgetIds.isEmpty()) return@withLock
        val today = LocalDate.now().toEpochDay()
        widgetIds.forEach { id ->
            val sizes = sizesOf(manager.getAppWidgetOptions(id))
            runCatching { manager.updateAppWidget(id, render(app, id, sizes)) }
                .recoverCatching {
                    // Запасной путь: один размер вместо набора — пусть мельче, но виджет не останется пустым.
                    Log.w("Woody", "widget $id: sized views failed", it)
                    manager.updateAppWidget(id, render(app, id, sizes.take(1)))
                }
                .onFailure { Log.w("Woody", "widget $id", it) }
        }
        prefs(app).edit().putLong(KeyDay, today).apply()
        scheduleMidnight(app)
    }

    /**
     * Готовый виджет для каждого размера, который сообщил лаунчер (обычно портрет и альбом);
     * система сама показывает подходящий.
     */
    internal suspend fun render(context: Context, appWidgetId: Int, sizes: List<DpSize>): RemoteViews {
        // Кнопкам виджета номер не нужен (это обычные PendingIntent), поэтому, если система его
        // не узнаёт, рисуем без него.
        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
        val widget = TodayWidget()
        val views = sizes.distinct().associate { size ->
            val rendered = if (glanceId != null) widget.compose(context, glanceId, size = size) else widget.compose(context, size = size)
            SizeF(size.width.value, size.height.value) to rendered
        }
        return views.values.singleOrNull() ?: RemoteViews(views)
    }

    /** Размеры виджета на рабочем столе, в dp. */
    internal fun sizesOf(options: Bundle?): List<DpSize> {
        @Suppress("DEPRECATION")
        val exact = options?.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.filter { it.width > 0 && it.height > 0 }
            ?.map { DpSize(it.width.dp, it.height.dp) }
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val reported = buildList {
            exact?.let { addAll(it.take(4)) }
            if (minWidth > 0 && minHeight > 0) {
                // Портрет: узкий и высокий, альбом: широкий и низкий.
                add(DpSize(minWidth.dp, maxHeight.coerceAtLeast(minHeight).dp))
                add(DpSize(maxWidth.coerceAtLeast(minWidth).dp, minHeight.dp))
            }
        }.distinct()
        if (reported.isEmpty()) return listOf(DefaultSize)
        // Некоторые лаунчеры (vivo OriginOS) сообщают размер меньше настоящего — тогда содержимое занимало
        // половину виджета. Добавляем варианты повыше: система сама показывает самый крупный из тех,
        // что помещаются в настоящий размер, так что лишние ступени ничего не ломают.
        val width = reported.minOf { it.width }
        val low = reported.minOf { it.height }
        val high = maxOf(reported.maxOf { it.height }, low * 1.5f)
        val ladder = (1..LadderSteps).map { DpSize(width, low + (high - low) * it / LadderSteps.toFloat()) }
        return (reported + ladder).distinct().take(MaxSizes)
    }

    private val DefaultSize = DpSize(250.dp, 64.dp)
    private const val LadderSteps = 3
    /** Не больше 10 вариантов: каждый — отдельная отрисовка и место в памяти лаунчера. */
    private const val MaxSizes = 10

    /**
     * Перерисовка в полночь — точный будильник с пробуждением, одно пробуждение в сутки. Будильник без
     * пробуждения vivo придерживал до утра (а то и до запуска приложения), и виджет показывал вчерашний день.
     * Подстраховка — ежечасное обновление от самой системы (updatePeriodMillis, пробуждения система
     * объединяет с чужими) и проверка при разблокировке, если включено постоянное уведомление.
     */
    fun scheduleMidnight(context: Context) {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, midnightIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, midnightIntent(context))
        }
    }

    fun cancelMidnight(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TodayWidgetReceiver::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
