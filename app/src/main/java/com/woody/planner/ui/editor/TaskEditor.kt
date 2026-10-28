package com.woody.planner.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.woody.planner.data.DraftSubtask
import com.woody.planner.data.TaskDraft
import com.woody.planner.domain.Category
import com.woody.planner.domain.Recurrence
import com.woody.planner.domain.RecurrenceType
import com.woody.planner.domain.ReminderMath
import com.woody.planner.domain.bit
import com.woody.planner.ui.components.GlassSheet
import com.woody.planner.ui.components.RuLocale
import com.woody.planner.ui.components.WoodyChip
import com.woody.planner.ui.components.bouncyClickable
import com.woody.planner.ui.theme.Motion
import com.woody.planner.ui.theme.Shapes
import com.woody.planner.ui.theme.WoodyIcons
import com.woody.planner.ui.theme.WoodyTheme
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("d MMM", RuLocale)

private val ReminderOptions = listOf(0 to "В момент", 10 to "За 10 мин", 30 to "За 30 мин", 60 to "За час")

/**
 * Редактор задачи в стеклянном листе.
 * [onSave] вызывается с готовым черновиком, [onDelete] — для существующих задач.
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
    // Держим последний черновик, чтобы лист доигрывал анимацию закрытия с содержимым.
    var last by remember { mutableStateOf(draft) }
    if (draft != null) last = draft

    GlassSheet(visible = draft != null, onDismiss = onDismiss) {
        val initial = last ?: return@GlassSheet
        EditorContent(initial, categories, today, onSave, onDelete, onDismiss, onReminderEnabled)
    }
}

@Composable
private fun EditorContent(
    initial: TaskDraft,
    categories: List<Category>,
    today: LocalDate,
    onSave: (TaskDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    onReminderEnabled: () -> Unit,
) {
    val colors = WoodyTheme.colors
    var draft by remember(initial) { mutableStateOf(initial) }
    var nextKey by remember(initial) { mutableLongStateOf(-1L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(initial) {
        if (initial.isNew) {
            delay(280)
            titleFocus.requestFocus()
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (initial.isNew) "Новая задача" else "Задача",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                WoodyIcons.Close,
                contentDescription = "Закрыть",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bouncyClickable(onClick = onDismiss)
                    .padding(7.dp),
            )
        }

        PlainField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            placeholder = "Что нужно сделать?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocus)
                .padding(vertical = 6.dp),
            imeAction = ImeAction.Next,
        )
        PlainField(
            value = draft.note,
            onValueChange = { draft = draft.copy(note = it) },
            placeholder = "Заметка",
            style = MaterialTheme.typography.bodyLarge.copy(color = colors.textSecondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            singleLine = false,
        )

        EditorSection("Категория") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WoodyChip(
                    label = "Без категории",
                    selected = draft.categoryId == null,
                    onClick = { draft = draft.copy(categoryId = null) },
                    selectedColor = colors.textPrimary,
                )
                categories.forEach { category ->
                    WoodyChip(
                        label = category.name,
                        leadingText = category.emoji,
                        selected = draft.categoryId == category.id,
                        onClick = { draft = draft.copy(categoryId = category.id) },
                        selectedColor = Color(category.colorArgb),
                    )
                }
            }
        }

        EditorSection("Когда") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tomorrow = today.plusDays(1)
                WoodyChip("Сегодня", draft.date == today, { draft = draft.copy(date = today) })
                WoodyChip("Завтра", draft.date == tomorrow, { draft = draft.copy(date = tomorrow) })
                val custom = draft.date != today && draft.date != tomorrow
                WoodyChip(
                    label = if (custom) draft.date.format(DateFormat) else "Дата…",
                    selected = custom,
                    onClick = { showDatePicker = true },
                    icon = WoodyIcons.Calendar,
                )
                WoodyChip(
                    label = draft.time?.format(TimeFormat) ?: "Время",
                    selected = draft.time != null,
                    onClick = { showTimePicker = true },
                    icon = WoodyIcons.Clock,
                    selectedColor = colors.textPrimary,
                )
                AnimatedVisibility(draft.time != null, enter = fadeIn(), exit = fadeOut()) {
                    WoodyChip("Без времени", false, { draft = draft.copy(time = null) })
                }
            }
        }

        EditorSection("Напоминание") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WoodyChip("Нет", draft.reminderOffsetMin == null, { draft = draft.copy(reminderOffsetMin = null) })
                ReminderOptions.forEach { (minutes, label) ->
                    WoodyChip(
                        label = label,
                        selected = draft.reminderOffsetMin == minutes,
                        icon = WoodyIcons.Bell,
                        onClick = {
                            if (draft.reminderOffsetMin == null) onReminderEnabled()
                            draft = draft.copy(reminderOffsetMin = minutes)
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = draft.reminderOffsetMin != null && draft.time == null,
                enter = expandVertically(Motion.soft()) + fadeIn(),
                exit = shrinkVertically(Motion.soft()) + fadeOut(),
            ) {
                Text(
                    "Время не задано — напомню в ${ReminderMath.DefaultTime.format(TimeFormat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }

        EditorSection("Повтор") {
            RecurrenceEditor(draft.recurrence, draft.date) { draft = draft.copy(recurrence = it) }
        }

        EditorSection("Чек-лист") {
            SubtaskEditor(
                subtasks = draft.subtasks,
                onChange = { draft = draft.copy(subtasks = it) },
                newKey = { nextKey.also { nextKey-- } },
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!initial.isNew) {
                Box(
                    Modifier
                        .size(54.dp)
                        .bouncyClickable { onDelete(initial.id) }
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
                    .bouncyClickable(enabled = enabled) { onSave(draft) }
                    .clip(Shapes.Pill)
                    .background(saveColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (initial.isNew) "Добавить" else "Сохранить",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        draft = draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("Отмена") } },
        ) {
            DatePicker(state)
        }
    }

    if (showTimePicker) {
        val initialTime = draft.time ?: LocalTime.now().plusHours(1).withMinute(0)
        val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton({
                    draft = draft.copy(time = LocalTime.of(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton({ showTimePicker = false }) { Text("Отмена") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title.uppercase(RuLocale),
            style = MaterialTheme.typography.labelSmall,
            color = WoodyTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        content()
    }
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
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                            .bouncyClickable {
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
            .bouncyClickable(onClick = onClick)
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
