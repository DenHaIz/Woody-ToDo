package com.woody.planner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme

/**
 * Единственная кнопка приложения. Касание — новая задача, удержание — сразу настройки.
 * Рядом может показываться подсказка про удержание (пока пользователь ни разу им не воспользовался).
 */
@Composable
fun WoodyFab(
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    showHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WoodyTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(modifier.padding(end = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = showHint,
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
                .semantics { contentDescription = "Добавить" }
                .bouncyClickable(
                    pressedScale = 0.9f,
                    glowColor = Color.White,
                    glowShape = Shapes.Pill,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    },
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAdd()
                }
                .liquidGlass(Shapes.Pill, tint = colors.accent.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(WoodyIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}
