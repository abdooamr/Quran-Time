package com.qurantime.feature.reciters

import com.qurantime.core.api.cleanName
import com.qurantime.core.api.objectsAt
import com.qurantime.core.api.parseDate
import com.qurantime.core.api.parseInt
import com.qurantime.core.api.parseIntOrNull
import com.qurantime.core.api.parseSurahList
import com.qurantime.core.api.stringOrNull
import com.qurantime.core.api.surahAudioUrl
import com.qurantime.core.api.upgradeToHttps
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One recording by a reciter: a riwaya + style, with its audio folder. */
class Moshaf(
    val id: Int,
    val name: String,
    server: String,
    val surahTotal: Int,
    val moshafType: Int,
    val surahs: List<Int>,
) {
    /** https, always ending with `/`. */
    val server: String = upgradeToHttps(server).let { if (it.endsWith('/')) it else "$it/" }
    private val surahSet = surahs.toHashSet()

    fun has(surah: Int): Boolean = surah in surahSet

    /** The MP3 URL for [surah], or null when this recording doesn't have it. */
    fun audioUrl(surah: Int): String? = if (has(surah)) surahAudioUrl(server, surah) else null

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("server", server)
        put("surah_total", surahTotal)
        put("moshaf_type", moshafType)
        put("surah_list", surahs.joinToString(","))
    }

    companion object {
        fun fromJson(j: JsonObject) = Moshaf(
            id = parseInt(j["id"]),
            name = cleanName(j["name"]),
            server = j["server"].stringOrNull().orEmpty(),
            surahTotal = parseIntOrNull(j["surah_total"]) ?: 0,
            moshafType = parseIntOrNull(j["moshaf_type"]) ?: 0,
            surahs = parseSurahList(j["surah_list"]),
        )
    }
}

class Reciter(
    val id: Int,
    val name: String,
    val letter: String,
    /** Epoch ms of when the reciter was added or changed, if known. */
    val date: Long? = null,
    /** Epoch ms of the latest recording (in `/recent_reads`). */
    val recentDate: Long? = null,
    val moshaf: List<Moshaf>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("letter", letter)
        date?.let { put("date", iso(it)) }
        recentDate?.let { put("recent_date", iso(it)) }
        put("moshaf", JsonArray(moshaf.map { it.toJson() }))
    }

    override fun equals(other: Any?) = other is Reciter && other.id == id
    override fun hashCode() = id
    override fun toString() = "Reciter($id $name)"

    companion object {
        fun fromJson(j: JsonObject) = Reciter(
            id = parseInt(j["id"]),
            name = cleanName(j["name"]),
            letter = cleanName(j["letter"]),
            date = parseDate(j["date"]),
            recentDate = parseDate(j["recent_date"]),
            moshaf = j.objectsAt("moshaf").map(Moshaf::fromJson),
        )

        private fun iso(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(ms)
    }
}

fun parseReciters(json: JsonElement): List<Reciter> = json.objectsAt("reciters").map(Reciter::fromJson)

fun parseRecentReads(json: JsonElement): List<Reciter> = json.objectsAt("reads").map(Reciter::fromJson)
