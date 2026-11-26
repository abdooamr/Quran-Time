package com.qurantime.feature.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The app's one ExoPlayer: speech audio attributes, pauses on a phone call or
 * when headphones are unplugged, and keeps the Wi-Fi/CPU awake while
 * streaming with the screen off.
 */
fun buildPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
    .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
        /* handleAudioFocus = */ true,
    )
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .setSeekBackIncrementMs(10_000)
    .setSeekForwardIncrementMs(10_000)
    .build()

/**
 * [PlayerBackend] over a Media3 [Player]. Must be used on the player's
 * thread (the main thread); [scope] should run there too.
 */
class Media3Backend(private val player: Player, private val scope: CoroutineScope) : PlayerBackend {
    private val _index = MutableStateFlow<Int?>(null)
    private val _state = MutableStateFlow(BackendState(false, Processing.IDLE))
    private val _duration = MutableStateFlow<Long?>(null)
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    private val _positions = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private var ticker: Job? = null

    override val currentIndex: StateFlow<Int?> = _index
    override val state: StateFlow<BackendState> = _state
    override val durationMs: StateFlow<Long?> = _duration
    override val errors: SharedFlow<Throwable> = _errors
    override val positions: SharedFlow<Long> = _positions
    override val positionMs: Long get() = player.currentPosition

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                _index.value = if (p.mediaItemCount == 0) null else p.currentMediaItemIndex
                // Idle (stopped, or after an error) is never "playing", whatever
                // playWhenReady says: the player needs preparing before it can play.
                _state.value = BackendState(p.playWhenReady && p.playbackState != Player.STATE_IDLE, p.playbackState.toProcessing())
                _duration.value = p.duration.takeIf { it != C.TIME_UNSET && !p.isCurrentMediaItemLive }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) updateTicker(p.isPlaying)
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) _positions.tryEmit(p.currentPosition)
            }

            override fun onPlayerError(error: PlaybackException) {
                _errors.tryEmit(error)
            }
        })
    }

    /** Position updates four times a second, only while sound is playing (no wake-ups when paused). */
    private fun updateTicker(playing: Boolean) {
        ticker?.cancel()
        if (!playing) return
        ticker = scope.launch {
            while (isActive) {
                _positions.tryEmit(player.currentPosition)
                delay(250)
            }
        }
    }

    override suspend fun setSources(sources: List<QueueSource>, startIndex: Int, startPositionMs: Long) {
        val items: List<MediaItem> = sources.map { it.toMediaItem() }
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && cont.isActive) {
                        player.removeListener(this)
                        cont.resume(Unit)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (cont.isActive) {
                        player.removeListener(this)
                        cont.resumeWithException(error)
                    }
                }
            }
            player.addListener(listener)
            cont.invokeOnCancellation { player.removeListener(listener) }
            player.setMediaItems(items, startIndex, startPositionMs)
            player.prepare()
        }
    }

    /**
     * After stop() or a playback error the player is idle and plays nothing
     * until it is prepared again (its items are kept).
     */
    private fun prepareIfIdle() {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
    }

    override fun play() {
        prepareIfIdle()
        player.play()
    }

    override fun pause() = player.pause()
    override fun stop() {
        // stop() alone keeps playWhenReady, so a later seek would start playing by itself.
        player.pause()
        player.stop()
    }

    override fun seek(positionMs: Long, index: Int?) {
        if (index == null) player.seekTo(positionMs) else player.seekTo(index, positionMs)
        prepareIfIdle()
    }

    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override fun setRepeat(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun release() {
        ticker?.cancel()
        player.release()
    }

    private fun Int.toProcessing() = when (this) {
        Player.STATE_BUFFERING -> Processing.BUFFERING
        Player.STATE_READY -> Processing.READY
        Player.STATE_ENDED -> Processing.COMPLETED
        else -> Processing.IDLE
    }
}
