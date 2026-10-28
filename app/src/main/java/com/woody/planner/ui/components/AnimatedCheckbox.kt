package com.woody.planner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Круглый чекбокс: заливка «растекается» из центра, галочка прорисовывается штрихом,
 * а сам кружок упруго проседает и отпружинивает.
 */
@Composable
fun AnimatedCheckbox(
    checked: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    touchSize: Dp = 44.dp,
) {
    val progress = animateFloatAsState(if (checked) 1f else 0f, Motion.soft(), label = "check")
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val ring = WoodyTheme.colors.textTertiary

    Box(
        modifier = modifier
            .size(touchSize)
            .clickable(interactionSource = null, indication = null, role = Role.Checkbox) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                scope.launch {
                    scale.animateTo(0.72f, spring(stiffness = 2200f))
                    scale.animateTo(1f, Motion.bouncy())
                }
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            val p = progress.value
            val strokeWidth = 2.dp.toPx()
            val radius = this.size.minDimension / 2f - strokeWidth / 2f
            drawCircle(lerp(ring, color, p.coerceIn(0f, 1f)), radius = radius, style = Stroke(strokeWidth))
            if (p > 0f) drawCircle(color, radius = (radius + strokeWidth / 2f) * p.coerceIn(0f, 1f))
            val checkProgress = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (checkProgress > 0f) drawCheck(checkProgress, Color.White, 2.4.dp.toPx())
        }
    }
}

/** Галочка из двух отрезков, прорисованная на долю [fraction] своей длины. */
fun DrawScope.drawCheck(fraction: Float, color: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val a = Offset(w * 0.28f, h * 0.53f)
    val b = Offset(w * 0.44f, h * 0.68f)
    val c = Offset(w * 0.73f, h * 0.37f)
    val first = hypot(b.x - a.x, b.y - a.y)
    val second = hypot(c.x - b.x, c.y - b.y)
    val drawn = (first + second) * fraction
    if (drawn <= first) {
        val t = drawn / first
        drawLine(color, a, Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), strokeWidth, StrokeCap.Round)
    } else {
        drawLine(color, a, b, strokeWidth, StrokeCap.Round)
        val t = (drawn - first) / second
        drawLine(color, b, Offset(b.x + (c.x - b.x) * t, b.y + (c.y - b.y) * t), strokeWidth, StrokeCap.Round)
    }
}
