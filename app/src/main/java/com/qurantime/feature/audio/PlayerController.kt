package com.qurantime.feature.audio

import com.qurantime.core.settings.SettingsStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * What the player UI shows. The position is not here: it changes four times
 * a second, so only the slider reads it ([PlayerController.positionMs]) and
 * the rest of the app doesn't redraw while playing.
 */
data class PlayerUiState(
    val item: PlayableItem? = null,
    val playing: Boolean = false,
    val durationMs: Long? = null,
    val speed: Float = 1f,
    val repeat: RepeatMode = RepeatMode.OFF,
    val isLive: Boolean = false,
    val error: String? = null,
    val repeating: Boolean = false,
    val sleep: SleepState = SleepState(),
)

/**
 * The app's player: plays queues, remembers where you were (every 5 s while
 * playing, and on pause), restores it paused after a restart, and runs the
 * sleep timer and ayah/range repeat on top of [QueueHandler].
 */
class PlayerController(
    val queue: QueueHandler,
    private val settings: SettingsStore,
    private val requestNotificationPermission: suspend () -> Unit,
    private val scope: CoroutineScope,
    /** Connects the media session service; called before anything is loaded. */
    private val ensureService: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
) {
    val sleep = SleepTimer(scope, clock) { scope.launch { pauseAndSave() } }

    private val repeatingFlow = MutableStateFlow(false)
    private var repeatSession: AyahRepeatSession? = null
    private var repeatIndex: Int? = null
    private var askedPermission = false
    private var lastIndex: Int? = null
    private var saveTicker: Job? = null

    val state: StateFlow<PlayerUiState> = combine(queue.state, queue.current, repeatingFlow, sleep.state) { s, now, repeating, sleepState ->
        PlayerUiState(
            item = now?.item,
            playing = s.playing,
            durationMs = now?.durationMs,
            speed = s.speed,
            repeat = s.repeat,
            isLive = s.isLive,
            error = s.errorMessage,
            repeating = repeating,
            sleep = sleepState,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerUiState())

    /** Position updates of the current item (for the slider and follow-along). */
    val positionMs: Flow<Long> get() = queue.positions
    val currentPositionMs: Long get() = queue.positionMs

    init {
        scope.launch { queue.positions.collect(::onPosition) }
        scope.launch { queue.itemCompleted.collect(::onItemCompleted) }
        scope.launch { queue.state.collect(::onState) }
    }

    suspend fun playItems(items: List<PlayableItem>, startIndex: Int = 0, startPositionMs: Long = 0) {
        // Android 13+ needs permission for the playback notification; ask once.
        if (!askedPermission) {
            askedPermission = true
            scope.launch { runCatching { requestNotificationPermission() } }
        }
        ensureService()
        lastIndex = null // a new queue is not the old item finishing
        cancelRepeat()
        queue.playItems(items, startIndex, startPositionMs)
    }

    suspend fun togglePlay() {
        if (queue.state.value.playing) {
            pauseAndSave()
        } else {
            ensureService()
            queue.play()
        }
    }

    fun seek(ms: Long) = queue.seek(ms)

    fun next() {
        cancelRepeat()
        queue.skipToNext()
    }

    fun previous() {
        cancelRepeat()
        queue.skipToPrevious()
    }

    fun setSpeed(speed: Float) = queue.setSpeed(speed)

    fun cycleRepeat() = queue.setRepeatMode(
        when (queue.state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        },
    )

    /** Stops playback and cancels the sleep timer and any repeat. */
    fun stop() {
        cancelRepeat()
        sleep.cancel()
        queue.stop()
    }

    /** Loads the last saved queue and position, paused. A broken saved state is ignored. */
    suspend fun restore() {
        val raw = settings.state.value.lastPlaybackJson ?: return
        try {
            val j = Json.parseToJsonElement(raw).jsonObject
            val items = j["items"]!!.jsonArray.map { PlayableItem.fromJson(it.jsonObject) }
            if (items.isEmpty()) return
            val index = (j["index"]?.jsonPrimitive?.int ?: 0).coerceIn(0, items.lastIndex)
            val start = if (items[index] is RadioStream) 0L else j["positionMs"]?.jsonPrimitive?.long ?: 0L
            ensureService()
            queue.prepareItems(items, index, start)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** Repeats [startMs]..[endMs] of the current item [times] times (0 = until cancelled), then pauses. */
    fun startRangeRepeat(startMs: Long, endMs: Long, times: Int) {
        repeatIndex = queue.state.value.queueIndex
        repeatSession = AyahRepeatSession(startMs, endMs, times)
        repeatingFlow.value = true
        queue.seek(startMs)
        queue.play()
    }

    fun cancelRepeat() {
        repeatSession = null
        repeatingFlow.value = false
    }

    private fun onPosition(ms: Long) {
        val session = repeatSession ?: return
        // Positions of another item (after a skip) don't belong to this range.
        if (queue.state.value.queueIndex != repeatIndex) return
        apply(session.onPosition(ms))
    }

    /** An item finished on its own: end-of-surah sleep, and a range that ends with the file. */
    private fun onItemCompleted(index: Int) {
        sleep.onTrackCompleted()
        val session = repeatSession ?: return
        if (index != repeatIndex) return
        apply(session.onEnd())
    }

    private fun apply(decision: RepeatDecision) {
        when (decision) {
            is RepeatDecision.Seek -> seekInRange(decision.ms)
            RepeatDecision.Finished -> {
                val index = repeatIndex
                val start = repeatSession!!.startMs
                cancelRepeat()
                scope.launch {
                    pauseAndSave()
                    // If playback already ran into the next item, come back to the range.
                    if (index != null && queue.state.value.queueIndex != index) queue.seekToItem(start, index)
                }
            }
            RepeatDecision.None -> {}
        }
    }

    private fun seekInRange(ms: Long) {
        val index = repeatIndex
        if (index != null && queue.state.value.queueIndex != index) queue.seekToItem(ms, index) else queue.seek(ms)
    }

    private suspend fun pauseAndSave() {
        queue.pause()
        save()
    }

    private suspend fun save() {
        val items = queue.items
        if (items.isEmpty()) return
        settings.setLastPlaybackJson(
            buildJsonObject {
                put("items", JsonArray(items.map { it.toJson() }))
                put("index", queue.state.value.queueIndex ?: 0)
                put("positionMs", queue.positionMs)
            }.toString(),
        )
    }

    private fun onState(s: PlaybackSnapshot) {
        val index = s.queueIndex
        val advanced = lastIndex != null && index != null && index != lastIndex
        // A skip caused by a playback error must not replace the saved place.
        if (advanced && s.errorMessage == null) scope.launch { save() }
        lastIndex = index
        if (s.playing && saveTicker == null) {
            saveTicker = scope.launch {
                while (isActive) {
                    delay(5_000)
                    save()
                }
            }
        } else if (!s.playing) {
            saveTicker?.cancel()
            saveTicker = null
        }
    }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}
