package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.woody.planner.ui.theme.Brand
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Выбранная в настройках анимация свайпа. */
val LocalSwipeStyle = staticCompositionLocalOf { SwipeStyle.Classic }

data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    /** true — карточка улетает за край (задача уходит из списка), false — возвращается на место. */
    val dismiss: Boolean,
    val onTrigger: () -> Unit,
)

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Карточка со свайпами в обе стороны. Жест общий, а отрисовка зависит от [LocalSwipeStyle]:
 * классическая подложка, жидкая капля или 3D-поворот с салютом.
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

    var width by remember { mutableIntStateOf(0) }
    // Живой сдвиг пальцем — обычное состояние без корутин на каждое событие.
    var drag by remember { mutableFloatStateOf(0f) }
    val offset = { previewOffset?.let { it * threshold } ?: drag }
    val burst = remember { Animatable(0f) }
    val underlay = WoodyTheme.colors.background
    val splash = remember { Animatable(0f) }
    var burstColor by remember { mutableStateOf(Color.Transparent) }
    var splashFromStart by remember { mutableStateOf(true) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun animateTo(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>, after: suspend () -> Unit = {}) {
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
                // Салют (стиль «Объём») и всплеск (стиль «Капля») — поверх карточки.
                if (burst.value in 0.001f..0.999f) drawBurst(burst.value, burstColor)
                if (splash.value in 0.001f..0.999f) clipPath(Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(24.dp.toPx())))
                }) { drawSplash(splash.value, burstColor, splashFromStart) }
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
                        SwipeStyle.Depth -> drawDepthBackdrop(v, p, action.color)
                    }
                },
        ) {
            start?.let { action ->
                ActionHint(action, style, threshold, Alignment.Start) { offset() }
            }
            end?.let { action ->
                ActionHint(action, style, threshold, Alignment.End) { offset() }
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    val v = offset()
                    val p = (abs(v) / threshold).coerceIn(0f, 1.5f)
                    when (style) {
                        SwipeStyle.Classic -> translationX = v
                        SwipeStyle.Liquid -> {
                            translationX = v
                            // Карточка чуть «сплющивается», будто её толкает жидкость.
                            scaleY = 1f - 0.025f * p.coerceAtMost(1f)
                        }
                        SwipeStyle.Depth -> {
                            val w = max(size.width, 1f)
                            translationX = v * 0.92f
                            cameraDistance = 14f * density.density
                            transformOrigin = TransformOrigin(if (v > 0) 1f else 0f, 0.5f)
                            rotationY = -(v / w) * 38f
                            val s = 1f - 0.05f * p.coerceAtMost(1f)
                            scaleX = s
                            scaleY = s
                            shadowElevation = 14.dp.toPx() * p.coerceAtMost(1f)
                            shape = CardShape
                            clip = false
                            alpha = 1f - ((abs(v) - w * 0.6f) / (w * 0.6f)).coerceIn(0f, 1f)
                        }
                    }
                }
                .drawBehind {
                    // «Объём»: в движении карточка становится плотной, чтобы подложка не просвечивала сквозь 3D-поворот.
                    if (style == SwipeStyle.Depth) {
                        val a = (abs(offset()) / threshold * 4f).coerceIn(0f, 1f)
                        if (a > 0f) drawRoundRect(underlay.copy(alpha = a), cornerRadius = CornerRadius(24.dp.toPx()))
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
                                animateTo(0f, Motion.bouncy())
                                return@detectHorizontalDragGestures
                            }
                            val direction = sign(v)
                            if (style == SwipeStyle.Depth && !action.dismiss) {
                                burstColor = action.color
                                scope.launch {
                                    burst.snapTo(0f)
                                    burst.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                                }
                            }
                            if (style == SwipeStyle.Liquid && !action.dismiss) {
                                burstColor = action.color
                                splashFromStart = v > 0
                                scope.launch {
                                    splash.snapTo(0f)
                                    splash.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                                    splash.snapTo(0f)
                                }
                            }
                            if (action.dismiss) {
                                val target = direction * width * if (style == SwipeStyle.Depth) 1.25f else 1f
                                animateTo(target, Motion.snappy()) {
                                    action.onTrigger()
                                    // Если элемент остался в списке (например, после отмены) — вернём его на место.
                                    delay(700)
                                    drag = 0f
                                }
                            } else {
                                action.onTrigger()
                                animateTo(0f, Motion.bouncy())
                            }
                        },
                        onDragCancel = { animateTo(0f, Motion.bouncy()) },
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
                        val next = current + dragAmount * resistance
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

private enum class Alignment { Start, End }

/** Иконка действия на подложке. Положение и масштаб зависят от стиля. */
@Composable
private fun ActionHint(
    action: SwipeAction,
    style: SwipeStyle,
    threshold: Float,
    side: Alignment,
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
            val x = if (side == Alignment.Start) edge.toInt() else constraints.maxWidth - edge.toInt() - placeable.width
            placeable.placeWithLayer(x, (constraints.maxHeight - placeable.height) / 2) {
                val raw = offset()
                val v = if (side == Alignment.Start) raw else -raw
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
                        translationX = if (side == Alignment.Start) shift else -shift
                        val s = 0.5f + 0.5f * p.coerceAtMost(1f) + pop * 1.5f
                        scaleX = s
                        scaleY = s
                    }
                    SwipeStyle.Depth -> {
                        // Параллакс и докрутка иконки на место.
                        val shift = v * 0.18f
                        translationX = if (side == Alignment.Start) shift else -shift
                        rotationZ = (1f - p.coerceAtMost(1f)) * if (side == Alignment.Start) -120f else 120f
                        val s = 0.7f + 0.3f * p.coerceAtMost(1f) + pop
                        scaleX = s
                        scaleY = s
                    }
                }
            }
        }
    }
}

