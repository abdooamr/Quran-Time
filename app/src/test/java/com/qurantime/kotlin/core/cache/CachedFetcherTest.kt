package com.qurantime.kotlin.core.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.core.api.ApiException
import com.qurantime.kotlin.core.db.AppDatabase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedFetcherTest {
    private lateinit var db: AppDatabase
    private lateinit var fetcher: CachedFetcher
    private var now = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private var calls = 0
    private var remoteValue: JsonElement = v(1)
    private var remoteError: Exception? = null

    private fun v(n: Int) = buildJsonObject { put("v", n) }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        fetcher = CachedFetcher(db.apiCacheDao(), now = { now })
    }

    @After fun tearDown() = db.close()

    private suspend fun fetch(ttl: Duration? = 1.days, force: Boolean = false): Int = fetcher.fetch(
        key = "k",
        ttl = ttl,
        remote = {
            calls++
            remoteError?.let { throw it }
            remoteValue
        },
        parse = { it.jsonObject["v"]!!.jsonPrimitive.int },
        forceRefresh = force,
    )

    @Test
    fun `missing calls remote and stores`() = runTest {
        assertEquals(1, fetch())
        assertEquals(1, calls)
        assertEquals(1_767_225_600_000L, fetcher.fetchedAt("k"))
    }

    @Test
    fun `fresh hit skips remote`() = runTest {
        fetch()
        now += 23.hours.inWholeMilliseconds
        remoteValue = v(2)
        assertEquals(1, fetch())
        assertEquals(1, calls)
    }

    @Test
    fun `stale refetches and stores`() = runTest {
        fetch()
        now += 2.days.inWholeMilliseconds
        remoteValue = v(2)
        assertEquals(2, fetch())
        assertEquals(2, calls)
        assertEquals(now, fetcher.fetchedAt("k"))
    }

    @Test
    fun `stale + remote error returns stale`() = runTest {
        fetch()
        now += 2.days.inWholeMilliseconds
        remoteError = ApiException(null, "offline")
        assertEquals(1, fetch())
    }

    @Test
    fun `missing + remote error throws`() = runTest {
        remoteError = ApiException(null, "offline")
        try {
            fetch()
            fail("expected ApiException")
        } catch (_: ApiException) {
        }
    }

    @Test
    fun `forever never refetches after 10 years`() = runTest {
        fetch(ttl = Ttl.FOREVER)
        now += 3650.days.inWholeMilliseconds
        remoteValue = v(2)
        assertEquals(1, fetch(ttl = Ttl.FOREVER))
        assertEquals(1, calls)
    }

    @Test
    fun `forceRefresh calls remote even when fresh`() = runTest {
        fetch()
        remoteValue = v(3)
        assertEquals(3, fetch(force = true))
        assertEquals(2, calls)
    }

    @Test
    fun `ttl constants`() {
        assertEquals(30.days, Ttl.CATALOG)
        assertEquals(7.days, Ttl.WEEKLY)
        assertEquals(1.days, Ttl.DAILY)
    }
}
