package com.woody.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class QuickInputTest {
    /** Суббота. */
    private val today = LocalDate.of(2026, 10, 31)

    private fun parse(text: String) = QuickInput.parse(text, today)

    private fun cleaned(text: String) = parse(text)?.let { QuickInput.strip(text, it.spans) } ?: text

    @Test
    fun tomorrowAtHour() {
        val r = parse("Купить курицу завтра в 18")!!
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
        assertEquals("Купить курицу", cleaned("Купить курицу завтра в 18"))
    }

    @Test
    fun timeWithMinutesAndPartOfDay() {
        assertEquals(LocalTime.of(18, 30), parse("созвон в 18:30")!!.time)
        assertEquals(LocalTime.of(18, 30), parse("созвон в 18.30")!!.time)
        assertEquals(LocalTime.of(19, 0), parse("позвонить маме вечером в 7")!!.time)
        assertEquals(LocalTime.of(7, 0), parse("пробежка в 7 утра")!!.time)
        assertEquals(LocalTime.of(19, 0), parse("кино в 7 вечера")!!.time)
        assertEquals(LocalTime.of(9, 0), parse("зарядка утром")!!.time)
        assertEquals(LocalTime.of(12, 0), parse("обед в полдень")!!.time)
        // «в 5» без уточнения — вечер, а не 5 утра.
        assertEquals(LocalTime.of(17, 0), parse("забрать посылку в 5")!!.time)
    }

    @Test
    fun weekdaysAndRelativeDates() {
        assertEquals(LocalDate.of(2026, 11, 2), parse("отчёт в понедельник")!!.date)
        assertEquals(LocalDate.of(2026, 11, 4), parse("стрижка в среду в 15")!!.date)
        // В субботу «в субботу» — это следующая суббота.
        assertEquals(LocalDate.of(2026, 11, 7), parse("рынок в субботу")!!.date)
        assertEquals(today.plusDays(2), parse("врач послезавтра")!!.date)
        assertEquals(today.plusDays(3), parse("сдать книгу через 3 дня")!!.date)
        assertEquals(today.plusWeeks(1), parse("ТО машины через неделю")!!.date)
        assertEquals(LocalDate.of(2026, 11, 15), parse("день рождения 15 ноября")!!.date)
        assertEquals(LocalDate.of(2026, 12, 20), parse("билеты 20.12")!!.date)
        // Дата в прошлом без года — следующий год.
        assertEquals(LocalDate.of(2027, 3, 8), parse("цветы 8 марта")!!.date)
    }

    @Test
    fun repeats() {
        assertEquals(RecurrenceType.DAILY, parse("таблетки каждый день в 9")!!.recurrence!!.type)
        val weekdays = parse("планёрка по будням в 10")!!
        assertEquals(RecurrenceType.WEEKLY, weekdays.recurrence!!.type)
        assertEquals(Recurrence.maskOf(*DayOfWeek.entries.take(5).toTypedArray()), weekdays.recurrence!!.daysMask)
        assertEquals(LocalTime.of(10, 0), weekdays.time)
        val mondays = parse("Спортзал по понедельникам в 7 вечера")!!
        assertEquals(DayOfWeek.MONDAY.bit, mondays.recurrence!!.daysMask)
        assertEquals(LocalDate.of(2026, 11, 2), mondays.date)
        assertEquals(LocalTime.of(19, 0), mondays.time)
        assertEquals("Спортзал", cleaned("Спортзал по понедельникам в 7 вечера"))
        val every3 = parse("полить цветы каждые 3 дня")!!.recurrence!!
        assertEquals(RecurrenceType.EVERY_N_DAYS, every3.type)
        assertEquals(3, every3.interval)
        assertEquals(RecurrenceType.MONTHLY, parse("оплатить интернет каждый месяц")!!.recurrence!!.type)
    }

    @Test
    fun english() {
        val r = parse("Call mom tomorrow at 6pm")!!
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
        assertEquals("Call mom", cleaned("Call mom tomorrow at 6pm"))
        assertEquals(LocalDate.of(2026, 11, 6), parse("dentist on friday 10:30")!!.date)
        assertEquals(LocalTime.of(10, 30), parse("dentist on friday 10:30")!!.time)
        assertEquals(RecurrenceType.WEEKLY, parse("gym every monday")!!.recurrence!!.type)
        assertEquals(today.plusDays(5), parse("return book in 5 days")!!.date)
        assertEquals(LocalDate.of(2026, 11, 15), parse("party nov 15")!!.date)
    }

    @Test
    fun ordinaryTitlesStayUntouched() {
        assertNull(parse("Купить курицу"))
        assertNull(parse("Купить 2 молока"))
        assertNull(parse("Зайти в 5ку за хлебом"))
        assertNull(parse("Прочитать «Мастера и Маргариту»"))
        assertNull(parse("Позвонить в банк"))
        assertNull(parse("Buy milk"))
        assertNull(parse(""))
    }
}
