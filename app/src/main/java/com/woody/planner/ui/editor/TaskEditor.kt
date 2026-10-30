package com.woody.planner.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woody.planner.data.DraftSubtask
import com.woody.planner.data.TaskDraft
import com.woody.planner.domain.Category
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.RecurrenceType
import com.woody.planner.domain.ReminderMath
import com.woody.planner.domain.bit
import com.woody.planner.ui.components.FitText
import com.woody.planner.ui.components.GlassSheet
import com.woody.planner.ui.components.MonthCalendar
import com.woody.planner.ui.components.RuLocale
import com.woody.planner.ui.components.WoodyChip
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("d MMMM", RuLocale)

private val ReminderOptions = listOf(0 to "В момент", 10 to "За 10 мин", 30 to "За 30 мин", 60 to "За час")
private val TimePresets = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(21, 0))

private val TileShape = RoundedCornerShape(24.dp)

/** Параметр задачи, который настраивается во всплывающем окне. */
private enum class Param(val title: String) {
    Date("Дата"),
    Time("Время"),
    Category("Категория"),
    Reminder("Напоминание"),
    Repeat("Повтор"),
    Checklist("Чек-лист"),
}

/**
 * Редактор задачи в стеклянном листе: название и шесть плиток-параметров со значениями
 * по умолчанию. Нажатие на плитку открывает окно настройки, которое «вырастает» из неё.
 * Клавиатура появляется только по нажатию на поле ввода.
 */
