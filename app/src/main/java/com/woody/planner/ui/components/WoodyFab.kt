package com.woody.planner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme

data class FabMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Единственная кнопка приложения. Касание — новая задача.
 * Удержание — «вытекает» меню; можно, не отпуская пальца, провести к пункту и отпустить.
 */
@Composable
fun WoodyFab(
    onAdd: () -> Unit,
    items: List<FabMenuItem>,
    showHint: Boolean,
    onMenuOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WoodyTheme.colors
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    var hovered by remember { mutableIntStateOf(-1) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var fabCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val currentItems by rememberUpdatedState(items)
    val currentOnAdd by rememberUpdatedState(onAdd)
    val currentOnOpened by rememberUpdatedState(onMenuOpened)
    val rotation = animateFloatAsState(if (open) 135f else 0f, Motion.bouncy(), label = "fabRotation")

    fun openMenu() {
        if (open) return
        open = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnOpened()
    }

    Box(modifier.fillMaxSize()) {
        // Затемнение под меню: тап мимо — закрыть.
        AnimatedVisibility(open, enter = fadeIn(Motion.snappy()), exit = fadeOut(Motion.snappy())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = colors.scrim.alpha * 0.6f))
                    .clickable(interactionSource = null, indication = null) { open = false },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            currentItems.forEachIndexed { index, item ->
                // Пункты появляются снизу вверх с задержкой — «вытекают» из кнопки.
                val reverse = currentItems.size - 1 - index
                val progress = animateFloatAsState(
                    if (open) 1f else 0f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f - reverse * 60f),
                    label = "item$index",
                )
                val isHovered = hovered == index
                val tint by animateColorAsState(
                    if (isHovered) colors.accent.copy(alpha = 0.85f) else colors.glassTint,
                    Motion.snappy(),
                    label = "itemTint",
                )
                if (progress.value > 0.01f || open) {
                    Row(
                        Modifier
                            // Границы берём до трансформаций: это финальное место пункта.
                            .onGloballyPositioned { itemBounds[index] = it.boundsInRoot() }
                            .graphicsLayer {
                                val p = progress.value
                                alpha = p.coerceIn(0f, 1f)
                                translationY = (1f - p) * (64.dp.toPx() + reverse * 40.dp.toPx())
                                val s = 0.6f + 0.4f * p + if (isHovered) 0.06f else 0f
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(1f, 1f)
                            }
                            .height(52.dp)
                            .liquidGlass(Shapes.Pill, tint = tint)
                            .clickable(interactionSource = null, indication = null, enabled = open) {
                                open = false
                                item.onClick()
                            }
                            .padding(start = 18.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val fg = if (isHovered) Color.White else colors.textPrimary
                        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = showHint && !open,
                    enter = fadeIn() + slideInHorizontally(Motion.bouncy()) { it / 3 },
                    exit = fadeOut() + slideOutHorizontally(Motion.soft()) { it / 3 },
                ) {
                    Text(
                        "Удерживайте + — настройки",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .liquidGlass(Shapes.Pill)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
                Box(
                    Modifier
                        .size(66.dp)
                        .onGloballyPositioned { fabCoords = it }
                        .liquidGlass(Shapes.Pill, tint = colors.accent.copy(alpha = 0.85f), interactionSource = interaction)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Добавить"
                            onClick("Новая задача") { currentOnAdd(); true }
                            onLongClick("Меню") { openMenu(); true }
                        }
                        .pointerInput(Unit) {
                            val longPress = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val press = PressInteraction.Press(down.position)
                                interaction.tryEmit(press)
                                if (open) {
                                    // Меню уже открыто — кнопка-крестик закрывает его.
                                    waitForUpOrCancellation()
                                    interaction.tryEmit(PressInteraction.Release(press))
                                    open = false
                                    return@awaitEachGesture
                                }
                                val up = withTimeoutOrNull(longPress) { waitForUpOrCancellation() }
                                if (up != null) {
                                    interaction.tryEmit(PressInteraction.Release(press))
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    currentOnAdd()
                                    return@awaitEachGesture
                                }
                                // Долгое нажатие: открываем меню и следим за пальцем.
                                openMenu()
                                interaction.tryEmit(PressInteraction.Release(press))
                                var lastHover = -1
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val coords = fabCoords
                                    if (coords != null) {
                                        val rootPos = coords.localToRoot(change.position)
                                        val hit = itemBounds.entries.firstOrNull { it.value.inflate(12.dp.toPx()).contains(rootPos) }?.key ?: -1
                                        if (hit != lastHover) {
                                            lastHover = hit
                                            hovered = hit
                                            if (hit >= 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    }
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                hovered = -1
                                if (lastHover >= 0) {
                                    open = false
                                    currentItems.getOrNull(lastHover)?.onClick?.invoke()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        WoodyIcons.Plus,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { rotationZ = rotation.value },
                    )
                }
            }
        }
    }
}

