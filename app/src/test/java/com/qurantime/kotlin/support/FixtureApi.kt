package com.qurantime.kotlin.support

import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.ApiException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Reads a captured API response from `src/test/resources/fixtures/`. */
fun loadFixture(name: String): String =
    requireNotNull(FixtureApi::class.java.classLoader!!.getResourceAsStream("fixtures/$name")) { "no fixture $name" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

fun loadFixtureJson(name: String): JsonElement = Json.parseToJsonElement(loadFixture(name))

data class ApiCall(val path: String, val query: Map<String, String>)

/** Maps a request to the captured fixture that answers it (as the Flutter tests do). */
fun fixtureHandler(path: String, q: Map<String, String>): JsonElement {
    val lang = if (q["language"] == "eng") "eng" else "ar"
    return when {
        path == "suwar" -> loadFixtureJson("suwar_$lang.json")
        path == "riwayat" -> loadFixtureJson("riwayat_eng.json")
        path == "moshaf" -> loadFixtureJson("moshaf_eng.json")
        path == "reciters" && q["reciter"] == "231" -> loadFixtureJson("reciters_231_eng.json")
        path == "reciters" -> loadFixtureJson("reciters_eng.json")
        path == "recent_reads" -> loadFixtureJson("recent_reads_eng.json")
        path == "radios" -> loadFixtureJson("radios_eng.json")
        path == "live-tv" -> loadFixtureJson("live_tv_eng.json")
        path == "video_types" -> loadFixtureJson("video_types_ar.json")
        path == "videos" -> loadFixtureJson("videos_ar.json")
        path == "tafasir" -> loadFixtureJson("tafasir_ar.json")
        path == "tafsir" -> loadFixtureJson("tafsir_1_114_ar.json")
        path == "tadabor" -> loadFixtureJson("tadabor_1_ar.json")
        path == "ayat_timing/reads" -> loadFixtureJson("ayat_timing_reads.json")
        path == "ayat_timing/soar" -> loadFixtureJson("ayat_timing_soar_5.json")
        path == "ayat_timing" -> loadFixtureJson("ayat_timing_2_5.json")
        else -> throw ApiException(404, "no fixture for $path")
    }
}

/** An [Api] answered by [handler] (fixtures by default) that records every call. */
class FixtureApi(
    var handler: (path: String, query: Map<String, String>) -> JsonElement = ::fixtureHandler,
) : Api {
    val calls = mutableListOf<ApiCall>()

    override suspend fun getJson(path: String, query: Map<String, Any?>): JsonElement {
        val q = query.filterValues { it != null }.mapValues { it.value.toString() }
        calls += ApiCall(path, q)
        return handler(path, q)
    }

    fun callsTo(path: String): List<ApiCall> = calls.filter { it.path == path }
}
