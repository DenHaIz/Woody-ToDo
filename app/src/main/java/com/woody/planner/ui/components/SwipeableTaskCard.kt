package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.woody.planner.data.SwipeStyle
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Выбранная в настройках анимация свайпа. */
val LocalSwipeStyle = staticCompositionLocalOf { SwipeStyle.Classic }

data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    /** true — карточка уходит (задача пропадает из списка), false — возвращается на место. */
    val dismiss: Boolean,
    val onTrigger: () -> Unit,
)

private val CardShape = RoundedCornerShape(24.dp)

/** Доля ширины, на которой страница «Книжки» встаёт ребром (90°). */
private const val BookTurnFraction = 0.6f

/**
 * Карточка со свайпами в обе стороны. Жест общий, отрисовка зависит от [style]:
 * «Классика» — подложка под уезжающей карточкой; «Капля» — цвет вытекает жидкой каплей;
 * «Книжка» — карточка перелистывается вокруг дальнего края, как страница.
 * [previewOffset] фиксирует сдвиг (доля порога) — для превью и скриншотов.
 */
@Composable
fun SwipeableTaskCard(
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    style: SwipeStyle = LocalSwipeStyle.current,
    previewOffset: Float? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 88.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val start by rememberUpdatedState(startAction)
    val end by rememberUpdatedState(endAction)
    val underlay = WoodyTheme.colors.background

    var width by remember { mutableIntStateOf(0) }
    // Живой сдвиг пальцем — обычное состояние, без корутины на каждое событие.
    var drag by remember { mutableFloatStateOf(0f) }
    val offset = { previewOffset?.let { it * threshold } ?: drag }
    val splash = remember { Animatable(0f) }
    var splashColor by remember { mutableStateOf(Color.Transparent) }
    var splashFromStart by remember { mutableStateOf(true) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float, spec: AnimationSpec<Float>, after: suspend () -> Unit = {}) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(drag, target, animationSpec = spec) { value, _ -> drag = value }
            after()
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .drawWithContent {
                drawContent()
                // Всплеск «Капли» после срабатывания — световая волна по карточке.
                if (splash.value in 0.001f..0.999f) {
                    clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(24.dp.toPx()))) }) {
                        drawSplash(splash.value, splashColor, splashFromStart)
                    }
                }
            },
    ) {
        // Подложка с действием
        Box(
            Modifier
                .matchParentSize()
                .clip(CardShape)
                .drawBehind {
                    val v = offset()
                    val action = if (v > 0) start else end
                    if (action == null || v == 0f) return@drawBehind
                    val p = (abs(v) / threshold).coerceIn(0f, 1.5f)
                    when (style) {
                        SwipeStyle.Classic -> drawOutsideCard(v) {
                            drawRoundRect(
                                action.color.copy(alpha = 0.25f + 0.75f * p.coerceAtMost(1f)),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                            )
                        }
                        SwipeStyle.Liquid -> drawOutsideCard(v) { drawLiquid(v, p, action.color) }
                        SwipeStyle.Book -> drawBookBackdrop(v, p, action.color)
                    }
                },
        ) {
            start?.let { ActionHint(it, style, threshold, Side.Start) { offset() } }
            end?.let { ActionHint(it, style, threshold, Side.End) { offset() } }
        }

        Box(
            Modifier
                .graphicsLayer {
                    val v = offset()
                    when (style) {
                        SwipeStyle.Classic -> translationX = v
                        SwipeStyle.Liquid -> {
                            translationX = v
                            // Карточка чуть «сплющивается», будто её толкает жидкость.
                            scaleY = 1f - 0.025f * (abs(v) / threshold).coerceIn(0f, 1f)
                        }
                        SwipeStyle.Book -> {
                            // Петля — у дальнего края: тянем вправо — лист переворачивается вокруг правого края.
                            val turn = bookTurn(v, size.width)
                            transformOrigin = TransformOrigin(if (v >= 0) 1f else 0f, 0.5f)
                            cameraDistance = 24f * density.density
                            rotationY = if (v >= 0) turn * 90f else -turn * 90f
                        }
                    }
                }
                .drawWithContent {
                    val v = offset()
                    if (style == SwipeStyle.Book && v != 0f) {
                        val turn = bookTurn(v, size.width)
                        // Плотная подложка, чтобы при повороте сквозь карточку не просвечивал цвет действия.
                        drawRoundRect(underlay.copy(alpha = (turn * 6f).coerceIn(0f, 1f)), cornerRadius = CornerRadius(24.dp.toPx()))
                        drawContent()
                        // Тень сгиба: темнее у свободного края страницы.
                        val freeEdgeAtStart = v > 0
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = if (freeEdgeAtStart) {
                                    listOf(Color.Black.copy(alpha = 0.16f * turn), Color.Transparent)
                                } else {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f * turn))
                                },
                            ),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                        )
                    } else {
                        drawContent()
                    }
                }
                .pointerInput(style) {
                    if (previewOffset != null) return@pointerInput
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            armed = false
                        },
                        onDragEnd = {
                            val v = drag
                            val action = if (v > 0) start else end
                            if (action == null || abs(v) < threshold) {
                                settleTo(0f, Motion.bouncy())
                                return@detectHorizontalDragGestures
                            }
                            val direction = sign(v)
                            if (action.dismiss) {
                                val target = direction * width * if (style == SwipeStyle.Book) BookTurnFraction else 1f
                                settleTo(target, Motion.snappy()) {
                                    action.onTrigger()
                                    // Если элемент остался в списке (например, после отмены) — вернём его на место.
                                    delay(600)
                                    drag = 0f
                                }
                            } else {
                                if (style == SwipeStyle.Liquid) {
                                    splashColor = action.color
                                    splashFromStart = v > 0
                                    scope.launch {
                                        splash.snapTo(0f)
                                        splash.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                                        splash.snapTo(0f)
                                    }
                                }
                                action.onTrigger()
                                // «Книжка» шлёпается обратно с лёгким отскоком, остальные — пружиной.
                                val spec = if (style == SwipeStyle.Book) spring<Float>(dampingRatio = 0.45f, stiffness = 380f) else Motion.bouncy()
                                settleTo(0f, spec)
                            }
                        },
                        onDragCancel = { settleTo(0f, Motion.bouncy()) },
                    ) { change, dragAmount ->
                        change.consume()
                        val current = drag
                        val available = if (current + dragAmount > 0) start else end
                        // Без действия в эту сторону — тугая резина; за порогом — мягкое сопротивление.
                        val resistance = when {
                            available == null -> 0.15f
                            abs(current) > threshold -> 0.35f
                            else -> 1f
                        }
                        var next = current + dragAmount * resistance
                        // Страница не переворачивается дальше, чем «ребром».
                        if (style == SwipeStyle.Book) {
                            val limit = width * BookTurnFraction * 0.92f
                            next = next.coerceIn(-limit, limit)
                        }
                        val nowArmed = available != null && abs(next) >= threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.performHapticFeedback(
                                if (nowArmed) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick,
                            )
                        }
                        drag = next
                    }
                },
        ) {
            content()
        }
    }
}

