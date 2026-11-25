package com.qurantime.kotlin.feature.reciters

import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.api.objectsAt
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.cache.Ttl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class RecitersRepository(private val api: Api, private val fetcher: CachedFetcher) {

    /**
     * All reciters. After a week, asks only for reciters changed since the
     * last fetch and merges them in; falls back to a full fetch if that fails.
     */
    suspend fun all(lang: ApiLanguage, forceRefresh: Boolean = false): List<Reciter> {
        val key = "reciters:${lang.code}"
        if (!forceRefresh && fetcher.isStale(key, Ttl.WEEKLY)) {
            mergeChanges(key, lang)?.let { return it }
            return full(key, lang, force = true)
        }
        return full(key, lang, force = forceRefresh)
    }

    suspend fun byId(id: Int, lang: ApiLanguage): Reciter? = all(lang).firstOrNull { it.id == id }

    suspend fun recent(lang: ApiLanguage): List<Reciter> = fetcher.fetch(
        key = "recent_reads:${lang.code}",
        ttl = Ttl.DAILY,
        remote = { api.getJson("recent_reads", mapOf("language" to lang.code)) },
        parse = ::parseRecentReads,
    )

    private suspend fun full(key: String, lang: ApiLanguage, force: Boolean): List<Reciter> = fetcher.fetch(
        key = key,
        ttl = Ttl.WEEKLY,
        forceRefresh = force,
        remote = { api.getJson("reciters", mapOf("language" to lang.code)) },
        parse = ::parseReciters,
    )

    private suspend fun mergeChanges(key: String, lang: ApiLanguage): List<Reciter>? = try {
        val at = fetcher.fetchedAt(key)
        val cached = fetcher.peek(key) { it.objectsAt("reciters") }
        if (at == null || cached == null) {
            null
        } else {
            // A day of overlap so same-day changes are never missed; merging by id makes it harmless.
            val since = ymd(at - DAY_MS)
            val changed = api.getJson("reciters", mapOf("language" to lang.code, "last_updated_date" to since)).objectsAt("reciters")
            val byId = LinkedHashMap<String, JsonObject>()
            for (r in cached) byId[r["id"].toString()] = r
            for (r in changed) byId[r["id"].toString()] = r
            val json = buildJsonObject { put("reciters", JsonArray(byId.values.toList())) }
            val parsed = parseReciters(json)
            fetcher.store(key, json)
            parsed
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun ymd(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(ms)

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
