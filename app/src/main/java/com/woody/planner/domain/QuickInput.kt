package com.woody.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * «Ввод обычным языком»: из названия задачи достаются дата, время и повтор —
 * «купить курицу завтра в 18», «спортзал по понедельникам в 7 вечера», «call mom tomorrow at 6pm».
 * Понимает русский и английский одновременно. Работает целиком на телефоне.
 */
object QuickInput {

    data class Result(
        val date: LocalDate?,
        val time: LocalTime?,
        val recurrence: Recurrence?,
        /** Распознанные фрагменты названия — подсвечиваются и вырезаются при сохранении. */
        val spans: List<IntRange>,
    ) {
        /** Подпись для сравнения: тот же ли набор фраз распознан. */
        fun signature(text: String): String = spans.joinToString("|") { text.substring(it.first, it.last + 1).lowercase() }
    }

    fun parse(text: String, today: LocalDate): Result? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val candidates = Rules.flatMap { rule ->
            rule.regex.findAll(lower).mapNotNull { m -> rule.read(m, today)?.let { Candidate(m.range, rule.kind, it) } }.toList()
        }
        // Непересекающиеся фрагменты: раньше в тексте и длиннее — важнее.
        val chosen = mutableListOf<Candidate>()
        candidates.sortedWith(compareBy<Candidate> { it.range.first }.thenByDescending { it.range.last - it.range.first })
            .forEach { c -> if (chosen.none { it.range.overlaps(c.range) }) chosen += c }
        if (chosen.isEmpty()) return null

