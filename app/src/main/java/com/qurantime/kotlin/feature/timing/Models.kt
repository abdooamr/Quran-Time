package com.qurantime.kotlin.feature.timing

import androidx.compose.ui.geometry.Offset
import com.qurantime.kotlin.core.api.cleanName
import com.qurantime.kotlin.core.api.objects
import com.qurantime.kotlin.core.api.parseInt
import com.qurantime.kotlin.core.api.parseIntOrNull
import com.qurantime.kotlin.core.api.stringOrNull
import com.qurantime.kotlin.core.api.upgradeToHttps
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A recording that has per-ayah timing (`/ayat_timing/reads`). */
data class TimedRead(val id: Int, val name: String, val riwaya: String, val folderUrl: String, val surahCount: Int) {
    companion object {
        fun fromJson(j: JsonObject) = TimedRead(
            id = parseInt(j["id"]),
            name = cleanName(j["name"]),
            riwaya = cleanName(j["rewaya"]),
            folderUrl = upgradeToHttps(j["folder_url"].stringOrNull().orEmpty()),
            surahCount = parseIntOrNull(j["soar_count"]) ?: 0,
        )
    }
}

/** Where one ayah sits in the audio (ms) and on its Mushaf page (viewBox units). */
data class AyahTiming(
    val ayah: Int,
    val startMs: Long,
    val endMs: Long,
    val polygon: List<Offset>? = null,
    val marker: Offset? = null,
    val page: Int? = null,
) {
    companion object {
        fun fromJson(j: JsonObject): AyahTiming {
            val poly = j["polygon"].stringOrNull()
            val x = j["x"].stringOrNull()?.toFloatOrNull()
            val y = j["y"].stringOrNull()?.toFloatOrNull()
            return AyahTiming(
                ayah = parseInt(j["ayah"]),
                startMs = parseInt(j["start_time"]).toLong(),
                endMs = parseInt(j["end_time"]).toLong(),
                polygon = if (poly.isNullOrBlank()) null else parsePolygon(poly),
                marker = if (x != null && y != null) Offset(x, y) else null,
                page = pageFromSvgUrl(j["page"].stringOrNull()),
            )
        }
    }
}

private val SPACES = Regex("""\s+""")

/** `"x,y x,y ..."` as points. */
fun parsePolygon(s: String): List<Offset> = s.trim().split(SPACES).filter { ',' in it }.map { pair ->
    val (x, y) = pair.split(',')
    Offset(x.toFloat(), y.toFloat())
}

private val SVG_PAGE = Regex("""(\d+)\.svg$""")

fun pageFromSvgUrl(url: String?): Int? = url?.trim()?.let { SVG_PAGE.find(it)?.groupValues?.get(1)?.toInt() }

fun parseTimedReads(json: JsonElement): List<TimedRead> = json.objects().map(TimedRead::fromJson)

fun parseAyahTimings(json: JsonElement): List<AyahTiming> = json.objects().map(AyahTiming::fromJson)

fun parseTimedSurahs(json: JsonElement): List<Int> = json.objects().map { parseInt(it["id"]) }
