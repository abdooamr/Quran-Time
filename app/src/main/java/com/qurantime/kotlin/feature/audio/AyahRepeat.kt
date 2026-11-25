package com.qurantime.kotlin.feature.audio

/** What the player should do after a position report during a range repeat. */
sealed interface RepeatDecision {
    data object None : RepeatDecision
    data class Seek(val ms: Long) : RepeatDecision
    data object Finished : RepeatDecision
}

/**
 * Repeats [startMs]..[endMs] of a recording [times] times (0 = forever).
 * Feed it every position report; it says when to seek back and when to stop.
 */
class AyahRepeatSession(val startMs: Long, val endMs: Long, val times: Int) {
    var remaining: Int = times
        private set

    /**
     * After asking for a seek, positions are ignored until playback is back
     * inside the range, so a late position report isn't counted twice.
     */
    private var rewinding = false

    /**
     * The recording itself ended inside the range: that always means the
     * range was played through, even if no position report reached [endMs].
     */
    fun onEnd(): RepeatDecision {
        rewinding = false
        return onPosition(endMs)
    }

    fun onPosition(ms: Long): RepeatDecision {
        if (rewinding) {
            if (ms < endMs) rewinding = false
            return RepeatDecision.None
        }
        if (ms < endMs) return RepeatDecision.None
        if (times == 0) {
            rewinding = true
            return RepeatDecision.Seek(startMs)
        }
        remaining--
        if (remaining <= 0) return RepeatDecision.Finished
        rewinding = true
        return RepeatDecision.Seek(startMs)
    }
}
