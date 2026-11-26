package com.qurantime.feature.audio.ui

import com.qurantime.core.l10n.localDigits
import java.util.Locale

/** `1.5×`, `2×`. */
fun speedLabel(s: Float): String {
    val text = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()
    return "$text×"
}

/** `m:ss` or `h:mm:ss`, with Arabic-Indic digits in Arabic. */
fun formatClock(ms: Long, locale: Locale): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    val text = if (h > 0) "$h:${two(m)}:${two(s)}" else "${total / 60}:${two(s)}"
    if (locale.language != "ar") return text
    return text.map { c -> if (c.isDigit()) localDigits(c - '0', locale) else c.toString() }.joinToString("")
}
