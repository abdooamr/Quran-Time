package com.qurantime.kotlin.core.api

import java.util.Calendar
import java.util.TimeZone
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Tolerant readers for the API's loosely typed JSON.

private fun JsonElement?.primitiveOrNull(): JsonPrimitive? =
    if (this == null || this is JsonNull) null else this as? JsonPrimitive

/** An int that may arrive as a number, a decimal or a string. Throws [NumberFormatException]. */
fun parseInt(v: JsonElement?): Int {
    val p = v.primitiveOrNull() ?: throw NumberFormatException("Not an int: $v")
    val c = p.content.trim()
    return c.toIntOrNull() ?: c.toDoubleOrNull()?.takeIf { !p.isString }?.toInt()
        ?: throw NumberFormatException("Not an int: $v")
}

fun parseIntOrNull(v: JsonElement?): Int? = try {
    parseInt(v)
} catch (_: NumberFormatException) {
    null
}

/** Trims spaces and CR/LF; null becomes "". */
fun cleanName(v: JsonElement?): String = v.primitiveOrNull()?.content?.trim().orEmpty()

/** The API's comma-separated `surah_list`. */
fun parseSurahList(v: JsonElement?): List<Int> {
    val s = v.primitiveOrNull()?.content ?: return emptyList()
    return s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
}

private val DATE = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?""")

/**
 * An ISO-8601 date-time as epoch milliseconds (UTC when no zone is given), or
 * null. Hand-parsed so the app needs no java.time desugaring on API 24/25.
 */
fun parseDate(v: JsonElement?): Long? {
    val s = v.primitiveOrNull()?.content?.trim() ?: return null
    val m = DATE.matchEntire(s) ?: return null
    val g = m.groupValues
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
    }
    var ms = cal.timeInMillis + (g[7].padEnd(3, '0').take(3).ifEmpty { "0" }.toInt())
    val zone = g[8]
    if (zone.isNotEmpty() && zone != "Z") {
        val sign = if (zone[0] == '-') -1 else 1
        val digits = zone.substring(1).replace(":", "")
        val offset = (digits.substring(0, 2).toInt() * 60 + digits.substring(2).toInt()) * 60_000L
        ms -= sign * offset
    }
    return ms
}

operator fun JsonElement.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
