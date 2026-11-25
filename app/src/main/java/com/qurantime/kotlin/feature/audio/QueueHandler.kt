package com.qurantime.kotlin.feature.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What is loaded at the current queue position. */
data class NowPlaying(val item: PlayableItem, val index: Int, val uri: String, val durationMs: Long?)

/** The player's state as the app and the media session see it. */
data class PlaybackSnapshot(
    val playing: Boolean = false,
    val processing: Processing = Processing.IDLE,
    val queueIndex: Int? = null,
    val speed: Float = 1f,
    val repeat: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null,
    /** Radio: no seeking, no next/previous. */
    val isLive: Boolean = false,
) {
    val canSeek: Boolean get() = !isLive
    val canSkip: Boolean get() = !isLive
}

/**
 * The queue logic of the player, with no Android types: what to load, where
 * an item came from (a download or the network), what counts as an item
 * finishing, and how errors are handled. [PlaybackService] puts a media
 * session on top; [PlayerController] adds repeat, sleep and restore.
 */
class QueueHandler(
    private val backend: PlayerBackend,
    private val localPathFor: suspend (String) -> String?,
    private val scope: CoroutineScope,
) {
    var items: List<PlayableItem> = emptyList()
        private set
    private var uris: List<String> = emptyList()

    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    private val _current = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = _current.asStateFlow()

    private val _completed = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** The index of an item that finished on its own (the recording ended), never for skips. */
    val itemCompleted: SharedFlow<Int> = _completed.asSharedFlow()

    /** Position updates of the current item. */
    val positions: SharedFlow<Long> get() = backend.positions
    val positionMs: Long get() = backend.positionMs

    private var lastIndex: Int? = null

    /** The index we deliberately moved to (a skip, an error skip, a repeat). Reaching it isn't a finish. */
    private var jumpTarget: Int? = null
    private var errorStreak = 0
    private var lastProcessing = Processing.IDLE

    val currentItem: PlayableItem? get() = backend.currentIndex.value?.let { items.getOrNull(it) }

    init {
        scope.launch { backend.currentIndex.collect(::onIndex) }
        scope.launch {
            backend.state.collect { s ->
                if (s.processing == Processing.COMPLETED && lastProcessing != Processing.COMPLETED) {
                    lastIndex?.let { _completed.tryEmit(it) }
                }
                lastProcessing = s.processing
                if (s.playing && s.processing == Processing.READY) {
                    // Something is actually playing: earlier errors are over.
                    errorStreak = 0
                    _state.value = _state.value.copy(errorMessage = null)
                }
                _state.value = _state.value.copy(playing = s.playing, processing = s.processing)
            }
        }
        scope.launch { backend.durationMs.collect { d -> _current.value?.let { if (d != null && !_state.value.isLive) _current.value = it.copy(durationMs = d) } } }
        scope.launch { backend.errors.collect(::onError) }
    }

    /** Loads [list] and plays from [startIndex] at [startPositionMs]. */
    suspend fun playItems(list: List<PlayableItem>, startIndex: Int = 0, startPositionMs: Long = 0) {
        if (!prepareItems(list, startIndex, startPositionMs)) return
        play()
    }

    /** Loads [list] paused at [startIndex] (used to restore playback). Returns false if it couldn't load. */
    suspend fun prepareItems(list: List<PlayableItem>, startIndex: Int = 0, startPositionMs: Long = 0): Boolean {
        // A new queue starts with no history: its first index isn't a finish.
        lastIndex = null
        jumpTarget = null
        errorStreak = 0
        items = list.toList()
        uris = list.map { item -> localPathFor(item.url)?.let { "file://$it" } ?: item.url }
        val index = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        _current.value = list.getOrNull(index)?.let { NowPlaying(it, index, uris[index], null) }
        _state.value = _state.value.copy(errorMessage = null, isLive = list.getOrNull(index) is RadioStream, queueIndex = index)
        return try {
            backend.setSources(uris, index, startPositionMs)
            true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message ?: e.toString())
            false
        }
    }

    fun play() {
        backend.play()
        _state.value = _state.value.copy(playing = true)
    }

    fun pause() {
        backend.pause()
        _state.value = _state.value.copy(playing = false)
    }

    fun stop() {
        backend.stop()
        _state.value = _state.value.copy(playing = false, processing = Processing.IDLE)
    }

    fun seek(ms: Long) = backend.seek(ms)

    /** Moves to [index] at [positionMs] without it counting as a completion. */
    fun seekToItem(positionMs: Long, index: Int) {
        jumpTarget = if (index == backend.currentIndex.value) null else index
        backend.seek(positionMs, index)
    }

    fun skipToQueueItem(index: Int) {
        if (index !in items.indices) return
        seekToItem(0, index)
    }

    fun skipToNext() {
        val i = backend.currentIndex.value ?: return
        if (i + 1 < items.size) skipToQueueItem(i + 1)
    }

    /** Like most players: restart the item unless we're at its very start. */
    fun skipToPrevious() {
        val i = backend.currentIndex.value ?: return
        if (backend.positionMs > 3_000 || i == 0) backend.seek(0) else skipToQueueItem(i - 1)
    }

    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        backend.setSpeed(s)
        _state.value = _state.value.copy(speed = s)
    }

    fun setRepeatMode(mode: RepeatMode) {
        backend.setRepeat(mode)
        _state.value = _state.value.copy(repeat = mode)
    }

    private fun onIndex(i: Int?) {
        if (i != null) {
            val last = lastIndex
            if (last != null && i != last) {
                if (i == jumpTarget) jumpTarget = null else _completed.tryEmit(last)
            }
            lastIndex = i
            items.getOrNull(i)?.let { item -> _current.value = NowPlaying(item, i, uris.getOrElse(i) { item.url }, null) }
        }
        _state.value = _state.value.copy(queueIndex = i, isLive = i?.let { items.getOrNull(it) } is RadioStream)
    }

    private fun onError(e: Throwable) {
        errorStreak++
        _state.value = _state.value.copy(errorMessage = e.message ?: e.toString())
        val i = backend.currentIndex.value ?: 0
        // Two failures in a row usually means no connection: stop rather than walk the whole queue.
        if (errorStreak < 2 && i + 1 < items.size) seekToItem(0, i + 1) else stop()
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
    }
}
