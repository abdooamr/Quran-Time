package com.qurantime.kotlin.feature.audio

import com.qurantime.kotlin.core.api.surahAudioUrl
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Anything the player can play: a surah recording, a radio stream or a clip. */
sealed interface PlayableItem {
    val url: String
    fun toJson(): JsonObject

    companion object {
        /** Throws [IllegalArgumentException] for an unknown kind. */
        fun fromJson(j: JsonObject): PlayableItem {
            fun s(k: String) = j[k]!!.jsonPrimitive.content
            fun i(k: String) = j[k]!!.jsonPrimitive.int
            return when (j["kind"]?.jsonPrimitive?.contentOrNull) {
                "surah" -> SurahTrack(i("reciterId"), s("reciterName"), i("moshafId"), s("moshafName"), s("server"), i("surah"), s("surahName"))
                "radio" -> RadioStream(i("id"), s("name"), s("url"))
                "clip" -> AudioClip(s("id"), s("title"), s("subtitle"), s("url"), (j["artUrl"] as? JsonPrimitive)?.contentOrNull)
                else -> throw IllegalArgumentException("Unknown playable kind: ${j["kind"]}")
            }
        }
    }
}

data class SurahTrack(
    val reciterId: Int,
    val reciterName: String,
    val moshafId: Int,
    val moshafName: String,
    val server: String,
    val surah: Int,
    val surahName: String,
) : PlayableItem {
    override val url: String get() = surahAudioUrl(server, surah)

    override fun toJson() = buildJsonObject {
        put("kind", "surah")
        put("reciterId", reciterId)
        put("reciterName", reciterName)
        put("moshafId", moshafId)
        put("moshafName", moshafName)
        put("server", server)
        put("surah", surah)
        put("surahName", surahName)
    }

    // Same recording and surah: names may change language, the track doesn't.
    override fun equals(other: Any?) = other is SurahTrack && other.moshafId == moshafId && other.surah == surah && other.server == server
    override fun hashCode() = (moshafId * 31 + surah) * 31 + server.hashCode()
}

data class RadioStream(val id: Int, val name: String, override val url: String) : PlayableItem {
    override fun toJson() = buildJsonObject {
        put("kind", "radio")
        put("id", id)
        put("name", name)
        put("url", url)
    }

    override fun equals(other: Any?) = other is RadioStream && other.id == id && other.url == url
    override fun hashCode() = id * 31 + url.hashCode()
}

data class AudioClip(
    val id: String,
    val title: String,
    val subtitle: String,
    override val url: String,
    val artUrl: String? = null,
) : PlayableItem {
    override fun toJson() = buildJsonObject {
        put("kind", "clip")
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("url", url)
        put("artUrl", artUrl?.let(::JsonPrimitive) ?: JsonNull)
    }

    override fun equals(other: Any?) = other is AudioClip && other.id == id && other.url == url
    override fun hashCode() = id.hashCode() * 31 + url.hashCode()
}
