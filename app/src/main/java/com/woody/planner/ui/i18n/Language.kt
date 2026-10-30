package com.woody.planner.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.woody.planner.data.AppLanguage
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Язык интерфейса; меняется в настройках и сразу перерисовывает весь экран. */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.Russian }

/** Строка на языке приложения: русский и английский варианты стоят рядом, прямо в месте использования. */
@Composable
@ReadOnlyComposable
fun tr(ru: String, en: String): String = LocalLanguage.current.pick(ru, en)

/** Локаль для дат и названий дней/месяцев. */
val appLocale: Locale
    @Composable
    @ReadOnlyComposable
    get() = LocalLanguage.current.locale

private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

/** Дата по шаблону языка: «31 октября» / «October 31». Форматтеры кэшируются. */
fun AppLanguage.format(value: TemporalAccessor, ru: String, en: String): String {
    val pattern = pick(ru, en)
    return formatters.getOrPut("$name|$pattern") { DateTimeFormatter.ofPattern(pattern, locale) }.format(value)
}

/** «31 октября» / «October 31». */
fun AppLanguage.dayMonth(value: TemporalAccessor): String = format(value, "d MMMM", "MMMM d")

/** «31 окт.» / «Oct 31». */
fun AppLanguage.dayMonthShort(value: TemporalAccessor): String = format(value, "d MMM", "MMM d")

/** Слово после числа: русские формы для 1, 2–4 и 5+ («1 задача», «3 задачи», «5 задач»), английские — ед./мн. */
fun AppLanguage.plural(n: Int, one: String, few: String, many: String, enOne: String, enMany: String): String {
    if (this == AppLanguage.English) return if (n == 1) enOne else enMany
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