/** Доля переворота страницы 0..1 (1 — ребром). */
private fun bookTurn(v: Float, width: Float): Float =
    (abs(v) / (width.coerceAtLeast(1f) * BookTurnFraction)).coerceIn(0f, 1f)

private enum class Side { Start, End }

/** Иконка действия на подложке. Трансформации — на самой иконке, вокруг её центра. */
@Composable
private fun ActionHint(
    action: SwipeAction,
    style: SwipeStyle,
    threshold: Float,
    side: Side,
    offset: () -> Float,
) {
    val density = LocalDensity.current
    val edge = with(density) { 22.dp.toPx() }
    val iconHalf = with(density) { 14.dp.toPx() }
    Layout(
        content = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(action.label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = if (side == Side.Start) edge.toInt() else constraints.maxWidth - edge.toInt() - placeable.width
            placeable.placeWithLayer(x, (constraints.maxHeight - placeable.height) / 2) {
                val raw = offset()
                val v = if (side == Side.Start) raw else -raw
                val p = (v / threshold).coerceIn(0f, 1.3f)
                alpha = (p * 1.4f).coerceIn(0f, 1f)
                val pop = if (p >= 1f) 0.1f else 0f
                when (style) {
                    SwipeStyle.Classic -> {
                        val s = 0.6f + 0.4f * p.coerceAtMost(1f) + pop
                        scaleX = s
                        scaleY = s
                    }
                    SwipeStyle.Liquid -> {
                        // Иконка «плывёт» в центре капли.
                        val shift = (v / 2f - edge - iconHalf).coerceAtLeast(0f)
                        translationX = if (side == Side.Start) shift else -shift
                        val s = 0.5f + 0.5f * p.coerceAtMost(1f) + pop * 1.5f
                        scaleX = s
                        scaleY = s
                    }
                    SwipeStyle.Book -> {
                        // Иконка «выглядывает» из-под поднимающейся страницы.
                        val s = 0.7f + 0.3f * p.coerceAtMost(1f) + pop
                        scaleX = s
                        scaleY = s
                        translationX = (1f - p.coerceAtMost(1f)) * if (side == Side.Start) -edge else edge
                    }
                }
            }
        }
    }
}

