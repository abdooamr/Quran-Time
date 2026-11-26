package com.qurantime.feature.audio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qurantime.core.settings.SettingsStore
import com.qurantime.support.FakePlayerBackend
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Ports of the PlayerController groups of the Flutter player_controller_test.dart and ayah_repeat_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val backend = FakePlayerBackend()
    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings by lazy { SettingsStore(PreferenceDataStoreFactory.create(scope = dataScope) { File(tmp.root, "s.preferences_pb") }) }

    @After fun tearDown() = dataScope.cancel()

    private fun TestScope.controller(): PlayerController {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val queue = QueueHandler(backend, { null }, scope)
        return PlayerController(queue, settings, requestNotificationPermission = {}, scope = scope, clock = { testScheduler.currentTime })
    }

    private fun savedJson() = Json.parseToJsonElement(settings.state.value.lastPlaybackJson!!).jsonObject

    @Test
    fun `sleep timer pauses playback`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1)))
        c.sleep.start(30 * 60_000L)
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals("pause", backend.calls.last())
        assertFalse(c.state.value.playing)
    }

    @Test
    fun `end-of-surah sleep pauses when the next surah starts`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        c.sleep.startEndOfTrack()
        backend.emitIndex(1)
        runCurrent()
        assertEquals("pause", backend.calls.last())
    }

    @Test
    fun `restore loads the saved queue paused`() = runTest {
        settings.setLastPlaybackJson("""{"items":[${track(1).toJson()},${track(2).toJson()}],"index":1,"positionMs":65000}""")
        val c = controller()
        c.restore()
        assertEquals(1, backend.currentIndex.value)
        assertEquals(65_000L, backend.positionMs)
        assertFalse("play" in backend.calls)
        assertEquals("Surah 2", c.state.value.item!!.title)
    }

    @Test
    fun `restore ignores missing or broken data`() = runTest {
        val c = controller()
        c.restore()
        settings.setLastPlaybackJson("{not json")
        c.restore()
        assertTrue(backend.calls.isEmpty())
    }

    @Test
    fun `pausing saves where we are`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        backend.emitPosition(12_000)
        c.togglePlay()
        val saved = savedJson()
        assertEquals(0, saved["index"]!!.jsonPrimitive.int)
        assertEquals(12_000L, saved["positionMs"]!!.jsonPrimitive.long)
        assertEquals(2, saved["items"]!!.jsonArray.size)
    }

    @Test
    fun `speed cycles through the allowed steps`() = runTest {
        val c = controller()
        c.setSpeed(1.25f)
        assertEquals(1.25f, c.state.value.speed)
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PlayerController.SPEEDS)
    }

    @Test
    fun `repeat cycles none - all - one - none`() = runTest {
        val c = controller()
        c.cycleRepeat()
        assertEquals(RepeatMode.ALL, backend.lastRepeat)
        c.cycleRepeat()
        assertEquals(RepeatMode.ONE, backend.lastRepeat)
        c.cycleRepeat()
        assertEquals(RepeatMode.OFF, backend.lastRepeat)
    }

    @Test
    fun `an error-driven skip does not overwrite the saved position`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        backend.emitPosition(30_000)
        c.togglePlay()
        val saved = settings.state.value.lastPlaybackJson
        c.togglePlay()
        backend.emitError(Exception("offline"))
        runCurrent()
        assertEquals(saved, settings.state.value.lastPlaybackJson)
    }

    @Test
    fun `end-of-surah sleep does not fire when a new queue starts`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        c.sleep.startEndOfTrack()
        c.playItems(listOf(track(5), track(6), track(7)), startIndex = 2)
        runCurrent()
        assertTrue(c.sleep.state.value.endOfTrack)
        assertNotEquals("pause", backend.calls.last())
    }

    @Test
    fun `Previous within a surah does not swallow the next natural end`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        c.sleep.startEndOfTrack()
        backend.emitPosition(40_000)
        c.previous() // restarts surah 1, no index change
        backend.emitIndex(1) // surah 1 ends naturally
        runCurrent()
        assertEquals("pause", backend.calls.last())
    }

    @Test
    fun `a skip from the notification does not trigger end-of-surah sleep`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1), track(2)))
        c.sleep.startEndOfTrack()
        c.queue.skipToNext()
        runCurrent()
        assertTrue(c.sleep.state.value.endOfTrack)
        assertNotEquals("pause", backend.calls.last())
    }

    @Test
    fun `stop cancels sleep and repeat`() = runTest {
        val c = controller()
        c.playItems(listOf(track(1)))
        c.sleep.start(60_000)
        c.startRangeRepeat(1_000, 2_000, times = 0)
        c.stop()
        assertFalse(c.sleep.state.value.active)
        assertFalse(c.state.value.repeating)
        assertEquals("stop", backend.calls.last())
    }

    // --- range repeat (ayah_repeat_test.dart, PlayerController group) ---

    private val start = 54_884L
    private val end = 76_368L

    private suspend fun TestScope.repeatController(): PlayerController = controller().also {
        it.playItems(listOf(track(2)))
        backend.calls.clear()
    }

    @Test
    fun `starts at the range and pauses after the last repeat`() = runTest {
        val c = repeatController()
        c.startRangeRepeat(start, end, times = 2)
        assertEquals(listOf("seek $start", "play"), backend.calls.filter { it.startsWith("seek") || it == "play" }.take(2))
        assertTrue(c.state.value.repeating)
        backend.emitPosition(end)
        runCurrent()
        assertEquals("seek $start", backend.calls.last())
        backend.emitPosition(start + 10)
        backend.emitPosition(end)
        runCurrent()
        assertEquals("pause", backend.calls.last())
        assertFalse(c.state.value.repeating)
    }

    @Test
    fun `cancelRepeat stops repeating`() = runTest {
        val c = repeatController()
        c.startRangeRepeat(start, end, times = 0)
        c.cancelRepeat()
        backend.emitPosition(end)
        runCurrent()
        assertEquals(1, backend.calls.count { it == "seek $start" })
    }

    @Test
    fun `a range that ends with the file repeats across the auto-advance`() = runTest {
        val c = repeatController()
        c.playItems(listOf(track(2), track(3)))
        backend.calls.clear()
        c.startRangeRepeat(6_600_000, 6_703_906, times = 2)
        backend.emitIndex(1) // the file ended before a position hit endMs
        runCurrent()
        assertTrue(backend.calls.toString(), "seek 6600000 @0" in backend.calls)
        assertTrue(backend.calls.toString(), c.state.value.repeating)
        backend.emitIndex(1) // second play-through ends too
        runCurrent()
        assertFalse(backend.calls.toString(), c.state.value.repeating)
        assertTrue("pause" in backend.calls)
    }
}
