package com.woody.planner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.WoodyTheme

/** Кольцо прогресса дня: дуга догоняет значение пружиной, цифры перелистываются. */
@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val colors = WoodyTheme.colors
    val target = if (total == 0) 0f else done / total.toFloat()
    val progress = animateFloatAsState(target, Motion.soft(), label = "ring")
    val arcColor = animateColorAsState(
        if (total > 0 && done == total) colors.success else colors.accent,
        Motion.soft(),
        label = "ringColor",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(colors.chip, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val p = progress.value
            if (p > 0.001f) {
                drawArc(
                    color = arcColor.value,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = done,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(Motion.bouncy()) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(Motion.bouncy()) { if (up) -it else it } + fadeOut())
                },
                label = "done",
            ) { value ->
                Text("$value", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
            Text("/$total", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
    }
}
