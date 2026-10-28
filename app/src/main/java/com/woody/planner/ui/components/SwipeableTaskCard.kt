package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    /** true — карточка улетает за край (задача уходит из списка), false — возвращается на место. */
    val dismiss: Boolean,
    val onTrigger: () -> Unit,
)

/**
 * Карточка со свайпами в обе стороны. Подложка проявляется по мере сдвига,
 * за порогом — резиновое сопротивление и тактильный щелчок.
 */
@Composable
fun SwipeableTaskCard(
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    val corner = with(LocalDensity.current) { 24.dp.toPx() }
    val start by rememberUpdatedState(startAction)
    val end by rememberUpdatedState(endAction)

    Box(modifier.onSizeChanged { width = it.width }) {
        // Подложка с действием
        Box(
            Modifier
                .matchParentSize()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .drawBehind {
                    val v = offset.value
                    val action = if (v > 0) start else end
                    if (action != null && v != 0f) {
                        val alpha = (abs(v) / threshold).coerceIn(0f, 1f)
                        drawRoundRect(action.color.copy(alpha = 0.25f + 0.75f * alpha), cornerRadius = CornerRadius(corner))
                    }
                },
        ) {
            start?.let { action ->
                ActionHint(action, Modifier.align(Alignment.CenterStart).padding(start = 22.dp)) { offset.value / threshold }
            }
            end?.let { action ->
                ActionHint(action, Modifier.align(Alignment.CenterEnd).padding(end = 22.dp)) { -offset.value / threshold }
            }
        }

        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(Unit) {
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragStart = { armed = false },
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                val action = if (v > 0) start else end
                                if (action != null && abs(v) >= threshold) {
                                    if (action.dismiss) {
                                        offset.animateTo(if (v > 0) width.toFloat() else -width.toFloat(), Motion.snappy())
                                        action.onTrigger()
                                        // Если элемент остался в списке (например, после отмены) — вернём его на место.
                                        delay(700)
                                        offset.snapTo(0f)
                                    } else {
                                        action.onTrigger()
                                        offset.animateTo(0f, Motion.bouncy())
                                    }
                                } else {
                                    offset.animateTo(0f, Motion.bouncy())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, Motion.bouncy()) } },
                    ) { change, dragAmount ->
                        change.consume()
                        val current = offset.value
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
                        scope.launch { offset.snapTo(next) }
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun ActionHint(action: SwipeAction, modifier: Modifier, progress: () -> Float) {
    Column(
        modifier.graphicsLayer {
            val p = progress().coerceIn(0f, 1.2f)
            alpha = (p * 1.4f).coerceIn(0f, 1f)
            val s = 0.6f + 0.4f * p.coerceAtMost(1f) + if (p >= 1f) 0.08f else 0f
            scaleX = s
            scaleY = s
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(action.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
