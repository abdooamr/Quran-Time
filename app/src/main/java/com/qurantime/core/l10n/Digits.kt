package com.qurantime.core.l10n

import java.util.Locale

/** [n] written with Eastern Arabic digits in Arabic, Western digits otherwise. */
fun localDigits(n: Int, locale: Locale): String {
    val s = n.toString()
    if (locale.language != "ar") return s
    return buildString(s.length) { for (c in s) append(if (c in '0'..'9') '٠' + (c - '0') else c) }
}

/** Parses a number typed with Western, Eastern Arabic or Persian digits. */
fun parseLocalDigits(input: String): Int? {
    val t = input.trim()
    if (t.isEmpty()) return null
    val buf = StringBuilder(t.length)
    for (c in t) {
        buf.append(
            when (c) {
                in '0'..'9' -> c
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> return null
            },
        )
    }
    return buf.toString().toIntOrNull()
}
