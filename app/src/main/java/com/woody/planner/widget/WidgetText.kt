package com.woody.planner.widget

/** Как показать название задачи в однострочном виджете: текст, кегль (в dp), число строк и строка-подпись. */
internal data class TitleFit(val text: String, val size: Float, val lines: Int, val metaSize: Float, val meta: Boolean)

/**
 * Подбор кегля названия под место в виджете. Виджет не умеет мерить текст (это RemoteViews),
 * поэтому ширина считается по средней ширине буквы — с запасом, а остаток подстрахует многоточие.
 */
internal object WidgetText {
    /** Длиннее — обрезаем по словам: на рабочем столе хватает сути. */
    const val MaxWords = 5
    private const val CharWidth = 0.58f
    /** Высота строки TextView в долях кегля, с отступами шрифта — с запасом, чтобы низ не обрезался. */
    private const val LineHeight = 1.3f
    /** Перенос по словам оставляет пустоту в конце первой строки. */
    private const val WrapSlack = 0.85f
    private const val MinSize = 13f

    fun shorten(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size > MaxWords) words.take(MaxWords).joinToString(" ") + "…" else words.joinToString(" ")
    }

    fun metaSize(size: Float): Float = (size * 0.68f).coerceIn(11f, 16f)

    /** Самый крупный кегль, с которым название помещается в [width]×[height] dp. */
    fun fit(title: String, width: Float, height: Float): TitleFit {
        val text = shorten(title)
        val max = (height * 0.36f).coerceIn(15f, 24f)
        // Короткое — одной строкой и крупно; длинное — в две строки; подпись (время, категория)
        // убираем только если иначе название не помещается.
        val attempts = listOf(
            Attempt(lines = 1, meta = true, min = max * 0.8f),
            Attempt(lines = 2, meta = true, min = MinSize),
            Attempt(lines = 1, meta = true, min = MinSize),
            Attempt(lines = 2, meta = false, min = MinSize),
        )
        for (attempt in attempts) {
            var size = max
            while (size >= attempt.min) {
                val perLine = width / (size * CharWidth)
                val capacity = if (attempt.lines == 1) perLine else perLine * 2 * WrapSlack
                val needed = attempt.lines * size * LineHeight + if (attempt.meta) metaSize(size) * LineHeight else 0f
                if (text.length <= capacity && needed <= height) return TitleFit(text, size, attempt.lines, metaSize(size), attempt.meta)
                size -= 0.5f
            }
        }
        val lines = if (2 * MinSize * LineHeight <= height) 2 else 1
        return TitleFit(text, MinSize, lines, metaSize(MinSize), meta = false)
    }

    private class Attempt(val lines: Int, val meta: Boolean, val min: Float)
}
