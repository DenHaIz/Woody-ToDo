package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import kotlin.math.max
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

/** «Книжка»: на сколько ширин карточки нужно протянуть, чтобы страница перевернулась целиком. */
private const val BookFullTurn = 2.3f

/** Страница «Книжки» перелистывается и ложится обратно плавно, без отскока. */
private val PageEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
private val BookReturn = spring<Float>(dampingRatio = 1f, stiffness = 240f)

/**
 * Карточка со свайпами в обе стороны. Жест общий, отрисовка зависит от [style]:
 * «Классика» — подложка под уезжающей карточкой; «Капля» — из края карточки вытягивается
 * прозрачная капля воды и отрывается на пороге; «Книжка» — карточка загибается и перелистывается,
 * как страница, от того места, за которое её потянули (верх, середина или низ края).
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
    val colors = WoodyTheme.colors
    // Оборот страницы — плотная бумага цвета карточки.
    val paper = colors.card.compositeOver(colors.background)
    val paperEdge = colors.cardBorder
    val pageLayer = rememberGraphicsLayer()

    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    // Живой сдвиг пальцем — обычное состояние, без корутины на каждое событие.
    var drag by remember { mutableFloatStateOf(0f) }
    val offset = { previewOffset?.let { it * threshold } ?: drag }
    // Высота, на которой взялись за карточку: от неё зависит, какой угол загибается в «Книжке».
    var grabY by remember { mutableFloatStateOf(Float.NaN) }
    val grab = { if (grabY.isNaN()) cardSize.height * 0.72f else grabY }
    // У «Книжки» порог длиннее: страница должна заметно загнуться, прежде чем перевернуться.
    val activeThreshold = if (style == SwipeStyle.Book && cardSize.width > 0) max(threshold, cardSize.width * 0.32f) else threshold

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
                    val p = (abs(v) / activeThreshold).coerceIn(0f, 1.5f)
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
                        SwipeStyle.Book -> pageFold(v, grab(), size)?.let { drawBookBackdrop(it, p, action.color) }
                    }
                },
        ) {
            val released = { locked != null && locked?.dismiss == false }
            startShown?.let { ActionHint(it, style, activeThreshold, Side.Start, released) { offset() } }
            endShown?.let { ActionHint(it, style, activeThreshold, Side.End, released) { offset() } }
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
                        // «Книжка» не двигает карточку целиком — страница загибается при отрисовке.
                        SwipeStyle.Book -> Unit
                    }
                }
                .drawWithContent {
                    val fold = if (style == SwipeStyle.Book) pageFold(offset(), grab(), size) else null
                    if (fold == null) {
                        drawContent()
                    } else {
                        pageLayer.record { this@drawWithContent.drawContent() }
                        drawPageTurn(pageLayer, fold, paper, paperEdge)
                    }
                }
                .pointerInput(style) {
                    if (previewOffset != null) return@pointerInput
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragStart = { at ->
                            settleJob?.cancel()
                            locked = null
                            lockedSide = 0
                            armed = false
                            // Новый захват — новый угол, но только если страница уже лежит ровно.
                            if (drag == 0f) grabY = at.y
                        },
                        onDragEnd = {
                            val v = drag
                            val action = if (v > 0) start else end
                            if (action == null || abs(v) < activeThreshold) {
                                settleTo(0f, if (style == SwipeStyle.Book) BookReturn else Motion.bouncy())
                                return@detectHorizontalDragGestures
                            }
                            val direction = sign(v)
                            locked = action
                            lockedSide = direction.toInt()
                            if (style == SwipeStyle.Book) {
                                // Страница переворачивается до конца, действие срабатывает, и (если задача
                                // остаётся в списке) страница так же плавно ложится обратно — уже в новом виде.
                                val full = direction * cardSize.width * BookFullTurn
                                settleTo(full, tween(520, easing = PageEasing)) {
                                    action.onTrigger()
                                    if (action.dismiss) {
                                        delay(600)
                                        drag = 0f
                                    } else {
                                        animate(drag, 0f, animationSpec = tween(620, easing = PageEasing)) { value, _ -> drag = value }
                                    }
                                }
                                return@detectHorizontalDragGestures
                            }
                            if (action.dismiss) {
                                val target = direction * cardSize.width
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
                                settleTo(0f, Motion.bouncy())
                            }
                        },
                        onDragCancel = { settleTo(0f, if (style == SwipeStyle.Book) BookReturn else Motion.bouncy()) },
                    ) { change, dragAmount ->
                        change.consume()
                        val current = drag
                        val available = if (current + dragAmount > 0) start else end
                        // Без действия в эту сторону — тугая резина; за порогом — мягкое сопротивление
                        // (страница «Книжки» идёт за пальцем без сопротивления).
                        val resistance = when {
                            available == null -> 0.15f
                            style == SwipeStyle.Book -> 1f
                            abs(current) > threshold -> 0.35f
                            else -> 1f
                        }
                        var next = current + dragAmount * resistance
                        if (style == SwipeStyle.Book) {
                            val limit = cardSize.width * BookFullTurn * 0.85f
                            next = next.coerceIn(-limit, limit)
                        }
                        val nowArmed = available != null && abs(next) >= activeThreshold
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

/**
 * Линия сгиба страницы. Палец тянет точку свободного края (на высоте захвата) — сгиб проходит
 * посередине между исходным и текущим положением этой точки, перпендикулярно направлению.
 * Взялись за верх или низ — точка ещё и смещается к середине, и загибается угол;
 * за середину — страница переворачивается ровно. [normal] смотрит в сторону поднятой части.
 */
