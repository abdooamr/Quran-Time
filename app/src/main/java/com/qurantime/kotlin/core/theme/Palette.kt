package com.qurantime.kotlin.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode { SYSTEM, LIGHT, SEPIA, DARK }

/**
 * The Madinah Mushaf Edition palette: page ground and ink, plus the
 * illumination colours (green field, gilt rules) used around the page.
 */
@Immutable
data class MushafPalette(
    /** Ground behind the page. */
    val background: Color,
    /** Colour the page's letters are drawn in. */
    val ink: Color,
    /** Translucent fill for the recited ayah. */
    val highlight: Color,
    /** Illuminated green field of cartouches. */
    val field: Color,
    val onField: Color,
    /** Gilt hairlines: frames, roundel rims, cartouche borders. */
    val gilt: Color,
    /** Bars and sheets around the page. */
    val chrome: Color,
) {
    companion object {
        val Light = MushafPalette(
            background = Color(0xFFF4EEDD), ink = Color(0xFF231F20), highlight = Color(0x552E7D5B),
            field = Color(0xFF2F6B4F), onField = Color(0xFFF7F1E3), gilt = Color(0xFFA97F33),
            chrome = Color(0xFFEBE3CF),
        )
        val Sepia = MushafPalette(
            background = Color(0xFFEBDDC2), ink = Color(0xFF4A3B28), highlight = Color(0x55B07D2B),
            field = Color(0xFF2F6B4F), onField = Color(0xFFF4EEDD), gilt = Color(0xFF9E7630),
            chrome = Color(0xFFE0CFAF),
        )
        val Dark = MushafPalette(
            background = Color(0xFF121212), ink = Color(0xFFE6E1D6), highlight = Color(0x664FB98A),
            field = Color(0xFF1D4434), onField = Color(0xFFEDE6D3), gilt = Color(0xFFC9A45A),
            chrome = Color(0xFF1A1A18),
        )
    }
}

fun paletteFor(mode: AppThemeMode, systemDark: Boolean): MushafPalette = when (mode) {
    AppThemeMode.LIGHT -> MushafPalette.Light
    AppThemeMode.SEPIA -> MushafPalette.Sepia
    AppThemeMode.DARK -> MushafPalette.Dark
    AppThemeMode.SYSTEM -> if (systemDark) MushafPalette.Dark else MushafPalette.Light
}

val LocalMushafPalette = staticCompositionLocalOf { MushafPalette.Light }
