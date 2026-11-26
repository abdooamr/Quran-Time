package com.qurantime.feature.catalog

import com.qurantime.core.api.cleanName
import com.qurantime.core.api.objectsAt
import com.qurantime.core.api.parseInt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class Surah(val id: Int, val name: String, val startPage: Int, val endPage: Int, val isMakki: Boolean) {
    override fun equals(other: Any?) = other is Surah && other.id == id
    override fun hashCode() = id
    override fun toString() = "Surah($id $name)"

    companion object {
        fun fromJson(j: JsonObject) = Surah(
            id = parseInt(j["id"]),
            name = cleanName(j["name"]),
            startPage = parseInt(j["start_page"]),
            endPage = parseInt(j["end_page"]),
            isMakki = parseInt(j["makkia"]) == 1,
        )
    }
}

class Riwaya(val id: Int, val name: String) {
    override fun equals(other: Any?) = other is Riwaya && other.id == id
    override fun hashCode() = id
}

/**
 * A riwaya + style combination from `/moshaf`. [id] equals a reciter moshaf's
 * `moshaf_type`; [riwayaId] is a `/riwayat` id.
 */
class MoshafType(val id: Int, val riwayaId: Int, val styleId: Int, val name: String) {
    override fun equals(other: Any?) = other is MoshafType && other.id == id
    override fun hashCode() = id
}

fun parseSuwar(json: JsonElement): List<Surah> = json.objectsAt("suwar").map(Surah::fromJson)

fun parseRiwayat(json: JsonElement): List<Riwaya> =
    json.objectsAt("riwayat").map { Riwaya(parseInt(it["id"]), cleanName(it["name"])) }

fun parseMoshafTypes(json: JsonElement): List<MoshafType> = json.objectsAt("riwayat").map {
    MoshafType(
        id = parseInt(it["id"]),
        riwayaId = parseInt(it["moshaf_type"]),
        styleId = parseInt(it["moshaf_id"]),
        name = cleanName(it["name"]),
    )
}
