package com.qurantime.kotlin.feature.followalong

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.settings.SettingsStore
import com.qurantime.kotlin.feature.audio.PlayerController
import com.qurantime.kotlin.feature.audio.QueueHandler
import com.qurantime.kotlin.feature.audio.RadioStream
import com.qurantime.kotlin.feature.audio.SurahTrack
import com.qurantime.kotlin.feature.catalog.parseSuwar
import com.qurantime.kotlin.feature.reciters.parseReciters
import com.qurantime.kotlin.feature.timing.TimedReadMatcher
import com.qurantime.kotlin.feature.timing.TimingRepository
import com.qurantime.kotlin.feature.timing.parseTimedReads
import com.qurantime.kotlin.support.FakePlayerBackend
import com.qurantime.kotlin.support.FixtureApi
import com.qurantime.kotlin.support.MemoryApiCacheDao
import com.qurantime.kotlin.support.loadFixtureJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Ports of the Flutter follow_along_controller_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowAlongControllerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val reads = parseTimedReads(loadFixtureJson("ayat_timing_reads.json"))
    private val reciters = parseReciters(loadFixtureJson("reciters_eng.json"))
    private val matcher = TimedReadMatcher(reads, reciters)
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))
    private val ajmi = matcher.moshafForRead(5)!!
    private val backend = FakePlayerBackend()
    private val api = FixtureApi()
    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = dataScope.cancel()

    private fun timedTrack(surah: Int) =
        SurahTrack(ajmi.reciter.id, ajmi.reciter.name, ajmi.moshaf.id, ajmi.moshaf.name, ajmi.moshaf.server, surah, "S$surah")

    private suspend fun settings(file: String = "s", readId: Int? = null) =
        SettingsStore(PreferenceDataStoreFactory.create(scope = dataScope) { File(tmp.root, "$file.preferences_pb") }).also { s ->
            readId?.let { s.setMushafReadId(it) }
            s.load()
        }

    private class Setup(val player: PlayerController, val follow: FollowAlongController, val notifications: () -> Int)

    private suspend fun TestScope.setup(readId: Int? = null): Setup {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val s = settings(readId = readId)
        val player = PlayerController(QueueHandler(backend, { null }, scope), s, requestNotificationPermission = {}, scope = scope)
        val follow = FollowAlongController(player, TimingRepository(api, CachedFetcher(MemoryApiCacheDao(), parseContext = scope.coroutineContext)), matcher = { matcher }, suwar = { suwar }, settings = s, scope = scope)
        var count = 0
        scope.launch { follow.state.collect { count++ } }
        return Setup(player, follow) { count }
    }

    @Test
    fun `inactive for a recording without timing`() = runTest {
        val (player, follow) = setup().let { it.player to it.follow }
        val untimed = reciters.flatMap { r -> r.moshaf.map { r to it } }.first { (_, m) -> !matcher.isTimed(m) && m.has(2) }
        player.playItems(listOf(SurahTrack(untimed.first.id, untimed.first.name, untimed.second.id, untimed.second.name, untimed.second.server, 2, "S2")))
        runCurrent()
        assertFalse(follow.state.value.active)
    }

    @Test
    fun `inactive for radio`() = runTest {
        val (player, follow) = setup().let { it.player to it.follow }
        player.playItems(listOf(RadioStream(1, "R", "https://r")))
        runCurrent()
        assertFalse(follow.state.value.active)
    }

    @Test
    fun `active for read 5, surah 2 - tracks the ayah and its page`() = runTest {
        val (player, follow) = setup().let { it.player to it.follow }
        player.playItems(listOf(timedTrack(2)))
        runCurrent()
        assertTrue(follow.state.value.active)
        assertEquals(5, follow.state.value.readId)
        assertEquals(2, follow.state.value.surah)
        backend.emitPosition(60_000)
        runCurrent()
        assertEquals(6, follow.state.value.current!!.ayah)
        assertEquals(3, follow.state.value.currentPage)
    }

    @Test
    fun `notifies once per ayah, not per position`() = runTest {
        val s = setup()
        s.player.playItems(listOf(timedTrack(2)))
        runCurrent()
        val before = s.notifications()
        for (ms in listOf(60_000L, 60_500L, 61_000L)) {
            backend.emitPosition(ms)
            runCurrent()
        }
        assertEquals(1, s.notifications() - before)
    }

    @Test
    fun `the intro (ayah 0) has no page`() = runTest {
        val (player, follow) = setup().let { it.player to it.follow }
        player.playItems(listOf(timedTrack(2)))
        runCurrent()
        backend.emitPosition(0)
        runCurrent()
        assertEquals(0, follow.state.value.current!!.ayah)
        assertNull(follow.state.value.currentPage)
    }

    @Test
    fun `playFromAyah plays the Mushaf reciter from that ayah`() = runTest {
        val s = setup()
        assertTrue(s.follow.playFromAyah(surah = 2, ayah = 7))
        val item = s.player.state.value.item as SurahTrack
        assertEquals(ajmi.moshaf.server, item.server)
        assertEquals(2, item.surah)
        assertEquals("Al-Baqarah", item.surahName)
        assertEquals(65_566L, backend.positionMs)
        assertTrue("play" in backend.calls)
    }

    @Test
    fun `playFromAyah refuses a surah the recording lacks`() = runTest {
        val partial = reads.firstOrNull { it.surahCount < 114 } ?: return@runTest
        val m = matcher.moshafForRead(partial.id)!!.moshaf
        val missing = (1..114).firstOrNull { !m.has(it) } ?: return@runTest // fixture has no incomplete read
        val s = setup(readId = partial.id)
        assertFalse(s.follow.playFromAyah(surah = missing, ayah = 1))
        assertFalse("play" in backend.calls)
    }
}
