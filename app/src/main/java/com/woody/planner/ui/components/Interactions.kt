package com.woody.planner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.woody.planner.ui.theme.Motion

/**
 * Нажатие с упругим «вдавливанием» вместо ripple — так кнопки ощущаются мягкими.
 * [glowColor] — мягкий небольшой отсвет в точке касания (обычно цвет самой кнопки), обрезанный по [glowShape].
 */
@Composable
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    glowColor: Color? = null,
    glowShape: Shape = RectangleShape,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, Motion.bouncy(), label = "press")
    val clickable = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    }
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .then(if (glowColor != null) Modifier.pressGlow(interaction, glowColor, glowShape) else Modifier)
        .then(clickable)
}

/** Мягкое пятно света под пальцем: появляется пружиной и плавно тает после отпускания. */
@Composable
fun Modifier.pressGlow(interaction: MutableInteractionSource, color: Color, shape: Shape = RectangleShape): Modifier {
    var center by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    center = it.pressPosition
                    pressed = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val strength = animateFloatAsState(if (pressed) 1f else 0f, Motion.soft(), label = "glow")
    return drawWithContent {
        drawContent()
        val a = strength.value
        if (a <= 0.01f || center == Offset.Unspecified) return@drawWithContent
        val radius = size.minDimension * (0.7f + 0.3f * a)
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.32f * a), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
