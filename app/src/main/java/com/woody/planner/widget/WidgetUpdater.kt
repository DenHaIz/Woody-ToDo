package com.woody.planner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Приёмник виджета «Сегодня»: плюс смена дня в полночь. */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_MIDNIGHT) {
            WidgetUpdater.refresh(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelMidnight(context)
    }
}

/**
 * Обновление виджета по событиям: изменились задачи, язык, цвета или наступил новый день.
 * Если виджета на рабочем столе нет — ничего не делает.
 */
object WidgetUpdater {
    const val ACTION_MIDNIGHT = "com.woody.planner.WIDGET_MIDNIGHT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val ids = GlanceAppWidgetManager(app).getGlanceIds(TodayWidget::class.java)
                if (ids.isNotEmpty()) {
                    TodayWidget().updateAll(app)
                    scheduleMidnight(app)
                }
            }
        }
    }

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
