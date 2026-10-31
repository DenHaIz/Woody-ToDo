package com.woody.planner.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.woody.planner.R
import com.woody.planner.WoodyApp
import com.woody.planner.data.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Виджет рисуется прямо в приёмнике — без сессий Glance и WorkManager (на HyperOS без автозапуска
 * они не стартовали, и виджет оставался бесконечной загрузкой).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WidgetTest {

    private val app get() = ApplicationProvider.getApplicationContext<WoodyApp>()
    private val manager get() = AppWidgetManager.getInstance(app)

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun widgetIsDrawnRightAwayWithTodayTasks() {
        runBlocking { app.container.repository.save(TaskDraft(title = "Купить курицу", date = LocalDate.now())) }
        val id = shadowOf(manager).createWidget(TodayWidgetReceiver::class.java, R.layout.widget_initial)
        runBlocking { WidgetUpdater.updateNow(app) }
        val shown = texts(shadowOf(manager).getViewFor(id))
        assertTrue("на виджете: $shown", shown.any { it == "Купить курицу" })
        assertTrue("на виджете: $shown", shown.any { it == "0/1" })
    }

    @Test
    fun portraitAndLandscapeSizesCombineIntoOneWidget() {
        // Лаунчер сообщает два размера (портрет и альбом) — система получает виджет под каждый.
        val views = runBlocking {
            WidgetUpdater.render(app, 1, listOf(DpSize(300.dp, 80.dp), DpSize(400.dp, 60.dp)))
        }
        val host = FrameLayout(app)
        host.addView(views.apply(app, host))
        assertTrue(texts(host).isNotEmpty())
    }

    @Test
    fun sizesFallBackToMinMaxOptions() {
        val options = android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 420)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 90)
        }
        val sizes = WidgetUpdater.sizesOf(options)
        assertTrue(sizes == listOf(DpSize(300.dp, 90.dp), DpSize(420.dp, 60.dp)))
        assertTrue(WidgetUpdater.sizesOf(null).size == 1)
    }

    @Test
    fun checkCircleMarksTaskDone() {
        val today = LocalDate.now()
        val taskId = runBlocking { app.container.repository.save(TaskDraft(title = "Полить цветы", date = today)) }
        TodayWidgetReceiver().onReceive(
            app,
            Intent(WidgetUpdater.ACTION_TOGGLE)
                .putExtra(WidgetUpdater.EXTRA_TASK_ID, taskId)
                .putExtra(WidgetUpdater.EXTRA_DAY, today.toEpochDay())
                .putExtra(WidgetUpdater.EXTRA_DONE, true),
        )
        val done = runBlocking {
            var result = false
            repeat(50) {
                result = app.container.repository.observeDay(today, today).first().done.any { it.taskId == taskId }
                if (result) return@repeat
                Thread.sleep(50)
            }
            result
        }
        assertTrue("задача не отмечена", done)
    }
}
