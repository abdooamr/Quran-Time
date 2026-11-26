package com.qurantime.core.cache

import kotlin.coroutines.CoroutineContext
import com.qurantime.core.db.ApiCacheDao
import com.qurantime.core.db.ApiCacheEntity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** How long each kind of API data stays fresh. */
object Ttl {
    val CATALOG: Duration = 30.days
    val WEEKLY: Duration = 7.days
    val DAILY: Duration = 1.days

    /** Never goes stale. */
    val FOREVER: Duration? = null
}

/**
 * Reads API data from the on-device cache first.
 *
 * - fresh: returns the cached copy without a network call
 * - stale or `forceRefresh`: fetches; if that fails, returns the stale copy
 * - missing: fetches; if that fails, rethrows
 */
class CachedFetcher(
    private val dao: ApiCacheDao,
    private val now: () -> Long = System::currentTimeMillis,
    /** Where JSON is decoded and parsed: off the main thread (tests on virtual time pass their own). */
    private val parseContext: CoroutineContext = Dispatchers.Default,
) {

    suspend fun <T> fetch(
        key: String,
        ttl: Duration?,
        remote: suspend () -> JsonElement,
        parse: (JsonElement) -> T,
        forceRefresh: Boolean = false,
    ): T {
        val cached = dao.get(key)
        if (cached != null && !forceRefresh && !isStale(cached, ttl)) return parsed(cached.json, parse)
        return try {
            val json = remote()
            val value = withContext(parseContext) { parse(json) }
            dao.put(ApiCacheEntity(key, json.toString(), now()))
            value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) parsed(cached.json, parse) else throw e
        }
    }

    /** Stores [json] under [key] as fetched now (used after merging updates). */
    suspend fun store(key: String, json: JsonElement) = dao.put(ApiCacheEntity(key, json.toString(), now()))

    /** The cached JSON for [key], parsed, without any network call. */
    suspend fun <T> peek(key: String, parse: (JsonElement) -> T): T? = dao.get(key)?.let { parsed(it.json, parse) }

    /** Whether [key] is cached and older than [ttl]. */
    suspend fun isStale(key: String, ttl: Duration?): Boolean = dao.get(key)?.let { isStale(it, ttl) } ?: false

    suspend fun fetchedAt(key: String): Long? = dao.get(key)?.fetchedAtMs

    private fun isStale(row: ApiCacheEntity, ttl: Duration?) = ttl != null && now() - row.fetchedAtMs > ttl.inWholeMilliseconds

    /** Decoding and parsing big JSON (reciters, timings) runs off the caller's thread. */
    private suspend fun <T> parsed(s: String, parse: (JsonElement) -> T): T =
        withContext(parseContext) { parse(Json.parseToJsonElement(s)) }
}
