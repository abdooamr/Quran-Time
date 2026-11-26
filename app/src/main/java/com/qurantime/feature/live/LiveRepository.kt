package com.qurantime.feature.live

import com.qurantime.core.api.Api
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.cache.CachedFetcher
import com.qurantime.core.cache.Ttl

class LiveRepository(private val api: Api, private val fetcher: CachedFetcher) {
    suspend fun radios(lang: ApiLanguage): List<RadioStation> = fetcher.fetch(
        key = "radios:${lang.code}",
        ttl = Ttl.WEEKLY,
        remote = { api.getJson("radios", mapOf("language" to lang.code)) },
        parse = ::parseRadios,
    )

    suspend fun liveTv(lang: ApiLanguage): List<LiveChannel> = fetcher.fetch(
        key = "live-tv:${lang.code}",
        ttl = Ttl.DAILY,
        remote = { api.getJson("live-tv", mapOf("language" to lang.code)) },
        parse = ::parseLiveTv,
    )
}
