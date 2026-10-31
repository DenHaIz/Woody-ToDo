package com.woody.planner.ui.day

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.woody.planner.domain.TaskItem
import com.woody.planner.ui.theme.Motion
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Перетаскивание задач: удержание поднимает карточку, дальше её можно двигать вверх-вниз
 * (порядок задач без времени) или бросить на день в ленте недели (перенос разовой задачи).
 * Удержание без движения — как раньше, открывает редактор.
 */
@Stable
class TaskDragState {
    /** Поднятая задача; null — ничего не тащат. */
    var item by mutableStateOf<TaskItem?>(null)
        private set

    /** Палец в координатах экрана. */
    var pointer by mutableStateOf(Offset.Unspecified)
        private set

    /** Где палец взял карточку — относительно её верхнего левого угла. */
    var grab by mutableStateOf(Offset.Zero)
        private set

    /** Нижний край шапки дня: выше него карточку заменяет плашка поверх шапки. */
    var headerBottom by mutableFloatStateOf(0f)

    /** Палец над шапкой (лентой недели) — карточка под стеклом не видна, показываем плашку. */
    val overHeader: Boolean
        get() = item != null && pointer != Offset.Unspecified && pointer.y < headerBottom

    /** Дни ленты недели, на которые можно бросить задачу (границы на экране). */
    val dayTargets = mutableStateMapOf<LocalDate, Rect>()

    /** День ленты под пальцем. */
    val hoverDay: LocalDate?
        get() {
            val p = pointer
            if (item == null || p == Offset.Unspecified) return null
            return dayTargets.entries.firstOrNull { it.value.contains(p) }?.key
        }

    fun isDragging(item: TaskItem) = this.item?.let { it.taskId == item.taskId && it.day == item.day } == true

    internal fun start(item: TaskItem, pointer: Offset, grab: Offset) {
        this.item = item
        this.pointer = pointer
        this.grab = grab
    }

    internal fun move(pointer: Offset) {
        this.pointer = pointer
    }

    internal fun end() {
        item = null
        pointer = Offset.Unspecified
    }
}

val LocalTaskDrag = staticCompositionLocalOf<TaskDragState?> { null }

/**
 * Жест на карточке. [onMove] — палец сдвинулся (для перестановки в списке), [onDrop] — отпустили
 * после перетаскивания (вернуть true, если задача перенесена на другой день), [onEdit] — удержали
 * и отпустили на месте.
 */
@Composable
fun Modifier.taskDrag(
    item: TaskItem,
    enabled: Boolean,
    onMove: (pointer: Offset) -> Unit,
    onDrop: (day: LocalDate?) -> Unit,
    onEdit: () -> Unit,
): Modifier {
    val state = LocalTaskDrag.current ?: return this
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val edit by rememberUpdatedState(onEdit)
    val current by rememberUpdatedState(item)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var top by remember { mutableFloatStateOf(0f) }
    var left by remember { mutableFloatStateOf(0f) }
    // Мягкое возвращение на место после отпускания.
    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val lifted = remember { Animatable(0f) }
    val dragging = state.isDragging(item)

    return this
        .zIndex(if (dragging || lifted.value > 0f) 1f else 0f)
        .onGloballyPositioned {
            coords = it
            val p = it.positionInRoot()
            top = p.y
            left = p.x
        }
        // Жест — до сдвига карточки: координаты пальца считаются в несдвинутой системе.
        .pointerInput(item.taskId, item.day, enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                long.consume()
                if (!enabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    edit()
                    // Дальше пальцем ничего не делаем: дочитываем жест до отпускания.
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                        if (e.changes.none { it.pressed }) break
                    }
                    return@awaitEachGesture
                }
                val c = coords ?: return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val startRoot = c.localToRoot(long.position)
                state.start(current, startRoot, long.position)
                scope.launch {
                    settle.snapTo(Offset.Zero)
                    lifted.animateTo(1f, Motion.bouncy())
                }
                var travelled = 0f
                var lastDay: LocalDate? = null
                while (true) {
                    // Initial: карточка забирает касания раньше свайпа, прокрутки и нажатия.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == long.id } ?: break
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) break
                    val root = coords?.localToRoot(change.position) ?: break
                    travelled += (root - state.pointer).getDistance()
                    state.move(root)
                    val day = state.hoverDay
                    if (day != lastDay) {
                        lastDay = day
                        if (day != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    if (day == null) move(root)
                }
                val wasMoved = travelled > viewConfiguration.touchSlop
                val target = state.hoverDay
                // Остаток сдвига — в пружину возврата, чтобы карточка доплыла на место, а не прыгнула.
                val rest = Offset(state.pointer.x - state.grab.x - left, state.pointer.y - state.grab.y - top)
                state.end()
                scope.launch {
                    settle.snapTo(rest)
                    launch { lifted.animateTo(0f, Motion.soft()) }
                    settle.animateTo(Offset.Zero, Motion.soft())
                }
                if (wasMoved) drop(target) else edit()
            }
        }
        .graphicsLayer {
            val live = if (state.isDragging(current) && state.pointer != Offset.Unspecified) {
                Offset(state.pointer.x - state.grab.x - left, state.pointer.y - state.grab.y - top)
            } else {
                settle.value
            }
            translationX = live.x
            translationY = live.y
            // Над шапкой вместо карточки — плашка поверх всего (DragGhost).
            alpha = if (state.isDragging(current) && state.overHeader) 0f else 1f
            val l = lifted.value
            // Над лентой недели карточка «уменьшается в руке» — видно, что её сейчас положат в день.
            val overDay = if (state.isDragging(current) && state.hoverDay != null) 0.12f else 0f
            val s = 1f + 0.04f * l - overDay
            scaleX = s
            scaleY = s
            shadowElevation = 18f * l * density
            shape = com.woody.planner.ui.theme.Shapes.Card
            clip = false
        }
}
