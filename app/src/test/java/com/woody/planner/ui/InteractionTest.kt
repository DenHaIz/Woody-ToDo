package com.woody.planner.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
import com.woody.planner.data.AppLanguage
import com.woody.planner.data.SwipeStyle
import com.woody.planner.ui.i18n.LocalLanguage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.activity.compose.setContent
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
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
        compose.setContent { App() }
        compose.waitForIdle()
        // Список читается из базы в фоне — ждём, пока задачи появятся на экране.
        awaitText("Купить курицу")
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        // Как в MainActivity: язык берётся из настроек и меняется на лету.
        val lang by container.settings.language.collectAsStateWithLifecycle(AppLanguage.Russian)
        CompositionLocalProvider(LocalLanguage provides lang) {
            WoodyTheme(darkTheme = false) {
                WoodyAppShell(container, openDayRequest = null, onOpenDayHandled = {})
            }
        }
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
    fun everyTileOpensItsWindowOnNewTask() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        // Сначала печатаем название — как обычно и делают — затем по очереди открываем все параметры.
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Спортзал")
        compose.waitForIdle()
        listOf("Дата", "Время", "Категория", "Напоминание", "Повтор", "Чек-лист").forEach { title ->
            if (compose.onAllNodesWithText("Дополнительно").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("Дополнительно").performClick()
                compose.waitForIdle()
            }
            compose.onNodeWithContentDescription(title).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("param-window").assertExists()
            compose.onAllNodesWithContentDescription("Закрыть").onLast().performClick()
            compose.waitForIdle()
            compose.onAllNodesWithTag("param-window").assertCountEquals(0)
        }
    }

    @Test
    fun typingCollapsesExtraTilesIntoStrip() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Повтор").assertExists()
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.waitForIdle()
        // При вводе текста остаются дата, время и категория; остальное — за полоской «Дополнительно».
        compose.onNodeWithContentDescription("Дата").assertExists()
        compose.onAllNodesWithContentDescription("Повтор").assertCountEquals(0)
        compose.onNodeWithText("Дополнительно").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Повтор").assertExists()
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

    @Test
    fun languageSwitchesWholeInterfaceAtOnce() {
        start()
        compose.onNodeWithContentDescription("Добавить").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("English").performScrollTo().performClick()
        awaitText("Settings")
        compose.onNodeWithText("LANGUAGE").assertExists()
        compose.onNodeWithText("CATEGORIES").assertExists()
        // Нетронутые стартовые категории переведены вместе с интерфейсом.
        awaitText("Sport")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        // День за настройками тоже уже на английском.
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New task").assertExists()
        compose.onNodeWithContentDescription("Reminder").assertExists()
        runBlocking { container.settings.setLanguage(AppLanguage.Russian) }
    }

    @Test
    fun alarmModeToggleReschedules() {
        start()
        compose.onNodeWithContentDescription("Добавить").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Как будильник").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { !container.settings.alarmClockReminders.first() } }
        awaitText("Обычный точный будильник, без значка")
        compose.onNodeWithText("Как будильник").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.alarmClockReminders.first() } }
    }

    /**
     * Сценарий из жалобы: набрали название, нажали плитку — текст не должен стираться, окно должно
     * открываться, а после закрытия окон весь экран должен оставаться нажимаемым.
     */
    @Test
    fun typedTitleSurvivesTileWindowsAndScreenRebuild() {
        start()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Купить хлеб")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Время").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("param-window").assertExists()
        compose.onNodeWithText("13:00").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("param-window").assertCountEquals(0)
        compose.onNodeWithText("Купить хлеб").assertExists()

        // Система пересоздала экран (поворот, нехватка памяти) — набранное и выбранное на месте.
        compose.runOnUiThread { compose.activity.setContent { App() } }
        compose.waitForIdle()
        compose.onNodeWithText("Купить хлеб").assertExists()
        compose.onNodeWithText("13:00").assertExists()

        // Окна снова открываются, фон после них не перехватывает касания.
        compose.onNodeWithContentDescription("Дата").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Завтра").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Купить хлеб").assertExists()
        compose.onNodeWithText("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Добавить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Новая задача").assertExists()
        // Новое открытие — чистый черновик.
        compose.onAllNodes(hasSetTextAction() and hasText("Купить хлеб")).assertCountEquals(0)
    }

    @Test
    fun doubleTapOnEmptySpaceOpensNewTask() {
        start()
        val empty = { w: Float, h: Float -> androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.7f) }
        // Одно касание по пустому месту ничего не делает.
        compose.onRoot().performTouchInput { click(empty(width.toFloat(), height.toFloat())) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Новая задача").assertCountEquals(0)
        // Пустое место под задачами, слева от кнопки «+».
        compose.onRoot().performTouchInput { doubleClick(empty(width.toFloat(), height.toFloat())) }
        compose.waitForIdle()
        compose.onNodeWithText("Новая задача").assertExists()
    }
}