/**
 * Рисует [block] строго вне сдвинутой карточки: карточки полупрозрачные, и подложка
 * не должна просвечивать сквозь них. [scale] — масштаб карточки (стиль «Объём»).
 */
private inline fun DrawScope.drawOutsideCard(v: Float, scale: Float = 1f, block: DrawScope.() -> Unit) {
    val w = size.width * scale
    val h = size.height * scale
    val left = v + (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val radius = 24.dp.toPx() * scale
    val card = Path().apply {
        addRoundRect(RoundRect(left, top, left + w, top + h, CornerRadius(radius)))
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
    drawCircle(
        color.copy(alpha = 0.7f * fade),
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx() * fade + 1f),
    )
}

/** Глубокая подложка: градиент от края и мягкая «тень» за поворачивающейся карточкой. */
private fun DrawScope.drawDepthBackdrop(v: Float, p: Float, color: Color) {
    val fill = p.coerceAtMost(1f)
    val fromStart = v > 0
    val brush = Brush.horizontalGradient(
        colors = if (fromStart) {
            listOf(color, color.copy(alpha = 0.55f * fill + 0.2f))
        } else {
            listOf(color.copy(alpha = 0.55f * fill + 0.2f), color)
        },
    )
    drawRoundRect(brush, alpha = 0.35f + 0.65f * fill, cornerRadius = CornerRadius(24.dp.toPx()))
}

/** Салют из частиц от чекбокса при выполнении задачи. */
private fun DrawScope.drawBurst(progress: Float, color: Color) {
    val origin = Offset(34.dp.toPx(), size.height / 2f)
    val random = Random(42)
    val palette = listOf(color, Brand.Amber, Brand.Crimson, Brand.Blue, Color.White)
    val travel = 120.dp.toPx()
    repeat(22) { i ->
        val angle = (i / 22f) * 6.283f + random.nextFloat() * 0.4f
        val speed = 0.45f + random.nextFloat() * 0.55f
        val d = travel * speed * progress
        // Лёгкая «гравитация» — частицы опускаются к концу полёта.
        val center = Offset(origin.x + cos(angle) * d, origin.y + sin(angle) * d * 0.6f + 40.dp.toPx() * progress * progress)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val c = palette[i % palette.size].copy(alpha = alpha)
        val r = (2.5f + random.nextFloat() * 2.5f).dp.toPx() * (1f - progress * 0.4f)
        if (i % 3 == 0) {
            rotate(progress * 540f + i * 20f, center) {
                drawRect(c, topLeft = Offset(center.x - r, center.y - r * 0.5f), size = Size(r * 2, r))
            }
        } else {
            drawCircle(c, radius = r, center = center)
        }
    }
}
