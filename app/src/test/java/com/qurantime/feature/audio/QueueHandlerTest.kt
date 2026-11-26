package com.qurantime.feature.audio

import com.qurantime.support.FakePlayerBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun track(surah: Int) = SurahTrack(
    reciterId = 5,
    reciterName = "Ahmad Al-Ajmy",
    moshafId = 5,
    moshafName = "Murattal",
    server = "https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/",
    surah = surah,
    surahName = "Surah $surah",
)

/** Ports of the Flutter quran_audio_handler_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueHandlerTest {
    private val backend = FakePlayerBackend()

    private fun kotlinx.coroutines.test.TestScope.handler(localPathFor: suspend (String) -> String? = { null }) =
        QueueHandler(backend, localPathFor, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    private operator fun kotlinx.coroutines.CoroutineScope.plus(d: kotlinx.coroutines.CoroutineDispatcher) =
        kotlinx.coroutines.CoroutineScope(coroutineContext + d)

    @Test
    fun `playItems sets queue and mediaItem`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1), track(2), track(3)), startIndex = 1, startPositionMs = 7_000)
        runCurrent()
        assertEquals(3, h.items.size)
        assertEquals("Surah 2", h.current.value!!.item.title)
        assertEquals(1, backend.currentIndex.value)
        assertEquals(7_000L, backend.positionMs)
        assertEquals(listOf("setSources", "play"), backend.calls)
        assertEquals("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/002.mp3", backend.uris[1])
    }

    @Test
    fun `prepareItems loads without playing`() = runTest {
        val h = handler()
        h.prepareItems(listOf(track(1)))
        assertEquals(listOf("setSources"), backend.calls)
        assertEquals("Surah 1", h.current.value!!.item.title)
    }

    @Test
    fun `uses a downloaded file when there is one`() = runTest {
        val h = handler(localPathFor = { url -> if (url.endsWith("002.mp3")) "/d/002.mp3" else null })
        h.playItems(listOf(track(1), track(2)))
        assertEquals(listOf("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/001.mp3", "file:///d/002.mp3"), backend.uris)
    }

    @Test
    fun `index changes update mediaItem`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1), track(2)))
        h.skipToNext()
        runCurrent()
        assertTrue("seek 0 @1" in backend.calls)
        assertEquals("Surah 2", h.current.value!!.item.title)
        assertEquals(1, h.state.value.queueIndex)
    }

    @Test
    fun `skipToNext on the last item does nothing`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1)))
        h.skipToNext()
        assertTrue(backend.calls.none { it.startsWith("seek") })
    }

    @Test
    fun `speed is clamped to 0_5 to 2_0`() = runTest {
        val h = handler()
        h.setSpeed(3.0f)
        h.setSpeed(0.1f)
        assertEquals(listOf("speed 2.0", "speed 0.5"), backend.calls)
    }

    @Test
    fun `repeat modes map to loop modes`() = runTest {
        val h = handler()
        h.setRepeatMode(RepeatMode.ONE)
        assertEquals(RepeatMode.ONE, backend.lastRepeat)
        h.setRepeatMode(RepeatMode.ALL)
        assertEquals(RepeatMode.ALL, backend.lastRepeat)
        h.setRepeatMode(RepeatMode.OFF)
        assertEquals(RepeatMode.OFF, backend.lastRepeat)
        assertEquals(RepeatMode.OFF, h.state.value.repeat)
    }

    @Test
    fun `an error skips to the next item and is reported`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1), track(2)))
        backend.emitError(Exception("404"))
        runCurrent()
        assertTrue("seek 0 @1" in backend.calls)
        assertNotNull(h.state.value.errorMessage)
    }

    @Test
    fun `an error on the last item stops`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1)))
        backend.emitError(Exception("404"))
        runCurrent()
        assertEquals("stop", backend.calls.last())
    }

    @Test
    fun `radio has no seek controls`() = runTest {
        val h = handler()
        h.playItems(listOf(RadioStream(id = 1, name = "R", url = "https://r")))
        runCurrent()
        val s = h.state.value
        assertFalse(s.canSeek)
        assertFalse(s.canSkip)
        assertTrue(s.isLive)
        assertTrue(s.playing)
    }

    @Test
    fun `surahs expose seek`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1)))
        runCurrent()
        assertTrue(h.state.value.canSeek)
    }

    @Test
    fun `a load error is reported, not thrown, and nothing plays`() = runTest {
        val h = handler()
        backend.failSetSources = true
        h.playItems(listOf(track(1), track(2)))
        assertFalse("play" in backend.calls)
        assertNotNull(h.state.value.errorMessage)
    }

    @Test
    fun `errors stop after two items in a row instead of walking the queue`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1), track(2), track(3), track(4)))
        backend.emitError(Exception("offline"))
        runCurrent()
        backend.emitError(Exception("offline"))
        runCurrent()
        assertEquals(listOf("seek 0 @1"), backend.calls.filter { it.startsWith("seek") })
        assertEquals("stop", backend.calls.last())
    }

    @Test
    fun `a track that plays resets the error streak`() = runTest {
        val h = handler()
        h.playItems(listOf(track(1), track(2), track(3)))
        backend.emitError(Exception("404"))
        runCurrent()
        backend.emitState(false, Processing.BUFFERING)
        backend.emitState(true, Processing.READY)
        runCurrent()
        backend.emitError(Exception("404"))
        runCurrent()
        assertEquals(listOf("seek 0 @1", "seek 0 @2"), backend.calls.filter { it.startsWith("seek") })
    }

    @Test
    fun `a new queue starting at the same index still reports its first natural end`() = runTest {
        val h = handler()
        val done = mutableListOf<Int>()
        backgroundScope.launchCollect(h, done)
        h.playItems(listOf(track(2)))
        runCurrent()
        h.playItems(listOf(track(2), track(3))) // index 0 again: no index change from the player
        runCurrent()
        backend.emitIndex(1)
        runCurrent()
        assertEquals(listOf(0), done)
    }

    @Test
    fun `natural completion is reported, skips are not`() = runTest {
        val h = handler()
        val done = mutableListOf<Int>()
        backgroundScope.launchCollect(h, done)
        h.playItems(listOf(track(1), track(2), track(3)))
        runCurrent()
        backend.emitIndex(1) // track 1 ended on its own
        runCurrent()
        h.skipToNext() // a person skipped: not a completion
        runCurrent()
        assertEquals(listOf(0), done)
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchCollect(h: QueueHandler, into: MutableList<Int>) =
    launch(kotlinx.coroutines.Dispatchers.Unconfined) { h.itemCompleted.collect { into += it } }
