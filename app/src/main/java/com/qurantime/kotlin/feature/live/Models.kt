package com.qurantime.kotlin.feature.live

import com.qurantime.kotlin.core.api.cleanName
import com.qurantime.kotlin.core.api.objectsAt
import com.qurantime.kotlin.core.api.parseInt
import com.qurantime.kotlin.core.api.stringOrNull
import com.qurantime.kotlin.core.api.upgradeToHttps
import kotlinx.serialization.json.JsonElement

class RadioStation(val id: Int, val name: String, val url: String) {
    override fun equals(other: Any?) = other is RadioStation && other.id == id
    override fun hashCode() = id
}

data class LiveChannel(val id: Int, val name: String, val url: String)

fun parseRadios(json: JsonElement): List<RadioStation> = json.objectsAt("radios").map {
    RadioStation(parseInt(it["id"]), cleanName(it["name"]), upgradeToHttps(it["url"].stringOrNull().orEmpty()))
}

fun parseLiveTv(json: JsonElement): List<LiveChannel> = json.objectsAt("livetv").map {
    LiveChannel(parseInt(it["id"]), cleanName(it["name"]), upgradeToHttps(it["url"].stringOrNull().orEmpty()))
}
