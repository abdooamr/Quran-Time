package com.qurantime.kotlin.feature.audio

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports of the AyahRepeatSession group of the Flutter ayah_repeat_test.dart (the PlayerController group is in Task 21). */
class AyahRepeatTest {
    // Al-Baqarah 6–7 for read 5: 54884 → 76368 ms.
    private val start = 54_884L
    private val end = 76_368L

    @Test
    fun `repeats the range three times then finishes`() {
        val s = AyahRepeatSession(start, end, times = 3)
        assertEquals(RepeatDecision.None, s.onPosition(end - 1))
        assertEquals(RepeatDecision.Seek(start), s.onPosition(end))
        assertEquals(2, s.remaining)
        assertEquals(RepeatDecision.None, s.onPosition(start + 50))
        assertEquals(RepeatDecision.Seek(start), s.onPosition(end + 5))
        assertEquals(1, s.remaining)
        assertEquals(RepeatDecision.None, s.onPosition(start))
        assertEquals(RepeatDecision.Finished, s.onPosition(end))
    }

    @Test
    fun `positions reported before the seek lands are not counted twice`() {
        val s = AyahRepeatSession(start, end, times = 3)
        assertEquals(RepeatDecision.Seek(start), s.onPosition(end))
        assertEquals(RepeatDecision.None, s.onPosition(end + 200))
        assertEquals(2, s.remaining)
    }

    @Test
    fun `a jump far past the end still triggers`() {
        val s = AyahRepeatSession(start, end, times = 2)
        assertEquals(RepeatDecision.Seek(start), s.onPosition(end + 60_000))
    }

    @Test
    fun `the file ending counts as reaching the end, even right after a rewind`() {
        val s = AyahRepeatSession(start, end, times = 2)
        assertEquals(RepeatDecision.Seek(start), s.onEnd())
        assertEquals(RepeatDecision.Finished, s.onEnd())
    }

    @Test
    fun `times 0 never finishes`() {
        val s = AyahRepeatSession(start, end, times = 0)
        repeat(50) {
            assertEquals(RepeatDecision.Seek(start), s.onPosition(end))
            s.onPosition(start)
        }
    }
}

/** Ports of the SleepTimer group of the Flutter player_controller_test.dart. */
class SleepTimerTest {
    @Test
    fun `fires once after the duration`() = runTest {
        var fired = 0
        val t = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        t.start(15 * 60_000L)
        advanceTimeBy(14 * 60_000L + 59_000L)
        runCurrent()
        assertEquals(0, fired)
        assertEquals(1_000L, t.remainingMs())
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, fired)
        assertNull(t.remainingMs())
        advanceTimeBy(3_600_000L)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun `cancel stops it`() = runTest {
        var fired = 0
        val t = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        t.start(60_000L)
        t.cancel()
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(0, fired)
        assertFalse(t.state.value.active)
    }

    @Test
    fun `end of track fires on completion only when armed`() = runTest {
        var fired = 0
        val t = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        t.onTrackCompleted()
        assertEquals(0, fired)
        t.startEndOfTrack()
        assertTrue(t.state.value.endOfTrack)
        t.onTrackCompleted()
        assertEquals(1, fired)
        assertFalse(t.state.value.endOfTrack)
    }
}
