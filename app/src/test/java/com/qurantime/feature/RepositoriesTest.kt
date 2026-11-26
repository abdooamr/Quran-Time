package com.qurantime.feature

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.api.ApiException
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.api.normalizeServer
import com.qurantime.core.cache.CachedFetcher
import com.qurantime.core.db.AppDatabase
import com.qurantime.feature.catalog.CatalogRepository
import com.qurantime.feature.explore.ExploreRepository
import com.qurantime.feature.live.LiveRepository
import com.qurantime.feature.reciters.RecitersRepository
import com.qurantime.feature.reciters.parseReciters
import com.qurantime.feature.timing.AyahTiming
import com.qurantime.feature.timing.TimedReadMatcher
import com.qurantime.feature.timing.TimingRepository
import com.qurantime.feature.timing.parseAyahTimings
import com.qurantime.feature.timing.parseTimedReads
import com.qurantime.feature.timing.timingIndexAt
import com.qurantime.support.DAY_MS
import com.qurantime.support.FixtureApi
import com.qurantime.support.HOUR_MS
import com.qurantime.support.JAN_1_2026
import com.qurantime.support.fixtureHandler
import com.qurantime.support.loadFixtureJson
import com.qurantime.support.memoryDb
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Shared setup: in-memory DB, fixture API, a clock tests can move. */
abstract class RepoTestBase(start: Long = JAN_1_2026) {
    protected lateinit var db: AppDatabase
    protected val api = FixtureApi()
    protected var now = start
    protected lateinit var fetcher: CachedFetcher

    @Before fun setUpDb() {
        db = memoryDb()
        fetcher = CachedFetcher(db.apiCacheDao(), now = { now })
    }

    @After fun closeDb() = db.close()
}

/** Ports of the Flutter repositories_test.dart (Live, Explore, Timing groups). */
@RunWith(AndroidJUnit4::class)
class RepositoriesTest : RepoTestBase() {
    @Test
    fun `radios request and 7-day cache`() = runTest {
        val repo = LiveRepository(api, fetcher)
        assertTrue(repo.radios(ApiLanguage.ENG).isNotEmpty())
        assertEquals(mapOf("language" to "eng"), api.callsTo("radios").single().query)
        now += 6 * DAY_MS
        repo.radios(ApiLanguage.ENG)
        assertEquals(1, api.callsTo("radios").size)
        now += 2 * DAY_MS
        repo.radios(ApiLanguage.ENG)
        assertEquals(2, api.callsTo("radios").size)
    }

    @Test
    fun `live tv request and 1-day cache`() = runTest {
        val repo = LiveRepository(api, fetcher)
        assertEquals(2, repo.liveTv(ApiLanguage.AR).size)
        assertEquals(mapOf("language" to "ar"), api.callsTo("live-tv").single().query)
        now += 25 * HOUR_MS
        repo.liveTv(ApiLanguage.AR)
        assertEquals(2, api.callsTo("live-tv").size)
    }

    @Test
    fun `video types and videos`() = runTest {
        val repo = ExploreRepository(api, fetcher)
        assertTrue(repo.videoTypes(ApiLanguage.AR).isNotEmpty())
        assertTrue(repo.videos(ApiLanguage.AR).isNotEmpty())
        assertEquals(mapOf("language" to "ar"), api.callsTo("video_types").single().query)
        assertEquals(mapOf("language" to "ar"), api.callsTo("videos").single().query)
    }

    @Test
    fun `tafasir list`() = runTest {
        val repo = ExploreRepository(api, fetcher)
        assertEquals(1, repo.tafasir(ApiLanguage.AR).single().id)
        assertEquals(mapOf("language" to "ar"), api.callsTo("tafasir").single().query)
    }

    @Test
    fun `tafsir clips for one surah`() = runTest {
        val repo = ExploreRepository(api, fetcher)
        assertEquals(114, repo.tafsir(1, 114, ApiLanguage.AR).single().surah)
        assertEquals(mapOf("tafsir" to "1", "sura" to "114", "language" to "ar"), api.callsTo("tafsir").single().query)
        repo.tafsir(1, 114, ApiLanguage.AR)
        assertEquals(1, api.callsTo("tafsir").size)
    }

