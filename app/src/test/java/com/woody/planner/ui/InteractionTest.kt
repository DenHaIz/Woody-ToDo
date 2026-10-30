package com.woody.planner.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.woody.planner.WoodyApp
import com.woody.planner.data.SwipeStyle
import com.woody.planner.data.TaskDraft
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Проверка жестов на настоящем UI: свайпы карточек во всех стилях, закрытие листа, удержание «+». */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class InteractionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<WoodyApp>().container

    private fun start(style: SwipeStyle = SwipeStyle.Classic) {
        runBlocking {
            container.settings.setSwipeStyle(style)
            container.settings.setMenuHintSeen()
            val today = LocalDate.now()
            container.repository.save(TaskDraft(title = "Купить курицу", date = today))
            container.repository.save(TaskDraft(title = "Разобрать почту", date = today))
        }
        compose.setContent {
            WoodyTheme(darkTheme = false) {
                WoodyAppShell(container, openDayRequest = null, onOpenDayHandled = {})
            }
        }
        compose.waitForIdle()
    }

    /** Действия пишут в базу в фоновом потоке — ждём результат, а не только окончания анимаций. */
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun swipeDoneAndPostpone(style: SwipeStyle) {
        start(style)
        // Вправо — выполнено.
        compose.onNodeWithText("Купить курицу").performTouchInput {
            swipeRight(startX = left + 40f, endX = left + width * 0.7f, durationMillis = 300)
        }
        compose.waitForIdle()
        awaitText("ВЫПОЛНЕНО · 1")
        // Влево — на завтра (с плашкой «Отменить»).
        compose.onNodeWithText("Разобрать почту").performTouchInput {
            swipeLeft(startX = right - 40f, endX = right - width * 0.7f, durationMillis = 300)
        }
        compose.waitForIdle()
        awaitText("Перенесено на завтра")
    }

    @Test
    fun classicSwipe() = swipeDoneAndPostpone(SwipeStyle.Classic)

    @Test
    fun liquidSwipe() = swipeDoneAndPostpone(SwipeStyle.Liquid)

    @Test
    fun bookSwipe() = swipeDoneAndPostpone(SwipeStyle.Book)

    @Test
    fun shortSwipeDoesNothing() {
        start()
        compose.onNodeWithText("Купить курицу").performTouchInput {
            swipeRight(startX = left + 40f, endX = left + 120f, durationMillis = 300)
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("ВЫПОЛНЕНО · 1").assertCountEquals(0)
    }

    @Test
    fun sheetClosesBySwipeDownFromMiddle() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Новая задача").assertExists()
        compose.onNodeWithText("Напоминание").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 250)
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Новая задача").assertCountEquals(0)
    }

    @Test
    fun shortSheetDragSpringsBack() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Напоминание").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 60f, durationMillis = 600)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Новая задача").assertExists()
    }

    @Test
    fun longPressPlusOpensSettings() {
        start()
        compose.onNodeWithContentDescription("Добавить").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("АНИМАЦИЯ СВАЙПА").assertExists()
    }

    @Test
    fun editorOpensWithoutFocusAndKeyboard() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).onFirst().assertIsNotFocused()
        // Фокус — только по нажатию на поле.
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
    }

    @Test
    fun tileOpensPopupAndAppliesValue() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Категория").performClick()
        compose.waitForIdle()
        // Окно вырастает из плитки: заголовок окна + подпись плитки.
        compose.onAllNodesWithText("Категория").assertCountEquals(2)
        awaitText("Спорт")
        compose.onNodeWithText("Спорт").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Категория").assertCountEquals(1)
        compose.onNodeWithText("Спорт").assertExists()
    }

    @Test
    fun settingsShowRecentCategoriesAndExpand() {
        start()
        compose.onNodeWithContentDescription("Добавить").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("КАТЕГОРИИ").assertExists()
        // Из пяти стандартных категорий сначала видны три.
        compose.onAllNodesWithText("Здоровье").assertCountEquals(0)
        awaitText("Все категории · 5")
        compose.onNodeWithText("Все категории · 5").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Здоровье").assertExists()
        compose.onNodeWithText("Свернуть").assertExists()
    }
}
