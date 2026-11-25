package com.qurantime.kotlin.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.qurantime.kotlin.R

/** The book face used for surah names and page numerals. */
val BookFont = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

/** Material colours built around the palette, as the Flutter app's buildTheme. */
fun colorSchemeFor(palette: MushafPalette): ColorScheme {
    val dark = palette == MushafPalette.Dark
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val indicator = palette.field.copy(alpha = if (dark) 0.55f else 0.14f)
    return base.copy(
        primary = if (dark) Color(0xFF8FCBA9) else palette.field,
        onPrimary = if (dark) Color(0xFF0B2A1E) else palette.onField,
        secondaryContainer = indicator,
        tertiary = palette.gilt,
        background = palette.background,
        surface = palette.chrome,
        surfaceContainer = palette.chrome,
        surfaceContainerLow = palette.chrome,
        surfaceContainerHigh = palette.chrome,
        surfaceContainerLowest = palette.background,
        outlineVariant = palette.gilt.copy(alpha = 0.35f),
    )
}

@Composable
fun QuranTimeTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val palette = paletteFor(mode, isSystemInDarkTheme())
    val scheme = remember(palette) { colorSchemeFor(palette) }
    CompositionLocalProvider(LocalMushafPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