    @Test
    fun `tadabor for one surah`() = runTest {
        val repo = ExploreRepository(api, fetcher)
        assertTrue(repo.tadabor(1, ApiLanguage.AR).isNotEmpty())
        assertEquals(mapOf("sura" to "1", "language" to "ar"), api.callsTo("tadabor").single().query)
    }

    @Test
    fun `reads and timed surahs`() = runTest {
        val repo = TimingRepository(api, fetcher)
        assertTrue(repo.reads().any { it.id == 5 })
        assertTrue(114 in repo.timedSurahs(5))
        assertEquals(mapOf("read" to "5"), api.callsTo("ayat_timing/soar").single().query)
    }

    @Test
    fun `timing request and never refetched`() = runTest {
        val repo = TimingRepository(api, fetcher)
        assertEquals(287, repo.timing(5, 2).size)
        assertEquals(mapOf("surah" to "2", "read" to "5"), api.callsTo("ayat_timing").single().query)
        now += 3650 * DAY_MS
        repo.timing(5, 2)
        repo.reads()
        repo.reads()
        assertEquals(1, api.callsTo("ayat_timing").size)
        assertEquals(1, api.callsTo("ayat_timing/reads").size)
    }
}

/** Ports of the Flutter catalog/catalog_repository_test.dart. */
@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest : RepoTestBase() {
    private val repo by lazy { CatalogRepository(api, fetcher) }

    @Test
    fun `language is part of the key`() = runTest {
        val ar = repo.suwar(ApiLanguage.AR)
        val en = repo.suwar(ApiLanguage.ENG)
        assertEquals(listOf("ar", "eng"), api.callsTo("suwar").map { it.query["language"] })
        assertNotEquals(en.first().name, ar.first().name)
        assertEquals("Al-Fatihah", en.first().name)
    }

    @Test
    fun `suwar cached 30 days`() = runTest {
        repo.suwar(ApiLanguage.ENG)
        now += 29 * DAY_MS
        repo.suwar(ApiLanguage.ENG)
        assertEquals(1, api.callsTo("suwar").size)
        now += 2 * DAY_MS
        repo.suwar(ApiLanguage.ENG)
        assertEquals(2, api.callsTo("suwar").size)
    }

    @Test
    fun `riwayat and moshaf types`() = runTest {
        assertTrue(repo.riwayat(ApiLanguage.ENG).any { it.id == 1 })
        assertTrue(repo.moshafTypes(ApiLanguage.ENG).any { it.id == 11 && it.riwayaId == 1 })
        assertEquals("eng", api.callsTo("riwayat").single().query["language"])
        assertEquals("eng", api.callsTo("moshaf").single().query["language"])
    }
}

/** Ports of the Flutter reciters/reciters_repository_test.dart. */
@RunWith(AndroidJUnit4::class)
class RecitersRepositoryTest : RepoTestBase(start = JAN_1_2026 + 9 * DAY_MS) { // 2026-01-10
    private val repo by lazy { RecitersRepository(api, fetcher) }
    private fun fixtureCount() = loadFixtureJson("reciters_eng.json").jsonObject["reciters"]!!.jsonArray.size

    @Test
    fun `all is cached for 7 days`() = runTest {
        assertEquals(fixtureCount(), repo.all(ApiLanguage.ENG).size)
        now += 6 * DAY_MS
        repo.all(ApiLanguage.ENG)
        assertEquals(1, api.callsTo("reciters").size)
    }

    @Test
    fun `delta merge after 8 days`() = runTest {
        repo.all(ApiLanguage.ENG)
        now += 8 * DAY_MS
        api.handler = { path, q ->
            if (path == "reciters" && "last_updated_date" in q) {
                Json.parseToJsonElement(
                    """{"reciters":[{"id":231,"name":"Hazza Renamed","letter":"H","moshaf":[]},
                    {"id":99999,"name":"New Reciter","letter":"N","moshaf":[]}]}""",
                )
            } else {
                fixtureHandler(path, q)
            }
        }
        val merged = repo.all(ApiLanguage.ENG)
        val delta = api.callsTo("reciters").last()
        assertEquals("2026-01-09", delta.query["last_updated_date"])
        assertEquals("eng", delta.query["language"])
        assertEquals(fixtureCount() + 1, merged.size)
        assertEquals("Hazza Renamed", merged.first { it.id == 231 }.name)
        assertTrue(merged.any { it.id == 99999 })
        // Stored: reading again uses the merged copy without a request.
        val calls = api.calls.size
        val again = repo.all(ApiLanguage.ENG)
        assertEquals(calls, api.calls.size)
        assertEquals("Hazza Renamed", again.first { it.id == 231 }.name)
    }

