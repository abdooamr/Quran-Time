package com.qurantime.kotlin.feature.mushaf

import com.qurantime.kotlin.core.api.MUSHAF_PAGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The reader's position: the page on screen, jump requests, and whether a
 * recitation may turn pages. Saves the last page shortly after the reader
 * stops on it, and prefetches the pages around it.
 */
class MushafController(
    initialPage: Int,
    private val saveLastPage: suspend (Int) -> Unit,
    private val prefetch: suspend (page: Int, widthPx: Int) -> Unit,
    private val scope: CoroutineScope,
    private val saveDelayMs: Long = 500,
) {
    private val _currentPage = MutableStateFlow(initialPage.coerceIn(1, MUSHAF_PAGES))
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _followPaused = MutableStateFlow(false)

    /**
     * True after the reader swipes away from the page being recited; the
     * recitation then stops turning pages until [resumeFollow].
     */
    val followPaused: StateFlow<Boolean> = _followPaused.asStateFlow()

    private val _jumps = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** Requests for the reader to move to a page (emitted by [goTo]). */
    val jumpRequests: SharedFlow<Int> = _jumps.asSharedFlow()

    /** The page area's width in pixels, set by the reader; prefetched pages are made for it. */
    @Volatile var pageWidthPx: Int = 1080

    private var saveJob: Job? = null

    fun pauseFollow() {
        _followPaused.value = true
    }

    fun resumeFollow() {
        _followPaused.value = false
    }

    /** Called by the reader whenever a page settles on screen. */
    fun onPageShown(page: Int) {
        _currentPage.value = page
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDelayMs)
            saveLastPage(page)
        }
        val width = pageWidthPx
        scope.launch { runCatching { prefetch(page, width) } }
    }

    fun goTo(page: Int) {
        val target = page.coerceIn(1, MUSHAF_PAGES)
        _currentPage.value = target
        _jumps.tryEmit(target)
    }
}
