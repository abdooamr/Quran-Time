package com.qurantime.kotlin.feature.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.qurantime.kotlin.core.db.AppDatabase
import com.qurantime.kotlin.core.db.DownloadStatus
import com.qurantime.kotlin.feature.audio.SurahTrack
import com.qurantime.kotlin.feature.catalog.parseSuwar
import com.qurantime.kotlin.feature.reciters.Moshaf
import com.qurantime.kotlin.feature.reciters.parseReciters
import com.qurantime.kotlin.support.FakeDownloader
import com.qurantime.kotlin.support.loadFixtureJson
import com.qurantime.kotlin.support.memoryDb
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

private fun track(m: Moshaf, surah: Int) = SurahTrack(231, "Hazza", m.id, m.name, m.server, surah, "S$surah")

/** Ports of the Flutter downloads_repository_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val hazza = parseReciters(loadFixtureJson("reciters_231_eng.json")).single()
    private val m = hazza.moshaf.first()
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))
    private lateinit var db: AppDatabase
    private val dl = FakeDownloader()

    @Before fun setUp() {
        db = memoryDb()
    }

    @After fun tearDown() = db.close()

    private fun TestScope.repo() =
        DownloadsRepository(db.downloadsDao(), dl, tmp.root, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    private fun finalPath(surah: Int) = File(tmp.root, "audio/${m.id}/${surah.toString().padStart(3, '0')}.mp3")

    @Test
    fun `enqueueSurah creates one queued row per url`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        repo.enqueueSurah(track(m, 18))
        val rows = repo.all()
        assertEquals(1, rows.size)
        assertEquals(DownloadStatus.QUEUED, rows.single().status)
        assertTrue(rows.single().url.endsWith("/018.mp3"))
        assertEquals("018.mp3.part", dl.tasks.values.single().filename)
    }

    @Test
    fun `a finished download is played from disk`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.finish(dl.tasks.keys.single())
        repo.settled()
        assertEquals(finalPath(18).path, repo.localPathFor(track(m, 18).url))
        assertTrue(finalPath(18).exists())
        assertFalse(File(finalPath(18).path + ".part").exists())
        assertEquals(DownloadStatus.DONE, repo.all().single().status)
    }

    @Test
    fun `a file deleted outside the app is reported missing and streamed instead`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.finish(dl.tasks.keys.single())
        repo.settled()
        finalPath(18).delete()
        assertNull(repo.localPathFor(track(m, 18).url))
        assertEquals(DownloadStatus.MISSING, repo.all().single().status)
    }

    @Test
    fun `a failed download leaves no final file`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.finish(dl.tasks.keys.single(), status = DownloadStatus.FAILED, body = "half")
        repo.settled()
        assertFalse(finalPath(18).exists())
        assertFalse(File(finalPath(18).path + ".part").exists())
        assertNull(repo.localPathFor(track(m, 18).url))
        assertEquals(DownloadStatus.FAILED, repo.all().single().status)
    }

    @Test
    fun `enqueueMoshaf adds every surah once, skipping finished ones`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, m.surahs.first()))
        dl.finish(dl.tasks.keys.single())
        repo.settled()
        repo.enqueueMoshaf(hazza, m, suwar)
        assertEquals(m.surahs.size, repo.all().size)
        assertEquals(m.surahs.size, dl.tasks.size)
    }

    @Test
    fun `a failed download can be enqueued again`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.finish(dl.tasks.keys.single(), status = DownloadStatus.FAILED)
        repo.settled()
        repo.enqueueSurah(track(m, 18))
        assertEquals(2, dl.tasks.size)
        assertEquals(DownloadStatus.QUEUED, repo.all().single().status)
    }

    @Test
    fun `cancel removes the row and the partial file`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        val row = repo.all().single()
        val part = File(finalPath(18).path + ".part").also { it.parentFile!!.mkdirs(); it.writeText("x") }
        repo.cancel(row.id)
        assertTrue(repo.all().isEmpty())
        assertFalse(part.exists())
        assertEquals(listOf(row.taskId), dl.cancelled)
    }

    @Test
    fun `deleteMoshaf removes files and rows - bytesByMoshaf sums finished files`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        repo.enqueueSurah(track(m, 19))
        for (id in dl.tasks.keys.toList()) {
            dl.finish(id, body = "12345")
            repo.settled()
        }
        assertEquals(mapOf(m.id to 10L), repo.bytesByMoshaf())
        repo.deleteMoshaf(m.id)
        assertTrue(repo.all().isEmpty())
        assertFalse(finalPath(18).exists())
    }

    @Test
    fun `reconcile finishes downloads that completed while the app was closed`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        val id = dl.tasks.keys.single()
        val t = dl.tasks.getValue(id)
        File(t.directory, t.filename).also { it.parentFile!!.mkdirs() }.writeText("done-in-background")
        dl.statuses[id] = DownloadStatus.DONE
        repo.reconcile()
        assertTrue(finalPath(18).exists())
        assertEquals(DownloadStatus.DONE, repo.all().single().status)
        assertNotNull(repo.localPathFor(track(m, 18).url))
    }

    @Test
    fun `reconcile marks downloads whose task is gone as failed, so they can be retried`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        repo.reconcile()
        assertEquals(DownloadStatus.FAILED, repo.all().single().status)
        repo.enqueueSurah(track(m, 18))
        assertEquals(2, dl.tasks.size)
    }

    @Test
    fun `reconcile leaves downloads that are still running alone`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.statuses[dl.tasks.keys.single()] = DownloadStatus.RUNNING
        repo.reconcile()
        assertEquals(DownloadStatus.QUEUED, repo.all().single().status)
    }

    // --- added ---

    @Test
    fun `events for one row are applied in order`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        val id = dl.tasks.keys.single()
        dl.emit(DownloadEvent(id, DownloadStatus.RUNNING, received = 10, total = 100))
        dl.emit(DownloadEvent(id, DownloadStatus.RUNNING, received = 60, total = 100))
        dl.finish(id)
        repo.settled()
        val row = repo.all().single()
        assertEquals(DownloadStatus.DONE, row.status)
    }

    @Test
    fun `deleted file reports no local path`() = runTest {
        val repo = repo()
        repo.enqueueSurah(track(m, 18))
        dl.finish(dl.tasks.keys.single())
        repo.settled()
        File(tmp.root, "audio").deleteRecursively()
        assertNull(repo.localPathFor(track(m, 18).url))
    }
}

@RunWith(AndroidJUnit4::class)
class DownloadWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    @Test
    fun `resumes from the part file with a Range header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-9/10").setBody("67890"))
        val part = File(tmp.root, "018.mp3.part").also { it.writeText("12345") }
        val worker = TestListenableWorkerBuilder<DownloadWorker>(ApplicationProvider.getApplicationContext<Context>())
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_URL, server.url("/018.mp3").toString())
                    .putString(DownloadWorker.KEY_DIR, tmp.root.path)
                    .putString(DownloadWorker.KEY_FILE, "018.mp3.part")
                    .build(),
            )
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success()::class, result::class)
        assertEquals("bytes=5-", server.takeRequest().getHeader("Range"))
        assertEquals("1234567890", part.readText())
    }
}