@Composable
fun TaskEditorSheet(
    draft: TaskDraft?,
    categories: List<Category>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onReminderEnabled: () -> Unit,
) {
    // Рабочая копия черновика: её правят и лист, и окна параметров. Сбрасывается при каждом
    // новом открытии и живёт, пока лист доигрывает анимацию закрытия.
    var openedFor by remember { mutableStateOf<TaskDraft?>(null) }
    var work by remember { mutableStateOf<TaskDraft?>(null) }
    var param by remember { mutableStateOf<Param?>(null) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    if (draft != null && draft !== openedFor) {
        openedFor = draft
        work = draft
        param = null
    }
    if (draft == null && param != null) param = null

    GlassSheet(visible = draft != null, onDismiss = onDismiss) {
        val current = work ?: return@GlassSheet
        EditorContent(
            draft = current,
            isNew = openedFor?.isNew ?: true,
            categories = categories,
            today = today,
            onChange = { work = it },
            onOpenParam = { p, at ->
                origin = at
                param = p
            },
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss,
        )
    }

    ParamPopup(open = param, origin = origin, onDismiss = { param = null }) { shown ->
        val current = work ?: return@ParamPopup
        val update: (TaskDraft) -> Unit = { work = it }
        val close = { param = null }
        when (shown) {
            Param.Date -> DatePanel(current, today, update, close)
            Param.Time -> TimePanel(current, update, close)
            Param.Category -> CategoryPanel(current, categories, update, close)
            Param.Reminder -> ReminderPanel(
                draft = current,
                today = today,
                onChange = {
                    if (current.reminderOffsetMin == null && it.reminderOffsetMin != null) onReminderEnabled()
                    update(it)
                },
                onPickTime = { param = Param.Time },
                onClose = close,
            )
            Param.Repeat -> RepeatPanel(current, update, close)
            Param.Checklist -> ChecklistPanel(current, update, close)
        }
    }
}

@Composable
private fun EditorContent(
    draft: TaskDraft,
    isNew: Boolean,
    categories: List<Category>,
    today: LocalDate,
    onChange: (TaskDraft) -> Unit,
    onOpenParam: (Param, Offset) -> Unit,
    onSave: (TaskDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WoodyTheme.colors
    val focusManager = LocalFocusManager.current
    val open: (Param, Offset) -> Unit = { p, at ->
        // Окно параметра — без клавиатуры: снимаем фокус с полей ввода.
        focusManager.clearFocus()
        onOpenParam(p, at)
    }

    // Пока печатают, остаются дата, время и категория; остальное — за полоской «Дополнительно».
    var titleFocused by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    val typing = titleFocused || noteFocused
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(typing) { if (!typing) moreOpen = false }
    // Клавиатуру спрятали жестом «назад» — снимаем фокус, чтобы плитки вернулись.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isNew) "Новая задача" else "Задача",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            CloseButton(onDismiss)
        }

        PlainField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            placeholder = "Что нужно сделать?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { titleFocused = it.isFocused }
                .padding(vertical = 6.dp),
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
        )
        PlainField(
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it)) },
            placeholder = "Заметка",
            style = MaterialTheme.typography.bodyLarge.copy(color = colors.textSecondary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { noteFocused = it.isFocused }
                .padding(bottom = 14.dp),
            singleLine = false,
        )

        val category = categories.firstOrNull { it.id == draft.categoryId }
        val tiles = listOf(
            TileData(Param.Date, WoodyIcons.Calendar, dateLabel(draft.date, today), active = draft.date != today),
            TileData(
                Param.Time,
                WoodyIcons.Clock,
                draft.time?.format(TimeFormat) ?: "Весь день",
                active = draft.time != null,
            ),
            TileData(
                Param.Category,
                WoodyIcons.Grid,
                category?.name ?: "Нет",
                active = category != null,
                tint = category?.let { Color(it.colorArgb) },
                emoji = category?.emoji,
            ),
            TileData(
                Param.Reminder,
                WoodyIcons.Bell,
                ReminderOptions.firstOrNull { it.first == draft.reminderOffsetMin }?.second ?: "Нет",
                active = draft.reminderOffsetMin != null,
            ),
            TileData(Param.Repeat, WoodyIcons.Repeat, recurrenceLabel(draft.recurrence), active = draft.recurrence.isRecurring),
            TileData(
                Param.Checklist,
                WoodyIcons.Checklist,
                draft.subtasks.count { it.title.isNotBlank() }.let { if (it == 0) "Пусто" else pointsWord(it) },
                active = draft.subtasks.any { it.title.isNotBlank() },
            ),
        )
        val (main, extra) = tiles.chunked(3)
        // Плитки большие, пока клавиатуры нет, и ужимаются вместе с её появлением.
        val tileHeight by animateDpAsState(if (typing) 84.dp else 132.dp, TileSpring, label = "tileHeight")
        TileRow(main, tileHeight, typing) { p, at -> open(p, at) }
        AnimatedVisibility(
            visible = !typing || moreOpen,
            enter = expandVertically(TileSizeSpring, expandFrom = Alignment.Top) + fadeIn(TileFadeSpring),
            exit = shrinkVertically(TileSizeSpring, shrinkTowards = Alignment.Top) + fadeOut(TileFadeSpring),
        ) {
            TileRow(extra, tileHeight, typing) { p, at -> open(p, at) }
        }
        AnimatedVisibility(
            visible = typing && !moreOpen,
            enter = expandVertically(TileSizeSpring, expandFrom = Alignment.Top) + fadeIn(TileFadeSpring),
            exit = shrinkVertically(TileSizeSpring, shrinkTowards = Alignment.Top) + fadeOut(TileFadeSpring),
        ) {
            MoreStrip(extra) { moreOpen = true }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                Box(
                    Modifier
                        .size(54.dp)
                        .bouncyClickable(glowColor = colors.accent, glowShape = CircleShape) { onDelete(draft.id) }
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(WoodyIcons.Trash, contentDescription = "Удалить", tint = colors.accent)
                }
            }
            val enabled = draft.title.isNotBlank()
            val saveColor by animateColorAsState(
                if (enabled) colors.accent else colors.textTertiary.copy(alpha = 0.4f),
                Motion.snappy(),
                label = "save",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .bouncyClickable(enabled = enabled, glowColor = Color.White, glowShape = Shapes.Pill) {
                        focusManager.clearFocus()
                        onSave(draft)
                    }
                    .clip(Shapes.Pill)
                    .background(saveColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isNew) "Добавить" else "Сохранить",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

private data class TileData(
    val param: Param,
    val icon: ImageVector,
    val value: String,
    val active: Boolean,
    val tint: Color? = null,
    val emoji: String? = null,
)

private val TileSpring = spring<Dp>(dampingRatio = 1f, stiffness = 320f)
private val TileSizeSpring = spring(dampingRatio = 1f, stiffness = 320f, visibilityThreshold = IntSize.VisibilityThreshold)
private val TileFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 320f)