/**
 * Рисует [block] строго вне сдвинутой карточки: карточки полупрозрачные, и подложка
 * не должна просвечивать сквозь них.
 */
private inline fun DrawScope.drawOutsideCard(v: Float, block: DrawScope.() -> Unit) {
    val card = Path().apply {
        addRoundRect(RoundRect(v, 0f, v + size.width, size.height, CornerRadius(24.dp.toPx())))
    }
    clipPath(card, ClipOp.Difference) { block() }
}

/** Жидкая капля, вытекающая из края. */
private fun DrawScope.drawLiquid(v: Float, p: Float, color: Color) {
    val h = size.height
    val fromStart = v > 0
    val reach = abs(v) + 2.dp.toPx() * p.coerceAtMost(1f)
    val fill = p.coerceAtMost(1f)
    // Чем дальше палец, тем «полнее» капля: от узкого языка до почти прямоугольника.
    val shoulder = h * (0.5f - 0.42f * fill)
    val path = Path().apply {
        fun x(value: Float) = if (fromStart) value else size.width - value
        moveTo(x(0f), 0f)
        cubicTo(x(reach * 0.55f), 0f, x(reach), shoulder, x(reach), h / 2f)
        cubicTo(x(reach), h - shoulder, x(reach * 0.55f), h, x(0f), h)
        close()
    }
    drawPath(path, color.copy(alpha = 0.55f + 0.45f * fill))
}

/** Всплеск после срабатывания «Капли»: световая волна расходится от края по карточке. */
private fun DrawScope.drawSplash(progress: Float, color: Color, fromStart: Boolean) {
    val center = Offset(if (fromStart) 0f else size.width, size.height / 2f)
    val radius = size.width * 1.1f * progress
    val fade = 1f - progress
    drawCircle(color.copy(alpha = 0.18f * fade), radius = radius, center = center)
    drawCircle(color.copy(alpha = 0.7f * fade), radius = radius, center = center, style = Stroke(width = 10.dp.toPx() * fade + 1f))
}

/** Подложка «Книжки»: цвет действия с мягкой тенью от поднимающейся страницы у петли. */
private fun DrawScope.drawBookBackdrop(v: Float, p: Float, color: Color) {
    val turn = bookTurn(v, size.width)
    drawRoundRect(color.copy(alpha = 0.35f + 0.65f * p.coerceAtMost(1f)), cornerRadius = CornerRadius(24.dp.toPx()))
    val hingeAtEnd = v > 0
    val shadowWidth = size.width * 0.35f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = if (hingeAtEnd) {
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f * turn))
            } else {
                listOf(Color.Black.copy(alpha = 0.14f * turn), Color.Transparent)
            },
            startX = if (hingeAtEnd) size.width - shadowWidth else 0f,
            endX = if (hingeAtEnd) size.width else shadowWidth,
        ),
    )
}
