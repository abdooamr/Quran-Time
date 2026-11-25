package com.qurantime.kotlin.feature.followalong

import com.qurantime.kotlin.core.settings.SettingsStore
import com.qurantime.kotlin.feature.audio.PlayableItem
import com.qurantime.kotlin.feature.audio.PlayerController
import com.qurantime.kotlin.feature.audio.SurahTrack
import com.qurantime.kotlin.feature.catalog.Surah
import com.qurantime.kotlin.feature.timing.AyahTiming
import com.qurantime.kotlin.feature.timing.TimedReadMatcher
import com.qurantime.kotlin.feature.timing.TimingRepository
import com.qurantime.kotlin.feature.timing.timingIndexAt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The recited ayah of a timed recording, or inactive. Changes once per ayah, not per position. */
data class FollowState(
    val readId: Int? = null,
    val surah: Int? = null,
    val timings: List<AyahTiming> = emptyList(),
    val index: Int? = null,
) {
    val active: Boolean get() = timings.isNotEmpty()
    val current: AyahTiming? get() = index?.let { timings.getOrNull(it) }
    val currentPage: Int? get() = current?.page
}

/**
 * Joins the player's position to the ayah timing: which ayah is being
 * recited and on which page. Also plays the Mushaf reciter from an ayah and
 * starts ayah/range repeats.
 */
class FollowAlongController(
    private val player: PlayerController,
    private val timing: TimingRepository,
    private val matcher: suspend () -> TimedReadMatcher,
    private val suwar: suspend () -> List<Surah>,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FollowState())
    val state: StateFlow<FollowState> = _state.asStateFlow()
    private var loading: Job? = null

    init {
        scope.launch { player.state.map { it.item }.distinctUntilChanged().collect(::load) }
        scope.launch { player.positionMs.collect(::onPosition) }
    }

    /**
     * Plays the Mushaf reciter's recording of [surah] from [ayah], queueing
     * the rest of that recording after it. Returns false when the recording
     * doesn't have the surah (nothing is played then).
     */
    suspend fun playFromAyah(surah: Int, ayah: Int): Boolean {
        val readId = settings.state.value.mushafReadId
        val hit = matcher().moshafForRead(readId) ?: return false
        if (!hit.moshaf.has(surah)) return false
        val names = suwar().associate { it.id to it.name }
        val tracks = hit.moshaf.surahs.map { s ->
            SurahTrack(hit.reciter.id, hit.reciter.name, hit.moshaf.id, hit.moshaf.name, hit.moshaf.server, s, names[s] ?: "$s")
        }
        val start = try {
            timing.timing(readId, surah).firstOrNull { it.ayah == ayah }?.startMs ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0L // Without timing, play from the start of the surah.
        }
        player.playItems(tracks, hit.moshaf.surahs.indexOf(surah), start)
        return true
    }

    /** Ayah numbers (without the intro) of [surah] in the recording that would be used. */
    suspend fun ayahsOf(surah: Int): List<Int> = try {
        val s = _state.value
        val t = if (s.active && s.surah == surah) s.timings else timing.timing(settings.state.value.mushafReadId, surah)
        t.filter { it.ayah > 0 }.map { it.ayah }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Repeats ayahs [from]..[to] of [surah] [times] times (0 = until stopped),
     * starting the Mushaf reciter first when that surah isn't the one
     * following now. Returns false when it can't.
     */
    suspend fun repeatAyahs(surah: Int, from: Int, to: Int, times: Int): Boolean {
        val now = _state.value
        val following = now.active && now.surah == surah
        val readId = if (following) now.readId!! else settings.state.value.mushafReadId
        if (!following && !playFromAyah(surah, from)) return false
        val t = try {
            if (following) now.timings else timing.timing(readId, surah)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val a = t.firstOrNull { it.ayah == from } ?: return false
        val b = t.firstOrNull { it.ayah == to } ?: return false
        player.startRangeRepeat(a.startMs, b.endMs, times)
        return true
    }

    private fun load(item: PlayableItem?) {
        loading?.cancel()
        _state.value = FollowState()
        if (item !is SurahTrack) return
        loading = scope.launch {
            try {
                val read = matcher().readForServer(item.server) ?: return@launch
                val t = timing.timing(read.id, item.surah)
                _state.value = FollowState(read.id, item.surah, t, timingIndexAt(t, player.currentPositionMs))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No timing (offline, or the API failed): just no highlighting.
            }
        }
    }

    private fun onPosition(ms: Long) {
        val s = _state.value
        if (!s.active) return
        val i = timingIndexAt(s.timings, ms)
        if (i != s.index) _state.value = s.copy(index = i)
    }
}