        var date: LocalDate? = null
        var time: LocalTime? = null
        var partOfDay: PartOfDay? = null
        var recurrence: Recurrence? = null
        for (c in chosen) {
            val v = c.value
            when (c.kind) {
                Kind.Date -> if (date == null) date = v.date
                Kind.Time -> if (time == null) time = v.time
                Kind.PartOfDay -> if (partOfDay == null) partOfDay = v.partOfDay
                Kind.Repeat -> if (recurrence == null) {
                    recurrence = v.recurrence
                    if (date == null) date = v.date
                }
            }
        }
        // «Вечером в 7» → 19:00; одно «вечером» → 19:00.
        val pod = partOfDay
        if (pod != null) {
            time = when {
                time == null -> pod.default
                pod.afternoon && time.hour < 12 -> time.plusHours(12)
                else -> time
            }
        }
        return Result(date, time, recurrence, chosen.map { it.range }.sortedBy { it.first })
    }

    /** Название без распознанных фрагментов: «Купить курицу завтра в 18» → «Купить курицу». */
    fun strip(text: String, spans: List<IntRange>): String {
        val sb = StringBuilder()
        var last = 0
        for (r in spans.sortedBy { it.first }) {
            if (r.first < last || r.last >= text.length) continue
            sb.append(text, last, r.first)
            last = r.last + 1
        }
        sb.append(text.substring(last.coerceAtMost(text.length)))
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?;:])"), "$1")
            .trim()
            .trim(',', ';', '—', '-', ' ')
            .ifBlank { text.trim() }
    }

    // --- правила ---

    private enum class Kind { Date, Time, PartOfDay, Repeat }

    private class PartOfDay(val default: LocalTime, val afternoon: Boolean)

    private class Value(
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val partOfDay: PartOfDay? = null,
        val recurrence: Recurrence? = null,
    )

    private class Candidate(val range: IntRange, val kind: Kind, val value: Value)

    private class Rule(val kind: Kind, pattern: String, val read: (MatchResult, LocalDate) -> Value?) {
        val regex = Regex(pattern)
    }

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last

    /** Границы слова, понимающие кириллицу. */
    private const val B = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"

    private val WeekdayForms: List<Pair<DayOfWeek, String>> = listOf(
        DayOfWeek.MONDAY to "понедельник|monday",
        DayOfWeek.TUESDAY to "вторник|tuesday",
        DayOfWeek.WEDNESDAY to "среду|среда|wednesday",
        DayOfWeek.THURSDAY to "четверг|thursday",
        DayOfWeek.FRIDAY to "пятницу|пятница|friday",
        DayOfWeek.SATURDAY to "субботу|суббота|saturday",
        DayOfWeek.SUNDAY to "воскресенье|sunday",
    )

    /** «По понедельникам», «every monday». */
    private val WeekdayPlural: List<Pair<DayOfWeek, String>> = listOf(
        DayOfWeek.MONDAY to "понедельникам|mondays",
        DayOfWeek.TUESDAY to "вторникам|tuesdays",
        DayOfWeek.WEDNESDAY to "средам|wednesdays",
        DayOfWeek.THURSDAY to "четвергам|thursdays",
        DayOfWeek.FRIDAY to "пятницам|fridays",
        DayOfWeek.SATURDAY to "субботам|saturdays",
        DayOfWeek.SUNDAY to "воскресеньям|sundays",
    )

    private val Months: List<String> = listOf(
        "января|january|jan", "февраля|february|feb", "марта|march|mar", "апреля|april|apr",
        "мая|may", "июня|june|jun", "июля|july|jul", "августа|august|aug",
        "сентября|september|sept|sep", "октября|october|oct", "ноября|november|nov", "декабря|december|dec",
    )

    private val allWeekdays = WeekdayForms.joinToString("|") { it.second }
    private val allPlural = WeekdayPlural.joinToString("|") { it.second }
    private val allMonths = Months.joinToString("|")

    private fun weekdayOf(word: String): DayOfWeek? =
        WeekdayForms.firstOrNull { (_, forms) -> word in forms.split('|') }?.first
            ?: WeekdayPlural.firstOrNull { (_, forms) -> word in forms.split('|') }?.first

    private fun monthOf(word: String): Int? = Months.indexOfFirst { word in it.split('|') }.takeIf { it >= 0 }?.plus(1)

    /** Ближайший такой день недели после сегодняшнего. */
    private fun next(today: LocalDate, day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.next(day))

    /** Дата без года: если уже прошла — в следующем году. */
    private fun dateOf(today: LocalDate, day: Int, month: Int, year: Int?): LocalDate? {
        val y = year?.let { if (it < 100) 2000 + it else it } ?: today.year
        val date = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
        return if (year == null && date < today) date.plusYears(1) else date
    }

    private fun clock(hour: Int, minute: Int, suffix: String?): LocalTime? {
        var h = hour
        when (suffix?.replace(".", "")) {
            "утра", "am" -> if (h == 12) h = 0
            "дня", "вечера", "pm" -> if (h < 12) h += 12
            "ночи" -> if (h == 12) h = 0
            null -> if (h in 1..6) h += 12 // «в 5» — скорее 17:00, чем 5 утра
        }
        if (h !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(h, minute)
    }

    private val Rules: List<Rule> = listOf(
        // --- повторы ---
        Rule(Kind.Repeat, "${B}(каждый день|ежедневно|every day|daily)$E") { _, _ ->
            Value(recurrence = Recurrence(RecurrenceType.DAILY))
        },
        Rule(Kind.Repeat, "${B}(по будням|по рабочим дням|on weekdays|every weekday|weekdays)$E") { _, _ ->
            Value(recurrence = Recurrence(RecurrenceType.WEEKLY, daysMask = Recurrence.maskOf(*DayOfWeek.entries.take(5).toTypedArray())))
        },
        Rule(Kind.Repeat, "${B}(по выходным|on weekends|every weekend|weekends)$E") { _, _ ->
            Value(recurrence = Recurrence(RecurrenceType.WEEKLY, daysMask = Recurrence.maskOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        },
        Rule(Kind.Repeat, "${B}(?:(?:каждый|каждую|каждое|every)\\s+($allWeekdays)|(?:по\\s+)?($allPlural))$E") { m, today ->
            val word = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val day = weekdayOf(word) ?: return@Rule null
            val start = if (today.dayOfWeek == day) today else next(today, day)
            Value(date = start, recurrence = Recurrence(RecurrenceType.WEEKLY, daysMask = day.bit))
        },
        Rule(Kind.Repeat, "${B}(каждую неделю|еженедельно|every week|weekly)$E") { _, _ ->
            Value(recurrence = Recurrence(RecurrenceType.WEEKLY))
        },
        Rule(Kind.Repeat, "${B}(каждый месяц|ежемесячно|every month|monthly)$E") { _, _ ->
            Value(recurrence = Recurrence(RecurrenceType.MONTHLY))
        },
        Rule(Kind.Repeat, "${B}(?:каждые|every)\\s+(\\d{1,3})\\s+(?:дня|дней|days)$E") { m, _ ->
            val n = m.groupValues[1].toInt()
            if (n < 2) null else Value(recurrence = Recurrence(RecurrenceType.EVERY_N_DAYS, interval = n))
        },

        // --- даты ---
        Rule(Kind.Date, "${B}(послезавтра|day after tomorrow)$E") { _, today -> Value(date = today.plusDays(2)) },
        Rule(Kind.Date, "${B}(сегодня|today)$E") { _, today -> Value(date = today) },
        Rule(Kind.Date, "${B}(завтра|tomorrow)$E") { _, today -> Value(date = today.plusDays(1)) },
        Rule(Kind.Date, "${B}(?:(?:в|во|on|next|this)\\s+)?($allWeekdays)$E") { m, today ->
            weekdayOf(m.groupValues[1])?.let { Value(date = next(today, it)) }
        },
        Rule(Kind.Date, "${B}(?:через|in)\\s+(?:(\\d{1,3})\\s+)?(дня|дней|день|недели|недель|неделю|месяца|месяцев|месяц|days?|weeks?|months?)$E") { m, today ->
            val unit = m.groupValues[2]
            val n = m.groupValues[1].toIntOrNull()
            when {
                unit.startsWith("д") || unit.startsWith("day") -> n?.let { Value(date = today.plusDays(it.toLong())) }
                unit.startsWith("н") || unit.startsWith("week") -> Value(date = today.plusWeeks((n ?: 1).toLong()))
                else -> Value(date = today.plusMonths((n ?: 1).toLong()))
            }
        },
        Rule(Kind.Date, "${B}(\\d{1,2})\\s+($allMonths)(?:\\s+(\\d{4}))?$E") { m, today ->
            val month = monthOf(m.groupValues[2]) ?: return@Rule null
            dateOf(today, m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull())?.let { Value(date = it) }
        },
        Rule(Kind.Date, "${B}($allMonths)\\s+(\\d{1,2})$E") { m, today ->
            val month = monthOf(m.groupValues[1]) ?: return@Rule null
            dateOf(today, m.groupValues[2].toInt(), month, null)?.let { Value(date = it) }
        },
        // 15.11 или 15/11[/2026] — только если это не время («в 18.30»).
        Rule(Kind.Date, "${B}(?<!(?:в|at)\\s)(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?$E") { m, today ->
            val month = m.groupValues[2].toInt()
            if (month !in 1..12) return@Rule null
            dateOf(today, m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull())?.let { Value(date = it) }
        },

        // --- время ---
        Rule(Kind.Time, "${B}(?:в|во|к|at|@)\\s*(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:час(?:а|ов)?))?(?:\\s*(утра|дня|вечера|ночи|am|pm|a\\.m\\.|p\\.m\\.))?$E") { m, _ ->
            clock(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3].ifEmpty { null })
                ?.let { Value(time = it) }
        },
        Rule(Kind.Time, "${B}(\\d{1,2}):(\\d{2})$E") { m, _ ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) Value(time = LocalTime.of(h, min)) else null
        },
        Rule(Kind.Time, "${B}(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$E") { m, _ ->
            clock(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3])?.let { Value(time = it) }
        },
        Rule(Kind.Time, "${B}(?:в\\s+)?(полдень)$E|${B}(?:at\\s+)?(noon)$E") { _, _ -> Value(time = LocalTime.NOON) },
        Rule(Kind.PartOfDay, "${B}(утром|in the morning|morning)$E") { _, _ -> Value(partOfDay = PartOfDay(LocalTime.of(9, 0), afternoon = false)) },
        Rule(Kind.PartOfDay, "${B}(дн[её]м|in the afternoon|afternoon)$E") { _, _ -> Value(partOfDay = PartOfDay(LocalTime.of(13, 0), afternoon = true)) },
        Rule(Kind.PartOfDay, "${B}(вечером|in the evening|evening|tonight)$E") { _, _ ->
            Value(partOfDay = PartOfDay(LocalTime.of(19, 0), afternoon = true))
        },
    )
}
