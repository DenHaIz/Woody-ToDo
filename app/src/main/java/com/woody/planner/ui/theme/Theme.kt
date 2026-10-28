package com.woody.planner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Фирменные цвета Woody — те же, что в логотипе. */
object Brand {
    val Crimson = Color(0xFFE63946)
    val Blue = Color(0xFF2F5DA8)
    val Amber = Color(0xFFFFB703)
    val Cream = Color(0xFFFFF6E5)
    val Green = Color(0xFF2BB673)
}

@Immutable
data class WoodyColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val glassTint: Color,
    val glassStroke: Color,
    val scrim: Color,
    val success: Color,
    val warning: Color,
    val chip: Color,
)

private val LightColors = WoodyColors(
    isDark = false,
    background = Color(0xFFF6F1E9),
    card = Color.White.copy(alpha = 0.78f),
    cardBorder = Color.White.copy(alpha = 0.9f),
    textPrimary = Color(0xFF1B2233),
    textSecondary = Color(0xFF5F6678),
    textTertiary = Color(0xFF9097A6),
    accent = Brand.Crimson,
    glassTint = Color.White.copy(alpha = 0.32f),
    glassStroke = Color.White.copy(alpha = 0.65f),
    scrim = Color(0xFF1B2233).copy(alpha = 0.28f),
    success = Brand.Green,
    warning = Color(0xFFF2994A),
    chip = Color(0xFF1B2233).copy(alpha = 0.06f),
)

private val DarkColors = WoodyColors(
    isDark = true,
    background = Color(0xFF0D1220),
    card = Color(0xFF1C2438).copy(alpha = 0.74f),
    cardBorder = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFA3ACBE),
    textTertiary = Color(0xFF6E7890),
    accent = Color(0xFFFF5A64),
    glassTint = Color(0xFF1C2438).copy(alpha = 0.35f),
    glassStroke = Color.White.copy(alpha = 0.14f),
    scrim = Color.Black.copy(alpha = 0.45f),
    success = Color(0xFF3CCB86),
    warning = Color(0xFFFFB35C),
    chip = Color.White.copy(alpha = 0.08f),
)

object Shapes {
    val Card = RoundedCornerShape(24.dp)
    val Panel = RoundedCornerShape(32.dp)
    val Sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Chip = RoundedCornerShape(16.dp)
}

private val LocalWoodyColors = staticCompositionLocalOf { LightColors }

object WoodyTheme {
    val colors: WoodyColors
        @Composable @ReadOnlyComposable get() = LocalWoodyColors.current
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

@Composable
fun WoodyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            secondary = Brand.Blue,
            tertiary = Brand.Amber,
            background = colors.background,
            surface = Color(0xFF151B2B),
            onSurface = colors.textPrimary,
            onBackground = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            secondary = Brand.Blue,
            tertiary = Brand.Amber,
            background = colors.background,
            surface = Color(0xFFFFFBF5),
            onSurface = colors.textPrimary,
            onBackground = colors.textPrimary,
        )
    }
    CompositionLocalProvider(LocalWoodyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
