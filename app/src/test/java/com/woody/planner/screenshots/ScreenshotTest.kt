package com.woody.planner.screenshots

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.woody.planner.WoodyApp
import com.woody.planner.data.DraftSubtask
import com.woody.planner.data.SwipeStyle
import com.woody.planner.data.TaskDraft
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.RecurrenceType
import com.woody.planner.domain.TaskItem
import com.woody.planner.ui.WoodyAppShell
import com.woody.planner.ui.components.SwipeAction
import com.woody.planner.ui.components.SwipeableTaskCard
import com.woody.planner.ui.day.TaskCard
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Снимки экранов на JVM (без эмулятора). Запуск: gradlew testDebugUnitTest -Pscreenshots
 * Картинки появляются в app/build/outputs/roborazzi. Статус-бар и навигация имитируются отступами,
 * как на реальном телефоне.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<WoodyApp>().container

    private fun seed() = runBlocking {
        val repo = container.repository
        val today = LocalDate.now()
        repo.save(
            TaskDraft(
                title = "Спортзал",
                categoryId = 1,
                date = today.minusDays(7),
                time = LocalTime.of(19, 0),
                reminderOffsetMin = 30,
                recurrence = Recurrence(RecurrenceType.WEEKLY, daysMask = Recurrence.maskOf(*DayOfWeek.entries.toTypedArray())),
            ),
        )
        val shop = repo.save(
            TaskDraft(
                title = "Купить курицу",
                note = "Заехать после работы",
                categoryId = 2,
                date = today,
                subtasks = listOf(
                    DraftSubtask(-1, 0, "Куриное филе 1 кг"),
                    DraftSubtask(-2, 0, "Рис"),
                    DraftSubtask(-3, 0, "Помидоры"),
                ),
            ),
        )
        repo.loadDraft(shop)!!.subtasks.first().let { repo.setSubtaskDone(it.id, today, true) }
        val call = repo.save(TaskDraft(title = "Позвонить маме", date = today, time = LocalTime.of(12, 30)))
        repo.setDone(call, today, true)
        repo.save(TaskDraft(title = "Оплатить интернет", categoryId = 3, date = today.minusDays(1)))
        repo.save(TaskDraft(title = "Записаться к стоматологу на следующую неделю после обеда", categoryId = 5, date = today))
        repo.save(TaskDraft(title = "Разобрать почту", categoryId = 4, date = today))
    }

    private fun render(dark: Boolean, openDay: LocalDate? = null) {
        compose.setContent {
            WoodyTheme(darkTheme = dark) {
                WoodyAppShell(container, openDayRequest = openDay, onOpenDayHandled = {})
            }
        }
        // Статус-бар 40dp и жестовая панель 16dp, как на современном телефоне.
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 120, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .build()
            ViewCompat.dispatchApplyWindowInsets(content, insets)
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun dayLight() {
        seed()
        render(dark = false)
        shot("day_light")
        compose.onNodeWithText("Купить курицу").performClick()
        compose.waitForIdle()
        shot("day_light_expanded")
    }

    @Test
    fun sundayDark() {
        seed()
        render(dark = true, openDay = LocalDate.now().plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)))
        shot("sunday_dark")
    }

    @Test
    fun editor() {
        seed()
        render(dark = false)
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        shot("editor_light")
    }

    @Test
    fun month() {
        seed()
        render(dark = false)
        compose.onNodeWithText("Сегодня").performClick()
        compose.waitForIdle()
        shot("month_light")
    }

    @Test
    fun settingsByLongPress() {
        seed()
        render(dark = false)
        compose.onNodeWithContentDescription("Добавить").performTouchInput { longClick() }
        compose.waitForIdle()
        shot("settings_light")
    }

    @Test
    fun editorDark() {
        seed()
        render(dark = true)
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        shot("editor_dark")
    }

    /** Покадрово: палец тянет карточку, отпускает, анимация доигрывает. */
    private fun swipeFrames(style: SwipeStyle, name: String) {
        runBlocking { container.settings.setSwipeStyle(style) }
        seed()
        render(dark = false)
        compose.mainClock.autoAdvance = false
        val node = compose.onNodeWithText("Купить курицу")
        node.performTouchInput { down(androidx.compose.ui.geometry.Offset(left + 40f, centerY)) }
        repeat(12) {
            node.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(width * 0.05f, 0f)) }
            compose.mainClock.advanceTimeBy(16)
        }
        shot("${name}_1_drag")
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        shot("${name}_2_release")
        compose.mainClock.advanceTimeBy(200)
        shot("${name}_3_settle")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        shot("${name}_4_done")
    }

    @Test
    fun swipeFramesClassic() = swipeFrames(SwipeStyle.Classic, "frames_classic")

    @Test
    fun swipeFramesLiquid() = swipeFrames(SwipeStyle.Liquid, "frames_liquid")

    @Test
    fun swipeFramesBook() = swipeFrames(SwipeStyle.Book, "frames_book")

    @Test
    fun emptyDayDark() {
        render(dark = true)
        shot("empty_dark")
    }

    @Test
    fun swipeStyles() {
        compose.setContent {
            WoodyTheme(darkTheme = false) {
                val colors = WoodyTheme.colors
                val item = TaskItem(
                    taskId = 1, day = LocalDate.now(), title = "Спортзал", note = "", category = null,
                    time = LocalTime.of(19, 0), hasReminder = true, isRecurring = true, done = false, overdue = false,
                    subtasks = emptyList(),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SwipeStyle.entries.forEach { style ->
                        listOf(0.6f, 1.15f, -0.9f).forEach { offset ->
                            SwipeableTaskCard(
                                startAction = SwipeAction(WoodyIcons.Check, "Готово", colors.success, false) {},
                                endAction = SwipeAction(WoodyIcons.ArrowRight, "На завтра", colors.warning, true) {},
                                style = style,
                                previewOffset = offset,
                            ) {
                                TaskCard(item, onToggle = {}, onToggleSubtask = {}, onEdit = {})
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("swipe_styles")
    }
}