    @Test
    fun `delta failure falls back to full fetch`() = runTest {
        repo.all(ApiLanguage.ENG)
        now += 8 * DAY_MS
        api.handler = { path, q ->
            if ("last_updated_date" in q) throw ApiException(500, "boom") else fixtureHandler(path, q)
        }
        assertEquals(fixtureCount(), repo.all(ApiLanguage.ENG).size)
        assertFalse("last_updated_date" in api.callsTo("reciters").last().query)
    }

    @Test
    fun `byId comes from the all() cache`() = runTest {
        repo.all(ApiLanguage.ENG)
        assertEquals("Hazza Al-Balushi", repo.byId(231, ApiLanguage.ENG)!!.name)
        assertEquals(1, api.callsTo("reciters").size)
        assertNull(repo.byId(-1, ApiLanguage.ENG))
    }

    @Test
    fun `recent is cached for a day`() = runTest {
        assertTrue(repo.recent(ApiLanguage.ENG).isNotEmpty())
        repo.recent(ApiLanguage.ENG)
        assertEquals(1, api.callsTo("recent_reads").size)
        now += 2 * DAY_MS
        repo.recent(ApiLanguage.ENG)
        assertEquals(2, api.callsTo("recent_reads").size)
    }
}

/** Ports of the Flutter timing/lookup_and_matcher_test.dart. */
class LookupAndMatcherTest {
    private val t = parseAyahTimings(loadFixtureJson("ayat_timing_2_5.json"))
    private fun ayahAt(ms: Long) = timingIndexAt(t, ms)?.let { t[it].ayah }

    private val reads = parseTimedReads(loadFixtureJson("ayat_timing_reads.json"))
    private val m = TimedReadMatcher(reads, parseReciters(loadFixtureJson("reciters_eng.json")))
    private val r5 = reads.first { it.id == 5 }

    @Test
    fun `timingIndexAt finds the ayah being recited`() {
        assertEquals(0, ayahAt(0))
        assertEquals(0, ayahAt(5586))
        assertEquals(1, ayahAt(5587))
        assertEquals(1, ayahAt(12657))
        assertEquals(2, ayahAt(12658))
        assertEquals(t.last().ayah, ayahAt(99_999_999))
    }

    @Test
    fun `empty list and before the first entry`() {
        assertNull(timingIndexAt(emptyList(), 10))
        assertNull(timingIndexAt(listOf(AyahTiming(ayah = 1, startMs = 100, endMs = 200)), 50))
    }

    @Test
    fun `a gap keeps the earlier ayah`() {
        val gap = listOf(AyahTiming(ayah = 1, startMs = 0, endMs = 100), AyahTiming(ayah = 2, startMs = 200, endMs = 300))
        assertEquals(0, timingIndexAt(gap, 150))
    }

    @Test
    fun `finds a read by server, ignoring scheme, case and trailing slash`() {
        val noSlash = r5.folderUrl.replaceFirst("https://", "http://").uppercase()
        assertEquals(5, m.readForServer(noSlash.dropLast(1))!!.id)
        assertNull(m.readForServer("https://example.com/nothing/"))
    }

    @Test
    fun `moshafForRead returns the matching recording`() {
        val hit = m.moshafForRead(5)!!
        assertEquals(normalizeServer(r5.folderUrl), normalizeServer(hit.moshaf.server))
        assertTrue(hit.moshaf in hit.reciter.moshaf)
        assertTrue(m.isTimed(hit.moshaf))
    }

    @Test
    fun `every timed read in the fixture matches a recording`() {
        for (r in reads) assertNotNull("read ${r.id}", m.moshafForRead(r.id))
    }

    @Test
    fun `an unknown read has no recording`() {
        assertNull(m.moshafForRead(-1))
    }
}
