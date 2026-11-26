package com.qurantime.support

import com.qurantime.core.db.DownloadStatus
import com.qurantime.feature.downloads.DownloadEvent
import com.qurantime.feature.downloads.Downloader
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A [Downloader] tests drive: [finish] writes the task's file and reports a status. */
class FakeDownloader : Downloader {
    data class Task(val url: String, val directory: File, val filename: String)

    val tasks = linkedMapOf<String, Task>()
    val cancelled = mutableListOf<String>()

    /** What [statusOf] reports per task (absent = the task no longer exists). */
    val statuses = mutableMapOf<String, DownloadStatus>()
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<DownloadEvent> = _events
    private var n = 0

    override suspend fun enqueue(url: String, directory: File, filename: String): String {
        val id = "task-${++n}"
        tasks[id] = Task(url, directory, filename)
        return id
    }

    fun finish(id: String, status: DownloadStatus = DownloadStatus.DONE, body: String = "mp3-bytes") {
        val t = tasks.getValue(id)
        t.directory.mkdirs()
        File(t.directory, t.filename).writeText(body)
        _events.tryEmit(DownloadEvent(id, status))
    }

    fun emit(e: DownloadEvent) {
        _events.tryEmit(e)
    }

    override suspend fun statusOf(taskId: String): DownloadStatus? = statuses[taskId]
    override suspend fun cancel(taskId: String) {
        cancelled += taskId
    }
    override suspend fun pause(taskId: String) {}
}
