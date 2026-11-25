package com.qurantime.kotlin.feature.audio

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ONE, ALL }

enum class Processing { IDLE, LOADING, BUFFERING, READY, COMPLETED }

data class BackendState(val playing: Boolean, val processing: Processing)

/** One queue entry: what to play ([uri]: a downloaded file or the network) and what it is. */
data class QueueSource(val uri: String, val item: PlayableItem)

/**
 * The media engine under [QueueHandler] (ExoPlayer in the app, a fake in
 * tests). Calls other than [setSources] return at once; their effects arrive
 * through the flows.
 */
interface PlayerBackend {
    /** Loads the queue; throws when the first item can't be loaded. */
    suspend fun setSources(sources: List<QueueSource>, startIndex: Int, startPositionMs: Long)
    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long, index: Int? = null)
    fun setSpeed(speed: Float)
    fun setRepeat(mode: RepeatMode)
    fun release()

    val positionMs: Long
    val positions: SharedFlow<Long>
    val currentIndex: StateFlow<Int?>
    val state: StateFlow<BackendState>
    val durationMs: StateFlow<Long?>
    val errors: SharedFlow<Throwable>
}

/** The title and subtitle the player and notification show for an item. */
val PlayableItem.title: String
    get() = when (this) {
        is SurahTrack -> surahName
        is RadioStream -> name
        is AudioClip -> title
    }

val PlayableItem.subtitle: String?
    get() = when (this) {
        is SurahTrack -> reciterName
        is RadioStream -> null
        is AudioClip -> subtitle
    }
