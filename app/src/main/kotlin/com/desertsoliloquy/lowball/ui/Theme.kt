package com.desertsoliloquy.lowball.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors of the card table itself, which aren't part of the standard theme. */
data class TableColors(
    val felt: Color,
    val feltText: Color,
    val highlight: Color,
    val chip: Color,
)

private val LightTable = TableColors(
    felt = Color(0xFF2E7D4F),
    feltText = Color(0xFFFFFFFF),
    highlight = Color(0xFFFFD54F),
    chip = Color(0xFFC62828),
)

private val DarkTable = TableColors(
    felt = Color(0xFF14372A),
    feltText = Color(0xFFE6E6E6),
    highlight = Color(0xFFFFC107),
    chip = Color(0xFFEF5350),
)

val LocalTableColors = staticCompositionLocalOf { LightTable }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8B2E1F),
    onPrimary = Color.White,
    secondary = Color(0xFF6D5D3F),
    background = Color(0xFFF7F2E8),
    surface = Color(0xFFF7F2E8),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB4A2),
    onPrimary = Color(0xFF5A1A0F),
    secondary = Color(0xFFD8C4A0),
    background = Color(0xFF15120E),
    surface = Color(0xFF15120E),
)

@Composable
fun LowballTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTableColors provides if (dark) DarkTable else LightTable) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
