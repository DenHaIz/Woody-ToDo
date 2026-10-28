package com.woody.planner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Собственный минималистичный набор линейных иконок (скруглённые концы, толщина 2). */
object WoodyIcons {
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Calendar by lazy { icon("Calendar", "M7 3v3M17 3v3M4 9h16M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z") }
    val Grid by lazy {
        icon(
            "Grid",
            "M6 4h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" +
                "M15 4h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" +
                "M6 13h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z" +
                "M15 13h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        )
    }
    val Sliders by lazy { icon("Sliders", "M4 7h9M17 7h3M4 17h3M11 17h9M15 5v4M9 15v4") }
    val Bell by lazy { icon("Bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15L6 16.5zM10 20.5a2 2 0 0 0 4 0") }
    val Clock by lazy { icon("Clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7.5V12l3 2") }
    val Repeat by lazy { icon("Repeat", "M17 3l3 3-3 3M4 11.5V10a4 4 0 0 1 4-4h12M7 21l-3-3 3-3M20 12.5V14a4 4 0 0 1-4 4H4") }
    val Trash by lazy { icon("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3") }
    val ArrowRight by lazy { icon("ArrowRight", "M5 12h14M13 6l6 6-6 6") }
    val ChevronLeft by lazy { icon("ChevronLeft", "M15 6l-6 6 6 6") }
    val ChevronRight by lazy { icon("ChevronRight", "M9 6l6 6-6 6") }
    val ChevronDown by lazy { icon("ChevronDown", "M6 9l6 6 6-6") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Checklist by lazy { icon("Checklist", "M10 6h10M10 12h10M10 18h10M4 6l1.5 1.5L8 5M4 12l1.5 1.5L8 11M4 18l1.5 1.5L8 17") }
    val Note by lazy { icon("Note", "M6 3h9l4 4v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8 11h8M8 15h6") }
    val Skip by lazy { icon("Skip", "M6 6l8 6-8 6zM17 6v12") }
    val Undo by lazy { icon("Undo", "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
