package com.woody.planner.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.woody.planner.WoodyApp
import com.woody.planner.data.DraftSubtask
import com.woody.planner.data.TaskDraft
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.RecurrenceType
import com.woody.planner.ui.WoodyAppShell
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

/**
 * Снимки экранов на JVM (без эмулятора). Запуск: gradlew testDebugUnitTest -Pscreenshots
 * Картинки появляются в app/build/outputs/roborazzi.
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
                recurrence = Recurrence(
                    RecurrenceType.WEEKLY,
                    daysMask = Recurrence.maskOf(*DayOfWeek.entries.toTypedArray()),
                ),
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
        val vitamins = repo.save(
            TaskDraft(
                title = "Выпить витамины",
                categoryId = 5,
                date = today.minusDays(3),
                time = LocalTime.of(9, 0),
                recurrence = Recurrence(RecurrenceType.DAILY),
            ),
        )
        repo.setDone(vitamins, today, true)
        repo.save(TaskDraft(title = "Оплатить интернет", categoryId = 3, date = today.minusDays(1)))
        repo.save(TaskDraft(title = "Разобрать почту", categoryId = 4, date = today))
    }

    private fun render(dark: Boolean) {
        compose.setContent {
            WoodyTheme(darkTheme = dark) {
                WoodyAppShell(container, openDayRequest = null, onOpenDayHandled = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun dayLight() {
        seed()
        render(dark = false)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_light.png")
        compose.onNodeWithText("Купить курицу").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_light_expanded.png")
    }

    @Test
    fun dayDark() {
        seed()
        render(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day_dark.png")
    }

    @Test
    fun editor() {
        seed()
        render(dark = false)
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/editor_light.png")
    }

    @Test
    fun monthAndCategories() {
        seed()
        render(dark = false)
        compose.onNodeWithText("Сегодня").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/month_light.png")
        compose.onNodeWithText("Категории").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/categories_light.png")
    }

    @Test
    fun settingsDark() {
        seed()
        render(dark = true)
        compose.onNodeWithText("Настройки").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_dark.png")
    }

    @Test
    fun emptyDayDark() {
        render(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/empty_dark.png")
    }
}
