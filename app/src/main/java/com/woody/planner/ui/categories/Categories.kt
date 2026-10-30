package com.woody.planner.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.woody.planner.AppContainer
import com.woody.planner.domain.Category
import com.woody.planner.ui.components.GlassSheet
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.glass.FrostedCard
import com.woody.planner.ui.glass.LocalHazeState
import com.woody.planner.ui.glass.liquidGlass
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val counts: StateFlow<Map<Long, Int>> =
        repository.categoryTaskCounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun save(category: Category) = viewModelScope.launch { repository.saveCategory(category) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
}

val CategoryPalette = listOf(
    0xFF2F80ED, 0xFFF2994A, 0xFF27AE60, 0xFF7B61FF, 0xFFE5484D,
    0xFF00A3A3, 0xFFEB5FA4, 0xFFC08A3E, 0xFF5B6B8C, 0xFFFFB703,
).map { it.toInt() }

val CategoryEmoji = listOf(
    "🏋️", "🏃", "🧘", "🚴", "🛒", "🍗", "🥦", "🍳", "🏠", "🧹", "🧺", "🪴",
    "💼", "💻", "📚", "✏️", "💊", "🦷", "❤️", "😴", "🐾", "🚗", "✈️", "🎉",
    "🎁", "💰", "📞", "🎨", "🎵", "⚽", "👶", "🌳",
)

/** Лист создания/редактирования категории. [category] с id = 0 — новая. */
@Composable
fun CategoryEditorSheet(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var last by remember { mutableStateOf(category) }
    if (category != null) last = category
    GlassSheet(visible = category != null, onDismiss = onDismiss) {
        val initial = last ?: return@GlassSheet
        CategoryEditorContent(initial, onSave, onDelete)
    }
}

@Composable
private fun CategoryEditorContent(initial: Category, onSave: (Category) -> Unit, onDelete: (Long) -> Unit) {
    val colors = WoodyTheme.colors
    var draft by remember(initial) { mutableStateOf(initial) }
    val accent by animateColorAsState(Color(draft.colorArgb), Motion.snappy(), label = "catColor")

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(draft.emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.size(14.dp))
            BasicTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (draft.name.isEmpty()) {
                            Text("Название", style = MaterialTheme.typography.headlineSmall, color = colors.textTertiary)
                        }
                        inner()
                    }
                },
            )
        }

        Text("ЦВЕТ", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryPalette.forEach { argb ->
                val selected = draft.colorArgb == argb
                val scale = animateFloatAsState(if (selected) 1.12f else 1f, Motion.bouncy(), label = "swatch")
                Box(
                    Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .bouncyClickable { draft = draft.copy(colorArgb = argb) }
                        .background(Color(argb), CircleShape)
                        .then(if (selected) Modifier.border(3.dp, colors.background.copy(alpha = 0.9f), CircleShape) else Modifier),
                )
            }
        }

        Text("ЗНАЧОК", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryEmoji.forEach { emoji ->
                val selected = draft.emoji == emoji
                val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.25f) else colors.chip, Motion.snappy(), label = "emojiBg")
                Box(
                    Modifier
                        .size(44.dp)
                        .bouncyClickable { draft = draft.copy(emoji = emoji) }
                        .clip(Shapes.Chip)
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial.id != 0L) {
                Box(
                    Modifier
                        .size(54.dp)
                        .bouncyClickable { onDelete(initial.id) }
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(WoodyIcons.Trash, contentDescription = "Удалить категорию", tint = colors.accent)
                }
            }
            val enabled = draft.name.isNotBlank()
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .bouncyClickable(enabled = enabled) { onSave(draft.copy(name = draft.name.trim())) }
                    .clip(Shapes.Pill)
                    .background(if (enabled) accent else colors.textTertiary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (initial.id == 0L) "Создать" else "Сохранить", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

fun tasksWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "задач"
        mod10 == 1 -> "задача"
        mod10 in 2..4 -> "задачи"
        else -> "задач"
    }
    return if (n == 0) "Нет задач" else "$n $word"
}
