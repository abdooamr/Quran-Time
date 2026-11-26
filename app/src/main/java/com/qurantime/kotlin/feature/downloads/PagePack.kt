package com.qurantime.kotlin.feature.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qurantime.kotlin.QuranTimeApp
import com.qurantime.kotlin.core.api.MUSHAF_PAGES
import com.qurantime.kotlin.feature.mushaf.AyahGeometryRepository
import com.qurantime.kotlin.feature.mushaf.image.PackProgress
import com.qurantime.kotlin.feature.mushaf.image.PageImageStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Downloading every Mushaf page (and the ayah outlines) for offline reading. */
interface PagePack {
    /** Progress while running, null when idle. */
    val progress: Flow<PackProgress?>
    fun start(widthPx: Int)
    fun cancel()
}

/**
 * The page pack as a WorkManager job, so it keeps going when the app is
 * closed. A worker may run about 10 minutes, so each run converts pages for
 * 8 and then asks to run again; pages on disk are skipped, so it continues.
 */
class WorkManagerPagePack(private val context: Context) : PagePack {
    private val wm get() = WorkManager.getInstance(context)

    override val progress: Flow<PackProgress?> = wm.getWorkInfosForUniqueWorkFlow(PagePackWorker.NAME).map { infos ->
        val info = infos.firstOrNull { !it.state.isFinished } ?: return@map null
        val p = info.progress
        PackProgress(p.getInt(PagePackWorker.KEY_DONE, 0), MUSHAF_PAGES, p.getInt(PagePackWorker.KEY_FAILED, 0))
    }

    override fun start(widthPx: Int) {
        val request = OneTimeWorkRequestBuilder<PagePackWorker>()
            .setInputData(workDataOf(PagePackWorker.KEY_WIDTH to widthPx))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(PagePackWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        wm.cancelUniqueWork(PagePackWorker.NAME)
    }
}

class PagePackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as QuranTimeApp).container
        val width = inputData.getInt(KEY_WIDTH, 1080)
        var last: PackProgress? = null
        val finished = withTimeoutOrNull(RUN_MS) {
            c.pages.downloadAll(width).collect {
                last = it
                setProgress(workDataOf(KEY_DONE to it.done, KEY_FAILED to it.failed))
            }
            true
        } ?: false
        if (!finished) return Result.retry() // out of time: continue in the next run
        try {
            c.geometry.ensureAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Outlines load later, page by page, when online.
        }
        return if ((last?.failed ?: 0) > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        const val NAME = "page-pack"
        const val KEY_WIDTH = "width"
        const val KEY_DONE = "done"
        const val KEY_FAILED = "failed"
        private const val RUN_MS = 8 * 60_000L
    }
}

/** The page pack run in a coroutine (tests, and anywhere WorkManager isn't wanted). */
class InProcessPagePack(
    private val pages: PageImageStore,
    private val geometry: AyahGeometryRepository,
    private val scope: CoroutineScope,
) : PagePack {
    private val state = MutableStateFlow<PackProgress?>(null)
    override val progress: StateFlow<PackProgress?> = state
    private var job: Job? = null

    override fun start(widthPx: Int) {
        if (job?.isActive == true) return
        state.value = PackProgress(0, MUSHAF_PAGES, 0)
        job = scope.launch {
            try {
                pages.downloadAll(widthPx).collect { state.value = it }
                runCatching { geometry.ensureAll() }
            } finally {
                state.value = null
            }
        }
    }

    override fun cancel() {
        job?.cancel()
    }
}