@Composable
private fun TileRow(tiles: List<TileData>, height: Dp, compact: Boolean, onOpen: (Param, Offset) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { tile ->
            ParamTile(
                tile = tile,
                compact = compact,
                onClick = { onOpen(tile.param, it) },
                modifier = Modifier
                    .weight(1f)
                    .height(height),
            )
        }
    }
}

/** Скруглённый квадрат: значок, название параметра и его текущее значение. */
@Composable
private fun ParamTile(tile: TileData, compact: Boolean, onClick: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val colors = WoodyTheme.colors
    val tint = tile.tint ?: colors.accent
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val background by animateColorAsState(
        if (tile.active) tint.copy(alpha = if (colors.isDark) 0.24f else 0.14f) else colors.chip,
        Motion.snappy(),
        label = "tileBg",
    )
    val valueColor by animateColorAsState(if (tile.active) tint else colors.textPrimary, Motion.snappy(), label = "tileFg")
    val iconSize by animateDpAsState(if (compact) 28.dp else 42.dp, TileSpring, label = "tileIcon")
    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = tile.param.title }
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .bouncyClickable(glowColor = tint, glowShape = TileShape) { onClick(center) }
            .clip(TileShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .background(if (tile.active) tint.copy(alpha = 0.18f) else colors.background.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.emoji != null) {
                Text(tile.emoji, fontSize = if (compact) 15.sp else 21.sp)
            } else {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = if (tile.active) tint else colors.textSecondary,
                    modifier = Modifier.size(iconSize * 0.5f),
                )
            }
        }
        Column {
            FitText(tile.param.title, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, minSize = 9.sp)
            FitText(
                tile.value,
                style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
                    .copy(fontWeight = FontWeight.SemiBold),
                color = valueColor,
                minSize = 10.sp,
            )
        }
    }
}