private class PageFold(val mid: Offset, val normal: Offset, val lifted: Float)

private fun pageFold(v: Float, grabY: Float, size: Size): PageFold? {
    if (abs(v) < 0.5f || size.width <= 0f) return null
    val gx = if (v > 0) 0f else size.width
    val gy = grabY.coerceIn(0f, size.height)
    val lift = (size.height / 2f - gy) * 0.8f * smoothstep(0f, size.width * 0.9f, abs(v))
    val from = Offset(gx, gy)
    val to = Offset(gx + v, gy + lift)
    val d = from - to
    val length = d.getDistance()
    if (length < 0.5f) return null
    return PageFold(mid = (from + to) / 2f, normal = d / length, lifted = abs(v) / (size.width * BookFullTurn))
}

/** Полуплоскость по одну сторону сгиба ([side] = 1 — поднятая часть, -1 — лежащая). */
private fun DrawScope.halfPlane(fold: PageFold, side: Float): Path {
    val n = fold.normal * side
    val along = Offset(-n.y, n.x)
    val extent = (size.width + size.height) * 4f
    val a = fold.mid + along * extent
    val b = fold.mid - along * extent
    return Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(b.x + n.x * extent, b.y + n.y * extent)
        lineTo(a.x + n.x * extent, a.y + n.y * extent)
        close()
    }
}

/** Отражение относительно линии сгиба: так поднятая часть страницы ложится на другую сторону. */
private fun reflection(fold: PageFold): Matrix {
    val n = fold.normal
    val k = 2f * (fold.mid.x * n.x + fold.mid.y * n.y)
    return Matrix().apply {
        values[Matrix.ScaleX] = 1f - 2f * n.x * n.x
        values[Matrix.SkewX] = -2f * n.x * n.y
        values[Matrix.SkewY] = -2f * n.x * n.y
        values[Matrix.ScaleY] = 1f - 2f * n.y * n.y
        values[Matrix.TranslateX] = k * n.x
        values[Matrix.TranslateY] = k * n.y
    }
}

private fun DrawScope.cardPath(): Path =
    Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(24.dp.toPx()))) }

/**
 * Страница «Книжки»: лежащая часть карточки, тень от загиба и оборот загнутой части —
 * плотная бумага, сквозь которую чуть просвечивает зеркальный текст, со световым бликом у сгиба.
 */
private fun DrawScope.drawPageTurn(layer: GraphicsLayer, fold: PageFold, paper: Color, edge: Color) {
    val card = cardPath()
    val flat = halfPlane(fold, -1f)
    val lifted = halfPlane(fold, 1f)
    // Ближе к полному перевороту оборот тает — страница «уходит».
    val presence = 1f - smoothstep(0.7f, 0.98f, fold.lifted)
    val n = fold.normal

    clipPath(flat) { drawLayer(layer) }
    // Тень, которую загнутая страница отбрасывает на лежащую часть.
    clipPath(card) {
        clipPath(flat) {
            val reach = 26.dp.toPx()
            drawRect(
                Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.2f * presence),
                    1f to Color.Transparent,
                    start = fold.mid,
                    end = fold.mid - n * reach,
                ),
            )
        }
    }
    if (presence <= 0.01f) return

    drawIntoCanvas { canvas ->
        canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(-size.width * 3f, -size.height * 3f, size.width * 4f, size.height * 4f),
            Paint().apply { alpha = presence },
        )
        withTransform({ transform(reflection(fold)) }) {
            clipPath(card) {
                clipPath(lifted) {
                    // Зеркальный текст с обратной стороны, почти скрытый плотной бумагой.
                    drawLayer(layer)
                    drawRect(paper.copy(alpha = 0.9f))
                    // Свет: блик у сгиба, к краю страница уходит в лёгкую тень.
                    val span = (size.width * 0.7f).coerceAtLeast(1f)
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.32f),
                            0.25f to Color.White.copy(alpha = 0f),
                            1f to Color.Black.copy(alpha = 0.1f),
                            start = fold.mid,
                            end = fold.mid + n * span,
                        ),
                    )
                }
                // Кромка листа.
                clipPath(lifted) { drawPath(card, edge, style = Stroke(1.dp.toPx())) }
            }
        }
        canvas.restore()
    }
}

/** Подложка «Книжки»: цвет действия там, где страница поднялась, и мягкая тень у сгиба. */
private fun DrawScope.drawBookBackdrop(fold: PageFold, p: Float, color: Color) {
    val card = cardPath()
    clipPath(card) {
        clipPath(halfPlane(fold, 1f)) {
            drawRect(color.copy(alpha = (0.35f + 0.65f * p).coerceAtMost(1f)))
            drawRect(
                Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    start = fold.mid,
                    end = fold.mid + fold.normal * 30.dp.toPx(),
                ),
            )
        }
    }
}
