package com.qurantime.feature.explore

import com.qurantime.core.api.cleanName
import com.qurantime.core.api.objectsAt
import com.qurantime.core.api.parseInt
import com.qurantime.core.api.stringOrNull
import com.qurantime.core.api.upgradeToHttps
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class VideoType(val id: Int, val name: String)

data class Video(val id: Int, val typeId: Int, val url: String, val thumbUrl: String)

data class VideoGroup(val id: Int, val reciterName: String, val videos: List<Video>)

data class Tafsir(val id: Int, val name: String)

data class TafsirClip(val id: Int, val tafsirId: Int, val name: String, val url: String, val surah: Int)

/**
 * A reflection clip. The API now serves these as video ([videoUrl]); older
 * entries were audio ([audioUrl]). At least one is set.
 */
data class TadaborClip(
    val id: Int,
    val surah: Int,
    val audioUrl: String? = null,
    val videoUrl: String? = null,
    val imageUrl: String,
    val title: String,
    val text: String,
    val surahName: String,
    val riwayaName: String? = null,
    val reciterName: String? = null,
) {
    val isVideo: Boolean get() = videoUrl != null
}

fun parseVideoTypes(json: JsonElement): List<VideoType> =
    json.objectsAt("video_types").map { VideoType(parseInt(it["id"]), cleanName(it["video_type"])) }

fun parseVideos(json: JsonElement): List<VideoGroup> = json.objectsAt("videos").map { g ->
    VideoGroup(
        id = parseInt(g["id"]),
        reciterName = cleanName(g["reciter_name"]),
        videos = g.objectsAt("videos").map {
            Video(
                id = parseInt(it["id"]),
                typeId = parseInt(it["video_type"]),
                url = upgradeToHttps(it["video_url"].stringOrNull().orEmpty()),
                thumbUrl = upgradeToHttps(it["video_thumb_url"].stringOrNull().orEmpty()),
            )
        },
    )
}

fun parseTafasir(json: JsonElement): List<Tafsir> =
    json.objectsAt("tafasir").map { Tafsir(parseInt(it["id"]), cleanName(it["name"])) }

/** `{"tafasir":{"name":…,"soar":{"114":[…]}}}` flattened, sorted by surah then id. */
fun parseTafsirClips(json: JsonElement): List<TafsirClip> {
    val soar = ((json as? JsonObject)?.get("tafasir") as? JsonObject)?.get("soar") as? JsonObject ?: return emptyList()
    return soar.values.flatMap { list ->
        (list as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            TafsirClip(
                id = parseInt(it["id"]),
                tafsirId = parseInt(it["tafsir_id"]),
                name = cleanName(it["name"]),
                url = upgradeToHttps(it["url"].stringOrNull().orEmpty()),
                surah = parseInt(it["sura_id"]),
            )
        }
    }.sortedWith(compareBy({ it.surah }, { it.id }))
}

/** `{"tadabor":{"1":[…]}}` flattened; the surah comes from the key. */
fun parseTadabor(json: JsonElement): List<TadaborClip> {
    val byKey = (json as? JsonObject)?.get("tadabor") as? JsonObject ?: return emptyList()
    return byKey.entries.flatMap { (key, list) ->
        val surah = key.trim().toInt()
        (list as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { j ->
            TadaborClip(
                id = parseInt(j["id"]),
                surah = surah,
                audioUrl = clipUrl(j["audio_url"]),
                videoUrl = clipUrl(j["video_url"]),
                imageUrl = upgradeToHttps(j["image_url"].stringOrNull().orEmpty()),
                title = cleanName(j["title"]),
                text = cleanName(j["text"]),
                surahName = cleanName(j["sora_name"]),
                riwayaName = cleanName(j["rewaya_name"]).ifEmpty { null },
                reciterName = cleanName(j["reciter_name"]).ifEmpty { null },
            )
        }
    }
}

/** Trimmed, https, with spaces and non-ASCII letters percent-encoded (like Dart's Uri.encodeFull). */
private fun clipUrl(v: JsonElement?): String? {
    val s = v.stringOrNull()?.trim().orEmpty()
    return if (s.isEmpty()) null else upgradeToHttps(encodeFull(s))
}

private const val KEEP = "-_.~:/?#[]@!$&'()*+,;=%"

internal fun encodeFull(s: String): String = buildString {
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in KEEP)) append(ch) else append('%').append("%02X".format(c))
    }
}
