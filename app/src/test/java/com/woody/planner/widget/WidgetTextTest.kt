package com.woody.planner.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextTest {

    // Место под название в однострочном виджете на телефоне (~340×80 dp).
    private val width = 150f
    private val height = 64f

    @Test
    fun shortTitleIsLargeWithMeta() {
        val word = WidgetText.fit("Хлеб", width, height)
        assertEquals(1, word.lines)
        assertTrue(word.meta)
        assertTrue("кегль ${word.size}", word.size >= 22f)
        // Два слова — крупно (в одну или две строки), подпись со временем остаётся.
        val two = WidgetText.fit("Забрать машину", width, height)
        assertTrue(two.meta)
        assertTrue("кегль ${two.size}", two.size >= 18f)
    }

    @Test
    fun oneOrTwoWordsAreBigInATallerWidget() {
        // Однострочный виджет на телефоне владельца (~372×100 dp): место под название ~160×92.
        val word = WidgetText.fit("Хлеб", 160f, 92f)
        assertTrue("кегль ${word.size}", word.size >= 30f)
        assertTrue(word.meta)
        val two = WidgetText.fit("Забрать машину", 160f, 92f)
        assertTrue("кегль ${two.size}", two.size >= 24f)
        assertTrue(two.meta)
    }

    @Test
    fun wordsAreSplitIntoBalancedLines() {
        assertEquals(7, WidgetText.longestLine(listOf("Забрать", "машину"), 2))
        assertEquals(14, WidgetText.longestLine(listOf("Забрать", "машину"), 1))
        assertEquals(9, WidgetText.longestLine(listOf("Позвонить", "маме", "и", "спросить"), 3))
    }

    @Test
    fun longerTitleWrapsAndShrinks() {
        val short = WidgetText.fit("Забрать машину", width, height)
        val long = WidgetText.fit("Позвонить маме и спросить про выходные", width, height)
        assertEquals(2, long.lines)
        assertTrue(long.size < short.size)
        assertTrue(long.size >= 13f)
    }

    @Test
    fun veryLongTitleKeepsFiveWords() {
        val fit = WidgetText.fit("Записаться к стоматологу на следующую неделю после обеда и уточнить цену", width, height)
        assertEquals("Записаться к стоматологу на следующую…", fit.text)
    }

    @Test
    fun fewWordsStayAsIs() {
        assertEquals("Купить хлеб", WidgetText.shorten("  Купить   хлеб "))
    }
}
