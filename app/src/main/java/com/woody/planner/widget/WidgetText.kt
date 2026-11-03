package com.woody.planner.widget

/** Как показать название задачи в однострочном виджете: текст, кегль (в dp), число строк и строка-подпись. */
internal data class TitleFit(val text: String, val size: Float, val lines: Int, val metaSize: Float, val meta: Boolean)

/**
 * Подбор кегля названия под место в виджете. Виджет не умеет мерить текст (это RemoteViews),
 * поэтому ширина считается по средней ширине буквы и по тому, как слова лягут по строкам,
 * а остаток подстрахует многоточие.
 */
internal object WidgetText {
    /** Длиннее — обрезаем по словам: на рабочем столе хватает сути. */
    const val MaxWords = 5
    private const val CharWidth = 0.58f
    /** Межстрочный интервал Roboto в долях кегля и отступы шрифта у блока текста (сверху и снизу вместе). */
    private const val Line = 1.17f
    private const val Padding = 0.2f
    private const val MinSize = 13f
    private const val MaxLines = 3

    fun shorten(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size > MaxWords) words.take(MaxWords).joinToString(" ") + "…" else words.joinToString(" ")
    }

    fun metaSize(size: Float): Float = (size * 0.6f).coerceIn(11f, 19f)

    /** Высота блока текста из [lines] строк кеглем [size]. */
    private fun blockHeight(lines: Int, size: Float) = size * (Line * lines + Padding)

    /** Самая длинная строка (в символах) при лучшей раскладке слов на [lines] строк. */
    internal fun longestLine(words: List<String>, lines: Int): Int {
        fun len(from: Int, to: Int) = words.subList(from, to).sumOf { it.length } + (to - from - 1)
        val n = words.size
        return when {
            lines <= 1 || n <= 1 -> len(0, n)
            lines == 2 || n == 2 -> (1 until n).minOf { i -> maxOf(len(0, i), len(i, n)) }
            else -> (1 until n - 1).minOf { i -> (i + 1 until n).minOf { j -> maxOf(len(0, i), len(i, j), len(j, n)) } }
        }
    }

    /** Самый крупный кегль, с которым название (и по возможности подпись) помещается в [width]×[height] dp. */
    fun fit(title: String, width: Float, height: Float): TitleFit {
        val text = shorten(title)
        val words = text.split(" ")
        val max = (height * 0.5f).coerceIn(15f, 40f)
        var best: TitleFit? = null
        // Подпись (время, категория) нужна — без неё обходимся, только если иначе название совсем не влезает.
        for (meta in listOf(true, false)) {
            for (lines in 1..minOf(MaxLines, words.size)) {
                val longest = longestLine(words, lines)
                var size = minOf(max, width / (longest * CharWidth))
                while (size >= MinSize) {
                    val needed = blockHeight(lines, size) + if (meta) blockHeight(1, metaSize(size)) else 0f
                    if (needed <= height) break
                    size -= 0.5f
                }
                if (size < MinSize) continue
                val candidate = TitleFit(text, size, lines, metaSize(size), meta)
                // Строк поменьше, если кегль почти тот же: «Купить хлеб» одной строкой лучше двух.
                if (best == null || size > best.size * 1.08f) best = candidate
            }
            if (best != null) return best
        }
        return TitleFit(text, MinSize, if (blockHeight(2, MinSize) <= height) 2 else 1, metaSize(MinSize), meta = false)
    }
}
