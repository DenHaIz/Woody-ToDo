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
 * и WorkManager: на прошивках, которые не дают запускать фоновые задачи (HyperOS без автозапуска),
 * сессия не стартовала, и виджет навсегда оставался «загрузкой».
 */
class TodayWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_MIDNIGHT -> async { WidgetUpdater.updateNow(context) }
            WidgetUpdater.ACTION_TOGGLE -> async {
                val taskId = intent.getLongExtra(WidgetUpdater.EXTRA_TASK_ID, -1).takeIf { it > 0 } ?: return@async
                val day = LocalDate.ofEpochDay(intent.getLongExtra(WidgetUpdater.EXTRA_DAY, LocalDate.now().toEpochDay()))
                val done = intent.getBooleanExtra(WidgetUpdater.EXTRA_DONE, true)
                (context.applicationContext as WoodyApp).container.repository.setDone(taskId, day, done)
                WidgetUpdater.updateNow(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        async { WidgetUpdater.updateNow(context, appWidgetIds) }
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

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { updateNow(app) } }
    }

    /** Перерисовать виджеты [ids] (по умолчанию — все) под их текущий размер. */
    suspend fun updateNow(context: Context, ids: IntArray? = null) = lock.withLock {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val widgetIds = ids ?: manager.getAppWidgetIds(ComponentName(app, TodayWidgetReceiver::class.java))
        if (widgetIds.isEmpty()) return@withLock
        widgetIds.forEach { id ->
            runCatching { manager.updateAppWidget(id, render(app, id, sizesOf(manager.getAppWidgetOptions(id)))) }
                .onFailure { Log.w("Woody", "widget $id", it) }
        }
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
        if (!exact.isNullOrEmpty()) return exact.take(MaxSizes)
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        if (minWidth <= 0 || minHeight <= 0) return listOf(DefaultSize)
        // Портрет: узкий и высокий, альбом: широкий и низкий.
        return listOf(
            DpSize(minWidth.dp, maxHeight.coerceAtLeast(minHeight).dp),
            DpSize(maxWidth.coerceAtLeast(minWidth).dp, minHeight.dp),
        )
    }

    private val DefaultSize = DpSize(250.dp, 64.dp)
    private const val MaxSizes = 4

    /**
     * Будильник без пробуждения (RTC): в полночь телефон не будится — виджет перерисуется,
     * как только экран включат. Это ноль лишних пробуждений.
     */
    fun scheduleMidnight(context: Context) {
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 30_000
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, midnightIntent(context))
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
