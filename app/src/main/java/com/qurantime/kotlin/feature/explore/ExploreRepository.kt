package com.qurantime.kotlin.feature.explore

import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.cache.Ttl
import kotlin.time.Duration
import kotlinx.serialization.json.JsonElement

class ExploreRepository(private val api: Api, private val fetcher: CachedFetcher) {
    suspend fun videoTypes(lang: ApiLanguage) = get("video_types", mapOf("language" to lang.code), Ttl.DAILY, ::parseVideoTypes)

    suspend fun videos(lang: ApiLanguage) = get("videos", mapOf("language" to lang.code), Ttl.DAILY, ::parseVideos)

    suspend fun tafasir(lang: ApiLanguage) = get("tafasir", mapOf("language" to lang.code), Ttl.CATALOG, ::parseTafasir)

    suspend fun tafsir(tafsirId: Int, surah: Int, lang: ApiLanguage) =
        get("tafsir", mapOf("tafsir" to tafsirId, "sura" to surah, "language" to lang.code), Ttl.CATALOG, ::parseTafsirClips)

    suspend fun tadabor(surah: Int, lang: ApiLanguage) =
        get("tadabor", mapOf("sura" to surah, "language" to lang.code), Ttl.CATALOG, ::parseTadabor)

    private suspend fun <T> get(path: String, query: Map<String, Any>, ttl: Duration?, parse: (JsonElement) -> T): T = fetcher.fetch(
        key = "$path:" + query.entries.joinToString("&") { "${it.key}=${it.value}" },
        ttl = ttl,
        remote = { api.getJson(path, query) },
        parse = parse,
    )
}
