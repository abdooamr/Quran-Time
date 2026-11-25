package com.qurantime.kotlin.feature.catalog

import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.cache.Ttl
import kotlinx.serialization.json.JsonElement

class CatalogRepository(private val api: Api, private val fetcher: CachedFetcher) {
    suspend fun suwar(lang: ApiLanguage): List<Surah> = get("suwar", lang, ::parseSuwar)
    suspend fun riwayat(lang: ApiLanguage): List<Riwaya> = get("riwayat", lang, ::parseRiwayat)
    suspend fun moshafTypes(lang: ApiLanguage): List<MoshafType> = get("moshaf", lang, ::parseMoshafTypes)

    private suspend fun <T> get(path: String, lang: ApiLanguage, parse: (JsonElement) -> T): T = fetcher.fetch(
        key = "$path:${lang.code}",
        ttl = Ttl.CATALOG,
        remote = { api.getJson(path, mapOf("language" to lang.code)) },
        parse = parse,
    )
}
