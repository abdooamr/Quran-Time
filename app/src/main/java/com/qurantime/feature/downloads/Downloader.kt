package com.qurantime.feature.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qurantime.QuranTimeApp
import com.qurantime.core.api.await
import com.qurantime.core.db.DownloadStatus
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request

/** A change in one download (`received`/`total` in bytes, when known). */
data class DownloadEvent(val taskId: String, val status: DownloadStatus, val received: Long? = null, val total: Long? = null)

/** Background file downloads into `<directory>/<filename>` (a `.part` file the repository renames). */
interface Downloader {
    suspend fun enqueue(url: String, directory: File, filename: String): String
    /** Stops a task, keeping its `.part` file; enqueue the same file again to resume. */
    suspend fun pause(taskId: String)
    suspend fun cancel(taskId: String)

    /** The task's status now, or null when the task no longer exists. */
    suspend fun statusOf(taskId: String): DownloadStatus?
    val events: Flow<DownloadEvent>
}

/**
 * Downloads with WorkManager: one job per file, only on a network, retried
 * with backoff, and kept across app restarts. Each job is short (one surah),
 * so no foreground service is needed. A paused job is cancelled and later
 * re-enqueued; the worker resumes from the `.part` file.
 */
class WorkManagerDownloader(private val context: Context) : Downloader {
    private val wm get() = WorkManager.getInstance(context)

    override suspend fun enqueue(url: String, directory: File, filename: String): String {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_URL to url, DownloadWorker.KEY_DIR to directory.path, DownloadWorker.KEY_FILE to filename))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        wm.enqueue(request)
        return request.id.toString()
    }

    override suspend fun pause(taskId: String) {
        wm.cancelWorkById(UUID.fromString(taskId))
    }

    override suspend fun cancel(taskId: String) {
        wm.cancelWorkById(UUID.fromString(taskId))
    }

    override suspend fun statusOf(taskId: String): DownloadStatus? = withContext(Dispatchers.IO) {
        wm.getWorkInfoById(UUID.fromString(taskId)).get()?.let(::statusOf)
    }

    // WorkManager re-emits every tagged job (finished ones too) on every
    // change; only the jobs whose state or progress changed are passed on.
    private val lastSeen = mutableMapOf<String, DownloadEvent>()

    override val events: Flow<DownloadEvent> = wm.getWorkInfosByTagFlow(TAG).flatMapConcat { infos ->
        val all = infos.map { info ->
            DownloadEvent(
                taskId = info.id.toString(),
                status = statusOf(info),
                received = info.progress.getLong(DownloadWorker.KEY_RECEIVED, -1).takeIf { it >= 0 },
                total = info.progress.getLong(DownloadWorker.KEY_TOTAL, -1).takeIf { it > 0 },
            )
        }
        synchronized(lastSeen) { changedEvents(lastSeen, all) }.asFlow()
    }

    private fun statusOf(info: WorkInfo) = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.QUEUED
        WorkInfo.State.RUNNING -> DownloadStatus.RUNNING
        WorkInfo.State.SUCCEEDED -> DownloadStatus.DONE
        WorkInfo.State.FAILED -> DownloadStatus.FAILED
        WorkInfo.State.CANCELLED -> DownloadStatus.PAUSED
    }

    companion object {
        const val TAG = "qt-download"
    }
}

/** The events in [now] that differ from what [last] saw for their task; updates [last]. */
internal fun changedEvents(last: MutableMap<String, DownloadEvent>, now: List<DownloadEvent>): List<DownloadEvent> =
    now.filter { e -> last.put(e.taskId, e) != e }

/**
 * Downloads one file to its `.part` path, continuing a partial file with an
 * HTTP Range request. Network errors are retried by WorkManager (3 tries).
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val dir = inputData.getString(KEY_DIR) ?: return Result.failure()
        val name = inputData.getString(KEY_FILE) ?: return Result.failure()
        val part = File(dir, name)
        val client = (applicationContext as QuranTimeApp).container.http
        return try {
            withContext(Dispatchers.IO) {
                part.parentFile?.mkdirs()
                val have = if (part.exists()) part.length() else 0L
                val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                client.newCall(request).await().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                    // 206: continue the file; 200: the server sent it all again.
                    val append = res.code == 206
                    val start = if (append) have else 0L
                    val total = res.body.contentLength().takeIf { it > 0 }?.plus(start)
                    var received = start
                    var lastReport = 0L
                    java.io.FileOutputStream(part, append).use { out ->
                        val src = res.body.byteStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            if (received - lastReport > 256 * 1024) {
                                lastReport = received
                                setProgress(workDataOf(KEY_RECEIVED to received, KEY_TOTAL to (total ?: -1L)))
                            }
                        }
                    }
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_DIR = "dir"
        const val KEY_FILE = "file"
        const val KEY_RECEIVED = "received"
        const val KEY_TOTAL = "total"
    }
}
