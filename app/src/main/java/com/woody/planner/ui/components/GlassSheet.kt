package com.woody.planner.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Стеклянный лист поверх экрана. Появляется, «вырастая» снизу справа (от кнопки +),
 * закрывается свайпом вниз, тапом по фону или жестом «назад» с предпросмотром.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WoodyTheme.colors
    AnimatedVisibility(visible, enter = fadeIn(Motion.snappy()), exit = fadeOut(Motion.snappy())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(Motion.soft()) { it / 3 } +
            scaleIn(Motion.soft(), initialScale = 0.82f, transformOrigin = TransformOrigin(0.85f, 1f)) +
            fadeIn(Motion.snappy()),
        exit = slideOutVertically(Motion.soft()) { it } + fadeOut(Motion.snappy()),
    ) {
        val drag = remember { Animatable(0f) }
        var backProgress by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }

        PredictiveBackHandler(enabled = visible) { events ->
            try {
                events.collect { backProgress = it.progress }
                onDismiss()
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxSheetHeight = maxHeight - 12.dp
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .graphicsLayer {
                        translationY = drag.value
                        val s = 1f - backProgress * 0.08f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .heightIn(max = maxSheetHeight)
                    .fillMaxWidth()
                    .liquidGlass(Shapes.Sheet, tint = colors.background.copy(alpha = if (colors.isDark) 0.62f else 0.55f))
                    .clickable(interactionSource = null, indication = null) { /* не пропускать тапы к фону */ },
            ) {
                // Ручка: тянем вниз — лист следует за пальцем, отпускаем — закрывается или пружинит обратно.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (drag.value > dismissDistance) onDismiss() else drag.animateTo(0f, Motion.bouncy())
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f, Motion.bouncy()) } },
                            ) { change, dy ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 5.dp)
                            .background(colors.textTertiary.copy(alpha = 0.5f), RoundedCornerShape(50)),
                    )
                }
                content()
            }
        }
    }
}
