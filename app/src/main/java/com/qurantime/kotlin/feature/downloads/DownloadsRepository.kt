package com.qurantime.kotlin.feature.downloads

import com.qurantime.kotlin.core.db.DownloadEntity
import com.qurantime.kotlin.core.db.DownloadStatus
import com.qurantime.kotlin.core.db.DownloadsDao
import com.qurantime.kotlin.feature.audio.SurahTrack
import com.qurantime.kotlin.feature.catalog.Surah
import com.qurantime.kotlin.feature.reciters.Moshaf
import com.qurantime.kotlin.feature.reciters.Reciter
import com.qurantime.kotlin.feature.reciters.ui.tracksFor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Downloaded recordings: one row per file, under `<root>/audio/<moshafId>/NNN.mp3`.
 * Events from the [Downloader] are applied one at a time, in order.
 */
class DownloadsRepository(
    private val dao: DownloadsDao,
    private val downloader: Downloader,
    private val root: File,
    scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val received = MutableStateFlow(0L)
    private val handled = MutableStateFlow(0L)

    private val queue = Channel<DownloadEvent>(Channel.UNLIMITED)

    init {
        // Events are counted and queued the moment they arrive, then applied
        // one at a time, so settled() knows about every event received.
        scope.launch {
            downloader.events.collect { e ->
                received.value++
                queue.send(e)
            }
        }
        scope.launch {
            for (e in queue) {
                try {
                    lock.withLock { onEvent(e) }
                } finally {
                    handled.value++
                }
            }
        }
    }

    /** Completes once every download event received so far is handled. */
    suspend fun settled() {
        val target = received.value
        handled.first { it >= target }
    }

    suspend fun all(): List<DownloadEntity> = dao.all()

    fun watchAll(): Flow<List<DownloadEntity>> = dao.watchAll()

    /**
     * Catches up with downloads that changed while the app wasn't listening
     * (finished or vanished while it was closed). Call once at start-up.
     */
    suspend fun reconcile() = lock.withLock {
        for (row in dao.all()) {
            if (row.status == DownloadStatus.DONE || row.status == DownloadStatus.FAILED || row.status == DownloadStatus.MISSING) continue
            val taskId = row.taskId
            val now = taskId?.let { downloader.statusOf(it) }
            when {
                now == DownloadStatus.DONE || now == DownloadStatus.FAILED -> onEvent(DownloadEvent(taskId!!, now))
                now == null -> {
                    deleteQuietly(File(row.path + ".part"))
                    dao.update(row.copy(status = DownloadStatus.FAILED))
                }
            }
        }
    }

    private fun dirFor(moshafId: Int) = File(root, "audio/$moshafId")
    private fun fileFor(t: SurahTrack) = File(dirFor(t.moshafId), t.surah.toString().padStart(3, '0') + ".mp3")

    suspend fun enqueueSurah(t: SurahTrack) = lock.withLock {
        val existing = dao.byUrl(t.url)
        if (existing != null && existing.status != DownloadStatus.FAILED && existing.status != DownloadStatus.MISSING) return@withLock
        val file = fileFor(t)
        val taskId = downloader.enqueue(t.url, dirFor(t.moshafId), file.name + ".part")
        val row = DownloadEntity(
            id = existing?.id ?: 0,
            kind = "surah",
            moshafId = t.moshafId,
            surah = t.surah,
            url = t.url,
            path = file.path,
            status = DownloadStatus.QUEUED,
            totalBytes = null,
            receivedBytes = 0,
            taskId = taskId,
        )
        if (existing == null) dao.insert(row) else dao.update(row)
    }

    suspend fun enqueueMoshaf(r: Reciter, m: Moshaf, suwar: List<Surah>) {
        for (t in tracksFor(r, m, suwar)) enqueueSurah(t)
    }

    /**
     * The downloaded file for [remoteUrl], or null (then it is streamed). A
     * finished file that has since disappeared is marked missing.
     */
    suspend fun localPathFor(remoteUrl: String): String? {
        val row = dao.byUrl(remoteUrl) ?: return null
        if (row.status != DownloadStatus.DONE) return null
        if (withContext(Dispatchers.IO) { File(row.path).exists() }) return row.path
        dao.update(row.copy(status = DownloadStatus.MISSING))
        return null
    }

    suspend fun pause(id: Long) = lock.withLock {
        val row = dao.byId(id) ?: return@withLock
        row.taskId?.let { downloader.pause(it) }
        dao.update(row.copy(status = DownloadStatus.PAUSED))
    }

    /** Continues a paused download from its partial file. */
    suspend fun resume(id: Long) = lock.withLock {
        val row = dao.byId(id) ?: return@withLock
        val file = File(row.path)
        val taskId = downloader.enqueue(row.url, file.parentFile!!, file.name + ".part")
        dao.update(row.copy(status = DownloadStatus.QUEUED, taskId = taskId))
    }

    suspend fun cancel(id: Long) = lock.withLock {
        val row = dao.byId(id) ?: return@withLock
        row.taskId?.let { downloader.cancel(it) }
        deleteFiles(row)
        dao.deleteById(id)
    }

    suspend fun deleteMoshaf(moshafId: Int) = lock.withLock {
        for (row in dao.byMoshaf(moshafId)) {
            if (row.taskId != null && row.status != DownloadStatus.DONE) downloader.cancel(row.taskId)
            deleteFiles(row)
            dao.deleteById(row.id)
        }
    }

    /** Bytes of finished downloads per recording. */
    suspend fun bytesByMoshaf(): Map<Int, Long> = dao.all()
        .filter { it.status == DownloadStatus.DONE && it.moshafId != null }
        .groupBy { it.moshafId!! }
        .mapValues { (_, rows) -> rows.sumOf { it.totalBytes ?: 0L } }

    private suspend fun onEvent(e: DownloadEvent) {
        val row = dao.byTaskId(e.taskId) ?: return
        val part = File(row.path + ".part")
        when (e.status) {
            DownloadStatus.DONE -> {
                val file = File(row.path)
                val ok = withContext(Dispatchers.IO) {
                    if (part.exists()) {
                        file.delete()
                        part.renameTo(file)
                    }
                    file.exists()
                }
                dao.update(row.copy(status = if (ok) DownloadStatus.DONE else DownloadStatus.FAILED, totalBytes = if (ok) file.length() else null))
            }
            DownloadStatus.FAILED -> {
                deleteQuietly(part)
                dao.update(row.copy(status = DownloadStatus.FAILED))
            }
            else -> {
                // A finished row never goes back (WorkManager repeats old states).
                if (row.status == DownloadStatus.DONE) return
                dao.update(row.copy(status = e.status, receivedBytes = e.received ?: row.receivedBytes, totalBytes = e.total ?: row.totalBytes))
            }
        }
    }

    private suspend fun deleteFiles(row: DownloadEntity) {
        deleteQuietly(File(row.path))
        deleteQuietly(File(row.path + ".part"))
    }

    private suspend fun deleteQuietly(f: File) = withContext(Dispatchers.IO) { f.delete() }
}