/** Тонкая полоска вместо скрытых плиток: значки показывают, какие параметры уже заданы. */
@Composable
private fun MoreStrip(hidden: List<TileData>, onClick: () -> Unit) {
    val colors = WoodyTheme.colors
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .height(38.dp)
            .bouncyClickable(pressedScale = 0.97f, glowColor = colors.textPrimary, glowShape = Shapes.Pill, onClick = onClick)
            .clip(Shapes.Pill)
            .background(colors.chip)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Дополнительно",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        hidden.forEach { tile ->
            Icon(
                tile.icon,
                contentDescription = null,
                tint = if (tile.active) tile.tint ?: colors.accent else colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Icon(WoodyIcons.ChevronDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/**
 * Окно настройки параметра поверх экрана. Вырастает из точки [origin] (центр нажатой плитки)
 * и сворачивается обратно в неё. Закрывается тапом по фону, «назад» или кнопкой.
 */
@Composable
private fun ParamPopup(open: Param?, origin: Offset, onDismiss: () -> Unit, content: @Composable (Param) -> Unit) {
    val colors = WoodyTheme.colors
    var last by remember { mutableStateOf(open) }
    if (open != null) last = open
    val progress = remember { Animatable(0f) }
    val isOpen by rememberUpdatedState(open != null)
    LaunchedEffect(open != null) {
        progress.animateTo(
            if (open != null) 1f else 0f,
            if (open != null) spring(dampingRatio = 0.82f, stiffness = 480f) else spring(dampingRatio = 1f, stiffness = 650f),
        )
    }
    val shown by remember { derivedStateOf { isOpen || progress.value > 0.001f } }
    if (!shown) return
    val shownParam = last ?: return

    BackHandler(enabled = open != null, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(colors.scrim)
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
        var bounds by remember { mutableStateOf(Rect.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("param-window")
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .graphicsLayer {
                        val p = progress.value
                        transformOrigin = if (origin.isSpecified && bounds.width > 0f && bounds.height > 0f) {
                            TransformOrigin((origin.x - bounds.left) / bounds.width, (origin.y - bounds.top) / bounds.height)
                        } else {
                            TransformOrigin.Center
                        }
                        val s = 0.3f + 0.7f * p
                        scaleX = s
                        scaleY = s
                        alpha = (p * 1.8f).coerceIn(0f, 1f)
                    }
                    // Плотная поверхность без размытия: окно всегда читается и не зависит от шейдера стекла.
                    .shadow(24.dp, Shapes.Panel, clip = false)
                    .clip(Shapes.Panel)
                    .background(colors.background)
                    .border(1.dp, colors.cardBorder, Shapes.Panel)
                    .clickable(interactionSource = null, indication = null) { /* тапы внутри окна не закрывают его */ }
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        shownParam.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    CloseButton(onDismiss)
                }
                content(shownParam)
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val colors = WoodyTheme.colors
    Icon(
        WoodyIcons.Close,
        contentDescription = "Закрыть",
        tint = colors.textSecondary,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .bouncyClickable(onClick = onClick)
            .padding(7.dp),
    )
}

/** Кнопка подтверждения внизу окна параметра. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    val colors = WoodyTheme.colors
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(50.dp)
            .bouncyClickable(glowColor = Color.White, glowShape = Shapes.Pill, onClick = onClick)
            .clip(Shapes.Pill)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("Готово", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

// --- Окна параметров ---

@Composable
private fun DatePanel(draft: TaskDraft, today: LocalDate, onChange: (TaskDraft) -> Unit, onClose: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(draft.date)) }
    val pick = { day: LocalDate ->
        onChange(draft.copy(date = day))
        onClose()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WoodyChip("Сегодня", draft.date == today, { pick(today) })
        WoodyChip("Завтра", draft.date == today.plusDays(1), { pick(today.plusDays(1)) })
        WoodyChip("Через неделю", draft.date == today.plusWeeks(1), { pick(today.plusWeeks(1)) })
    }
    Spacer(Modifier.height(12.dp))
    MonthCalendar(
        month = month,
        selected = draft.date,
        today = today,
        summaries = { emptyList() },
        onSelect = pick,
        onMonthChange = { month = it },
    )
}

@Composable
private fun TimePanel(draft: TaskDraft, onChange: (TaskDraft) -> Unit, onClose: () -> Unit) {
    val colors = WoodyTheme.colors
    val initial = draft.time ?: LocalTime.now().plusHours(1).withMinute(0)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WoodyChip("Весь день", draft.time == null, {
            onChange(draft.copy(time = null))
            onClose()
        })
        TimePresets.forEach { preset ->
            WoodyChip(preset.format(TimeFormat), draft.time == preset, {
                onChange(draft.copy(time = preset))
                onClose()
            })
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(
            state,
            colors = TimePickerDefaults.colors(
                clockDialColor = colors.chip,
                selectorColor = colors.accent,
                timeSelectorSelectedContainerColor = colors.accent.copy(alpha = 0.18f),
                timeSelectorSelectedContentColor = colors.accent,
                timeSelectorUnselectedContainerColor = colors.chip,
                timeSelectorUnselectedContentColor = colors.textPrimary,
                clockDialSelectedContentColor = Color.White,
                clockDialUnselectedContentColor = colors.textPrimary,
            ),
        )
    }
    DoneButton {
        onChange(draft.copy(time = LocalTime.of(state.hour, state.minute)))
        onClose()
    }
}

@Composable
private fun CategoryPanel(draft: TaskDraft, categories: List<Category>, onChange: (TaskDraft) -> Unit, onClose: () -> Unit) {
    val colors = WoodyTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WoodyChip(
            label = "Без категории",
            selected = draft.categoryId == null,
            onClick = {
                onChange(draft.copy(categoryId = null))
                onClose()
            },
            selectedColor = colors.textPrimary,
        )
        categories.forEach { category ->
            WoodyChip(
                label = category.name,
                leadingText = category.emoji,
                selected = draft.categoryId == category.id,
                onClick = {
                    onChange(draft.copy(categoryId = category.id))
                    onClose()
                },
                selectedColor = Color(category.colorArgb),
            )
        }
    }
}

@Composable
private fun ReminderPanel(
    draft: TaskDraft,
    today: LocalDate,
    onChange: (TaskDraft) -> Unit,
    onPickTime: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = WoodyTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WoodyChip("Нет", draft.reminderOffsetMin == null, {
            onChange(draft.copy(reminderOffsetMin = null))
            onClose()
        })
        ReminderOptions.forEach { (minutes, label) ->
            WoodyChip(
                label = label,
                selected = draft.reminderOffsetMin == minutes,
                icon = WoodyIcons.Bell,
                onClick = {
                    onChange(draft.copy(reminderOffsetMin = minutes))
                    // Без времени напоминание привязано к 9:00 — оставляем окно открытым с подсказкой.
                    if (draft.time != null) onClose()
                },
            )
        }
    }
    if (draft.time == null) {
        // Без времени напоминание приходит в 9:00; если сегодня это время уже прошло — его не будет.
        val offset = (draft.reminderOffsetMin ?: 0).toLong()
        val missed = draft.date == today &&
            LocalDateTime.now().isAfter(today.atTime(ReminderMath.DefaultTime).minusMinutes(offset)) &&
            !draft.recurrence.isRecurring
        Text(
            if (missed) {
                "У задачи нет времени, а сегодня ${ReminderMath.DefaultTime.format(TimeFormat)} уже прошло — укажите время, иначе напоминания не будет."
            } else {
                "У задачи нет времени — напомню в ${ReminderMath.DefaultTime.format(TimeFormat)}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (missed) colors.accent else colors.textSecondary,
            modifier = Modifier.padding(top = 14.dp, start = 4.dp),
        )
        Text(
            "Указать время",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            modifier = Modifier
                .padding(top = 8.dp)
                .bouncyClickable(glowColor = colors.accent, glowShape = Shapes.Pill, onClick = onPickTime)
                .clip(Shapes.Pill)
                .background(colors.accent.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RepeatPanel(draft: TaskDraft, onChange: (TaskDraft) -> Unit, onClose: () -> Unit) {
    RecurrenceEditor(draft.recurrence, draft.date) { onChange(draft.copy(recurrence = it)) }
    DoneButton(onClose)
}

@Composable
private fun ChecklistPanel(draft: TaskDraft, onChange: (TaskDraft) -> Unit, onClose: () -> Unit) {
    var nextKey by remember { mutableLongStateOf(-1L - draft.subtasks.size) }
    SubtaskEditor(
        subtasks = draft.subtasks,
        onChange = { onChange(draft.copy(subtasks = it)) },
        newKey = { nextKey.also { nextKey-- } },
    )
    DoneButton(onClose)
}

@Composable
private fun RecurrenceEditor(value: Recurrence, startDate: LocalDate, onChange: (Recurrence) -> Unit) {
    val colors = WoodyTheme.colors
    val options = listOf(
        RecurrenceType.NONE to "Нет",
        RecurrenceType.DAILY to "Каждый день",
        RecurrenceType.WEEKLY to "По дням недели",
        RecurrenceType.EVERY_N_DAYS to "Каждые N дней",
        RecurrenceType.MONTHLY to "Каждый месяц",
    )
    Column(Modifier.animateContentSize(Motion.soft())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (type, label) ->
                WoodyChip(
                    label = label,
                    selected = value.type == type,
                    icon = if (type != RecurrenceType.NONE && value.type == type) WoodyIcons.Repeat else null,
                    onClick = {
                        onChange(
                            when (type) {
                                RecurrenceType.WEEKLY -> value.copy(
                                    type = type,
                                    daysMask = value.daysMask.takeIf { it != 0 } ?: startDate.dayOfWeek.bit,
                                )
                                RecurrenceType.EVERY_N_DAYS -> value.copy(type = type, interval = value.interval.coerceAtLeast(2))
                                else -> value.copy(type = type)
                            },
                        )
                    },
                )
            }
        }
        if (value.type == RecurrenceType.WEEKLY) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DayOfWeek.entries.forEach { day ->
                    val on = value.daysMask and day.bit != 0
                    val bg by animateColorAsState(if (on) colors.accent else colors.chip, Motion.snappy(), label = "dow")
                    val scale = animateFloatAsState(if (on) 1f else 0.9f, Motion.bouncy(), label = "dowScale")
                    Box(
                        Modifier
                            .size(38.dp)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                            .bouncyClickable(glowColor = colors.accent, glowShape = CircleShape) {
                                val mask = value.daysMask xor day.bit
                                if (mask != 0) onChange(value.copy(daysMask = mask))
                            }
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.getDisplayName(JTextStyle.SHORT, RuLocale),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) Color.White else colors.textPrimary,
                        )
                    }
                }
            }
        }
        if (value.type == RecurrenceType.EVERY_N_DAYS) {
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepperButton("−") { onChange(value.copy(interval = (value.interval - 1).coerceAtLeast(2))) }
                Text(
                    "каждые ${value.interval} ${daysWord(value.interval)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                StepperButton("+") { onChange(value.copy(interval = (value.interval + 1).coerceAtMost(365))) }
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .bouncyClickable(glowColor = WoodyTheme.colors.textPrimary, glowShape = CircleShape, onClick = onClick)
            .clip(CircleShape)
            .background(WoodyTheme.colors.chip),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = WoodyTheme.colors.textPrimary)
    }
}

@Composable
private fun SubtaskEditor(subtasks: List<DraftSubtask>, onChange: (List<DraftSubtask>) -> Unit, newKey: () -> Long) {
    val colors = WoodyTheme.colors
    var pending by remember { mutableStateOf("") }
    Column(Modifier.animateContentSize(Motion.soft())) {
        subtasks.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(
                    Modifier
                        .padding(start = 6.dp, end = 12.dp)
                        .size(8.dp)
                        .background(colors.textTertiary, CircleShape),
                )
                PlainField(
                    value = sub.title,
                    onValueChange = { text -> onChange(subtasks.map { if (it.key == sub.key) it.copy(title = text) else it }) },
                    placeholder = "Пункт",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    WoodyIcons.Close,
                    contentDescription = "Убрать пункт",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .bouncyClickable { onChange(subtasks.filterNot { it.key == sub.key }) }
                        .padding(8.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Icon(
                WoodyIcons.Plus,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .padding(start = 2.dp, end = 8.dp)
                    .size(16.dp),
            )
            val add = {
                if (pending.isNotBlank()) {
                    onChange(subtasks + DraftSubtask(key = newKey(), id = 0, title = pending.trim()))
                    pending = ""
                }
            }
            PlainField(
                value = pending,
                onValueChange = { pending = it },
                placeholder = "Добавить пункт",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Done,
                onImeAction = add,
            )
            AnimatedVisibility(pending.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Добавить",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                    modifier = Modifier
                        .bouncyClickable(onClick = add)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
) {
    val colors = WoodyTheme.colors
    val textStyle = style.copy(color = if (style.color == Color.Unspecified) colors.textPrimary else style.color)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = colors.textTertiary, fontWeight = FontWeight.Normal))
                }
                inner()
            }
        },
    )
}

private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    today.minusDays(1) -> "Вчера"
    else -> date.format(DateFormat)
}

private fun recurrenceLabel(r: Recurrence): String = when (r.type) {
    RecurrenceType.NONE -> "Нет"
    RecurrenceType.DAILY -> "Каждый день"
    RecurrenceType.WEEKLY -> {
        val days = DayOfWeek.entries.filter { r.daysMask and it.bit != 0 }
        when {
            days.size == 7 -> "Каждый день"
            days == DayOfWeek.entries.take(5) -> "По будням"
            days == DayOfWeek.entries.takeLast(2) -> "По выходным"
            else -> days.joinToString(", ") { it.getDisplayName(JTextStyle.SHORT, RuLocale) }
        }
    }
    RecurrenceType.EVERY_N_DAYS -> "Раз в ${r.interval} ${daysWord(r.interval)}"
    RecurrenceType.MONTHLY -> "Каждый месяц"
}

private fun pointsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "пунктов"
        mod10 == 1 -> "пункт"
        mod10 in 2..4 -> "пункта"
        else -> "пунктов"
    }
    return "$n $word"
}

private fun daysWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1 -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}
