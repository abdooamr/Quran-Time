package com.qurantime.feature.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A sleep timer set to a time ([endsAtMs]) or to the end of the current item. */
data class SleepState(val endsAtMs: Long? = null, val endOfTrack: Boolean = false) {
    val active: Boolean get() = endsAtMs != null || endOfTrack
}

/** Stops playback after a while, or when the current surah finishes. */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFire: () -> Unit,
) {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()
    private var job: Job? = null

    /** Time left in ms, or null when no timed sleep is set. */
    fun remainingMs(): Long? = _state.value.endsAtMs?.let { (it - clock()).coerceAtLeast(0) }

    fun start(durationMs: Long) {
        job?.cancel()
        _state.value = SleepState(endsAtMs = clock() + durationMs)
        job = scope.launch {
            delay(durationMs)
            fire()
        }
    }

    fun startEndOfTrack() {
        job?.cancel()
        _state.value = SleepState(endOfTrack = true)
    }

    /** Called by the player when an item finishes on its own. */
    fun onTrackCompleted() {
        if (_state.value.endOfTrack) fire()
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepState()
    }

    private fun fire() {
        cancel()
        onFire()
    }
}
