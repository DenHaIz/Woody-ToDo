package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woody.planner.data.SwipeStyle
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

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
 * «Классика» — подложка под уезжающей карточкой; «Капля» — из края карточки вытягивается
 * прозрачная капля воды и отрывается на пороге; «Книжка» — карточка перелистывается, как страница.
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

    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    // Живой сдвиг пальцем — обычное состояние, без корутины на каждое событие.
    var drag by remember { mutableFloatStateOf(0f) }
    val offset = { previewOffset?.let { it * threshold } ?: drag }

    // Действие, зафиксированное в момент отпускания. Пока карточка возвращается, подложка и иконка
    // не меняются, даже если у задачи уже сменились действия («Готово» → «Вернуть», «Удалить»).
    var locked by remember { mutableStateOf<SwipeAction?>(null) }
    var lockedSide by remember { mutableIntStateOf(0) }

    // «Капля»: дрожание оторвавшейся капли и всплеск после срабатывания.
    val wobble = remember { Animatable(0f) }
    val splash = remember { Animatable(0f) }
    var splashColor by remember { mutableStateOf(Color.Transparent) }
    var splashX by remember { mutableFloatStateOf(0f) }
    var splashRadius by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float, spec: AnimationSpec<Float>, after: suspend () -> Unit = {}) {
        settleJob?.cancel()
        val side = sign(drag)
        settleJob = scope.launch {
            animate(drag, target, animationSpec = spec) { value, _ ->
                // Возвращаясь, карточка не перелетает через ноль на другую сторону —
                // иначе на миг проступил бы цвет второго действия.
                drag = when {
                    target != 0f -> value
                    side > 0f -> value.coerceAtLeast(0f)
                    else -> value.coerceAtMost(0f)
                }
            }
            after()
            locked = null
            lockedSide = 0
        }
    }

    val startShown = if (lockedSide > 0) locked else startAction
    val endShown = if (lockedSide < 0) locked else endAction

    Box(
        modifier
            .onSizeChanged { cardSize = it }
            .drawWithContent {
                drawContent()
                val t = splash.value
                if (t > 0f && t < 1f) drawSplash(t, splashColor, splashX, splashRadius)
            },
    ) {
        // Подложка с действием
        Box(
            Modifier
                .matchParentSize()
                .then(if (style == SwipeStyle.Liquid) Modifier else Modifier.clip(CardShape))
                .drawBehind {
                    val v = offset()
                    if (v == 0f) return@drawBehind
                    val action = locked ?: (if (v > 0) start else end) ?: return@drawBehind
                    val p = (abs(v) / threshold).coerceIn(0f, 1.5f)
                    when (style) {
                        SwipeStyle.Classic -> drawOutsideCard(v) {
                            drawRoundRect(
                                action.color.copy(alpha = 0.25f + 0.75f * p.coerceAtMost(1f)),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                            )
                        }
                        // После срабатывания капля превращается во всплеск — саму каплю больше не рисуем.
                        SwipeStyle.Liquid -> if (locked == null || locked?.dismiss == true) {
                            drawOutsideCard(v) { drawWaterDrop(v, p, action.color, wobble.value) }
                        }
                        SwipeStyle.Book -> drawBookBackdrop(v, p, action.color)
                    }
                },
        ) {
            val released = { locked != null && locked?.dismiss == false }
            startShown?.let { ActionHint(it, style, threshold, Side.Start, released) { offset() } }
            endShown?.let { ActionHint(it, style, threshold, Side.End, released) { offset() } }
        }

        Box(
            Modifier
                .graphicsLayer {
                    val v = offset()
                    when (style) {
                        SwipeStyle.Classic -> translationX = v
                        SwipeStyle.Liquid -> {
                            translationX = v
                            // Карточка чуть «сплющивается», будто её держит поверхностное натяжение.
                            scaleY = 1f - 0.02f * (abs(v) / threshold).coerceIn(0f, 1f)
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
                            locked = null
                            lockedSide = 0
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
                            locked = action
                            lockedSide = direction.toInt()
                            if (action.dismiss) {
                                val target = direction * cardSize.width * if (style == SwipeStyle.Book) BookTurnFraction else 1f
                                settleTo(target, Motion.snappy()) {
                                    action.onTrigger()
                                    // Если элемент остался в списке (например, после отмены) — вернём его на место.
                                    delay(600)
                                    drag = 0f
                                }
                            } else {
                                if (style == SwipeStyle.Liquid) {
                                    splashColor = action.color
                                    splashX = if (v > 0) v / 2f else cardSize.width + v / 2f
                                    splashRadius = dropRadius(abs(v), cardSize.height.toFloat())
                                    scope.launch {
                                        splash.snapTo(0f)
                                        splash.animateTo(1f, tween(620, easing = LinearEasing))
                                        splash.snapTo(0f)
                                    }
                                }
                                action.onTrigger()
                                // «Книжка» мягко ложится обратно почти без отскока, остальные — пружиной.
                                val spec = if (style == SwipeStyle.Book) spring<Float>(dampingRatio = 0.7f, stiffness = 420f) else Motion.bouncy()
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
                            val limit = cardSize.width * BookTurnFraction * 0.92f
                            next = next.coerceIn(-limit, limit)
                        }
                        val nowArmed = available != null && abs(next) >= threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.performHapticFeedback(
                                if (nowArmed) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick,
                            )
                            // Капля отрывается от карточки и дрожит.
                            if (nowArmed && style == SwipeStyle.Liquid) {
                                scope.launch {
                                    wobble.snapTo(0f)
                                    wobble.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 520f), initialVelocity = 7f)
                                }
                            }
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

/** Радиус капли: вписывается в зазор между краем и карточкой, но не больше трети высоты. */
private fun Density.dropRadius(gap: Float, height: Float): Float =
    min((gap - 10.dp.toPx()) / 2f, height * 0.34f).coerceAtLeast(0f)

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private enum class Side { Start, End }

/** Иконка действия на подложке. Трансформации — на самой иконке, вокруг её центра. */
@Composable
private fun ActionHint(
    action: SwipeAction,
    style: SwipeStyle,
    threshold: Float,
    side: Side,
    released: () -> Boolean,
    offset: () -> Float,
) {
    val density = LocalDensity.current
    val edge = with(density) { 22.dp.toPx() }
    val iconHalf = with(density) { 12.dp.toPx() }
    val liquid = style == SwipeStyle.Liquid
    Layout(
        content = {
            if (liquid) {
                // В капле — только значок цвета действия: капля прозрачная, белый на ней не читается.
                Icon(action.icon, contentDescription = action.label, tint = action.color, modifier = Modifier.size(24.dp))
            } else {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(action.label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
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
                        // Значок плавает в центре капли и исчезает вместе с ней во всплеске.
                        if (released()) alpha = 0f
                        val shift = (v / 2f - edge - iconHalf).coerceAtLeast(0f)
                        translationX = if (side == Side.Start) shift else -shift
                        val r = dropRadius(v.coerceAtLeast(0f), constraints.maxHeight.toFloat())
                        val s = (r / (constraints.maxHeight * 0.34f)).coerceIn(0f, 1f) * 0.9f + pop
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

/**
 * Капля воды в зазоре между исходным местом карточки и её краем.
 * Пока порог не пройден, капля тянется к карточке перешейком; на пороге перешеек
 * истончается и рвётся, а оторвавшаяся капля дрожит ([wobble]).
 */
private fun DrawScope.drawWaterDrop(v: Float, p: Float, color: Color, wobble: Float) {
    val gap = abs(v)
    val r = dropRadius(gap, size.height)
    if (r < 3.dp.toPx()) return
    val fromStart = v > 0
    fun x(value: Float) = if (fromStart) value else size.width - value
    val cy = size.height / 2f
    val attached = 1f - smoothstep(0.7f, 1f, p)
    val rx = r * (1f + 0.10f * attached + 0.12f * wobble)
    val ry = r * (1f - 0.05f * attached - 0.10f * wobble)
    val cxRel = gap / 2f
    val cx = x(cxRel)

    val body = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    val drop = if (attached > 0.02f) {
        // Перешеек к краю карточки: широкий у карточки, тонкая «талия», затем капля.
        val angle = 55f * PI.toFloat() / 180f
        val tx = cxRel + rx * cos(angle)
        val ty = ry * sin(angle)
        val edgeHalf = r * 0.9f * attached
        val waist = r * 0.32f * attached
        val mid = (gap + tx) / 2f
        val neck = Path().apply {
            moveTo(x(gap + 2f), cy - edgeHalf)
            quadraticTo(x(mid), cy - waist, x(tx), cy - ty)
            lineTo(x(tx), cy + ty)
            quadraticTo(x(mid), cy + waist, x(gap + 2f), cy + edgeHalf)
            close()
        }
        Path.combine(PathOperation.Union, body, neck)
    } else {
        body
    }

    // Вода прозрачная: в центре почти бесцветная, к краю плотнее.
    drawPath(
        drop,
        Brush.radialGradient(
            0f to color.copy(alpha = 0.08f),
            0.6f to color.copy(alpha = 0.2f),
            1f to color.copy(alpha = 0.5f),
            center = Offset(cx, cy + r * 0.12f),
            radius = r * 1.05f,
        ),
    )
    drawPath(drop, color.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx()))
    // Блики: дуга сверху, искорка и слабое отражение снизу.
    val arcBox = Size(rx * 1.36f, ry * 1.36f)
    drawArc(
        Color.White.copy(alpha = 0.75f),
        startAngle = 200f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(cx - arcBox.width / 2f, cy - arcBox.height / 2f),
        size = arcBox,
        style = Stroke(width = r * 0.13f, cap = StrokeCap.Round),
    )
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.075f, center = Offset(cx + rx * 0.36f, cy - ry * 0.5f))
    val lowBox = Size(rx * 1.56f, ry * 1.56f)
    drawArc(
        Color.White.copy(alpha = 0.25f),
        startAngle = 25f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(cx - lowBox.width / 2f, cy - lowBox.height / 2f),
        size = lowBox,
        style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
    )
}

/**
 * Всплеск после срабатывания «Капли»: капля лопается — по карточке расходятся круги,
 * а мелкие брызги разлетаются и падают.
 */
private fun DrawScope.drawSplash(t: Float, color: Color, cx: Float, r0: Float) {
    val cy = size.height / 2f
    val fade = 1f - t
    val ease = 1f - (1f - t) * (1f - t) * (1f - t)
    val card = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(24.dp.toPx())))
    }
    clipPath(card) {
        val wash = r0 + ease * size.width * 0.7f
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f * fade), color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = wash.coerceAtLeast(1f),
            ),
            radius = wash,
            center = Offset(cx, cy),
        )
        for (ring in 0..1) {
            val local = ((t - ring * 0.18f) / (1f - ring * 0.18f)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val e = 1f - (1f - local) * (1f - local) * (1f - local)
            drawCircle(
                color.copy(alpha = 0.45f * (1f - local)),
                radius = r0 + e * size.height * (1.3f - ring * 0.4f),
                center = Offset(cx, cy),
                style = Stroke(width = 2.5.dp.toPx() * (1f - local) + 0.5f),
            )
        }
    }
    // Брызги: веером в стороны, с лёгким падением.
    val count = 8
    val reach = 30.dp.toPx()
    for (i in 0 until count) {
        val angle = (2f * PI.toFloat() * i / count) + 0.35f
        val distance = r0 * 0.7f + ease * reach * (0.75f + 0.25f * ((i * 7) % 3))
        val px = cx + cos(angle) * distance
        val py = cy + sin(angle) * distance * 0.75f + t * t * 14.dp.toPx()
        drawCircle(color.copy(alpha = 0.65f * fade), radius = 3.dp.toPx() * (1f - 0.6f * t), center = Offset(px, py))
    }
}

/** Подложка «Книжки»: цвет действия с мягкой тенью от поднимающейся страницы у петли. */
private fun DrawScope.drawBookBackdrop(v: Float, p: Float, color: Color) {
    val turn = bookTurn(v, size.width)
    // Около нуля цвет почти прозрачный — страница ложится без цветной вспышки.
    drawRoundRect(color.copy(alpha = (p * 1.3f).coerceAtMost(1f)), cornerRadius = CornerRadius(24.dp.toPx()))
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
