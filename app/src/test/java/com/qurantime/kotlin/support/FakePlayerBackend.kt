package com.qurantime.kotlin.support

import com.qurantime.kotlin.feature.audio.BackendState
import com.qurantime.kotlin.feature.audio.PlayerBackend
import com.qurantime.kotlin.feature.audio.Processing
import com.qurantime.kotlin.feature.audio.RepeatMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PlayerBackend] that records calls (`setSources`, `play`, `seek 0 @1`…) and lets tests emit events. */
class FakePlayerBackend : PlayerBackend {
    val calls = mutableListOf<String>()

    /** When true, [setSources] throws, as when the first source can't load. */
    var failSetSources = false
    var uris: List<String> = emptyList()
    var lastSpeed = 1f
    var lastRepeat = RepeatMode.OFF

    private val _index = MutableStateFlow<Int?>(null)
    private val _state = MutableStateFlow(BackendState(false, Processing.IDLE))
    private val _duration = MutableStateFlow<Long?>(null)
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)
    private val _positions = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    private var position = 0L

    override val currentIndex: StateFlow<Int?> = _index
    override val state: StateFlow<BackendState> = _state
    override val durationMs: StateFlow<Long?> = _duration
    override val errors: SharedFlow<Throwable> = _errors
    override val positions: SharedFlow<Long> = _positions
    override val positionMs: Long get() = position

    fun emitIndex(i: Int) {
        _index.value = i
    }

    fun emitPosition(ms: Long) {
        position = ms
        _positions.tryEmit(ms)
    }

    fun emitState(playing: Boolean, processing: Processing) {
        _state.value = BackendState(playing, processing)
    }

    fun emitDuration(ms: Long?) {
        _duration.value = ms
    }

    fun emitError(e: Throwable) {
        _errors.tryEmit(e)
    }

    override suspend fun setSources(urls: List<String>, startIndex: Int, startPositionMs: Long) {
        calls += "setSources"
        if (failSetSources) throw IllegalStateException("Source error")
        uris = urls
        position = startPositionMs
        _index.value = startIndex
    }

    override fun play() {
        calls += "play"
        _state.value = BackendState(true, Processing.READY)
    }

    override fun pause() {
        calls += "pause"
        _state.value = BackendState(false, Processing.READY)
    }

    override fun stop() {
        calls += "stop"
        _state.value = BackendState(false, Processing.IDLE)
    }

    override fun seek(positionMs: Long, index: Int?) {
        calls += "seek $positionMs" + (index?.let { " @$it" } ?: "")
        position = positionMs
        if (index != null) _index.value = index
    }

    override fun setSpeed(speed: Float) {
        calls += "speed $speed"
        lastSpeed = speed
    }

    override fun setRepeat(mode: RepeatMode) {
        calls += "repeat $mode"
        lastRepeat = mode
    }

    override fun release() {
        calls += "release"
    }
}
