package com.woody.planner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyTheme

/** Чип-переключатель: цвет плавно перетекает, нажатие — упругое. */
@Composable
fun WoodyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingText: String? = null,
    selectedColor: Color = WoodyTheme.colors.accent,
) {
    val colors = WoodyTheme.colors
    val background by animateColorAsState(if (selected) selectedColor else colors.chip, Motion.snappy(), label = "chipBg")
    val content by animateColorAsState(if (selected) Color.White else colors.textPrimary, Motion.snappy(), label = "chipFg")
    Row(
        modifier
            .bouncyClickable(onClick = onClick)
            .clip(Shapes.Chip)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        if (leadingText != null) Text(leadingText, style = MaterialTheme.typography.labelLarge)
        Text(label, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

/** Маленькая метка в строке задачи: время, категория, количество подзадач. */
@Composable
fun MetaTag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = WoodyTheme.colors.textSecondary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        if (text.isNotEmpty()) Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
