package com.qurantime.kotlin.feature.downloads

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.core.db.DownloadEntity
import com.qurantime.kotlin.core.db.DownloadStatus
import com.qurantime.kotlin.feature.mushaf.image.PackProgress
import com.qurantime.kotlin.feature.mushaf.image.PageImageStore
import com.qurantime.kotlin.feature.mushaf.image.RawDeflateMaskCodec
import com.qurantime.kotlin.feature.reciters.parseReciters
import com.qurantime.kotlin.support.FakePageFetcher
import com.qurantime.kotlin.support.TestContainer
import com.qurantime.kotlin.support.await
import com.qurantime.kotlin.support.launchApp
import com.qurantime.kotlin.support.loadFixtureJson
import com.qurantime.kotlin.support.solidRasterize
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Ports of the Flutter downloads_ui_test.dart (the offline-banner case is in OnlineStatusTest). */
@RunWith(AndroidJUnit4::class)
class DownloadsUiTest {
    @get:Rule val rule = createComposeRule()
    private val hazza = parseReciters(loadFixtureJson("reciters_231_eng.json")).single()
    private val m = hazza.moshaf.first()

    private fun TestContainer.setStatus(surah: Int, s: DownloadStatus, received: Long = 0, total: Long? = null) = runBlocking {
        val url = m.audioUrl(surah)!!
        val row = DownloadEntity(kind = "surah", moshafId = m.id, surah = surah, url = url, path = "/x/$surah.mp3", status = s, receivedBytes = received, totalBytes = total)
        val existing = db.downloadsDao().byUrl(url)
        if (existing == null) db.downloadsDao().insert(row) else db.downloadsDao().update(row.copy(id = existing.id))
    }

    private fun openRecording(c: TestContainer) {
        rule.launchApp(c)
        rule.onNodeWithText("Listen").performClick()
        rule.await { rule.onAllNodesWithText("Recently added").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("hazza")
        rule.waitForIdle()
        rule.onNode(hasTestTag("reciter-231")).performClick()
        rule.await { rule.onAllNodesWithText(m.name).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(m.name).performClick()
        rule.await { rule.onAllNodesWithContentDescription("Download all").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `surah rows show their download state`() {
        val c = TestContainer()
        val s = m.surahs
        c.setStatus(s[0], DownloadStatus.QUEUED)
        c.setStatus(s[1], DownloadStatus.RUNNING, received = 50, total = 100)
        c.setStatus(s[2], DownloadStatus.DONE)
        c.setStatus(s[3], DownloadStatus.FAILED)
        openRecording(c)
        rule.await { rule.onAllNodesWithContentDescription("Waiting to download").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithContentDescription("Downloading").assertExists()
        rule.onNodeWithContentDescription("Downloaded").assertExists()
        rule.onNodeWithContentDescription("Retry download").assertExists()
        assertTrue(rule.onAllNodesWithContentDescription("Download").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `download all enqueues every surah of the recording`() {
        val c = TestContainer()
        openRecording(c)
        rule.onNodeWithContentDescription("Download all").performClick()
        rule.await { runBlocking { c.db.downloadsDao().all().size } == m.surahTotal }
    }

    @Test
    fun `storage shows size per recording`() {
        val c = TestContainer()
        c.setStatus(m.surahs[0], DownloadStatus.DONE, total = 3L * 1024 * 1024)
        rule.launchApp(c)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Downloads").performClick()
        rule.await { rule.onAllNodesWithText("3.0 MB", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the page pack shows progress`() {
        val c = TestContainer()
        rule.launchApp(c)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Downloads").performClick()
        rule.await { rule.onAllNodesWithText("Download all pages (about 80 MB)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Download all pages (about 80 MB)").performClick()
        rule.await { rule.onAllNodesWithText(" / 604", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    // Review I6: a running pack (it may be on mobile data) can be stopped.
    @Test
    fun `a running page pack can be cancelled`() {
        val c = TestContainer()
        c.pageFetcher.hang = true
        rule.launchApp(c)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Downloads").performClick()
        rule.await { rule.onAllNodesWithText("Download all pages (about 80 MB)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Download all pages (about 80 MB)").performClick()
        rule.await { rule.onAllNodesWithText(" / 604", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Cancel").performClick()
        rule.await { rule.onAllNodesWithText("Download all pages (about 80 MB)").fetchSemanticsNodes().isNotEmpty() }
    }
}

/** The page pack's failure handling (Review Focus 3) and cancellation. */
@RunWith(AndroidJUnit4::class)
class PagePackTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `disk full on one page counts it failed and continues`() = runBlocking {
        val fetcher = FakePageFetcher().apply {
            failPages = setOf(7)
            error = { IOException("ENOSPC (No space left on device)") }
        }
        val store = PageImageStore(tmp.root, fetcher, RawDeflateMaskCodec, rasterize = solidRasterize)
        val last = store.downloadAll(120).toList().last()
        assertEquals(PackProgress(done = 603, total = 604, failed = 1), last)
        assertTrue(File(tmp.root, "mushaf").walkTopDown().none { it.name.endsWith(".part") })
        assertFalse(store.isCached(7))
        assertTrue(store.isCached(6) && store.isCached(8))
    }

    @Test
    fun `cancel stops and keeps finished pages`() = runBlocking {
        val store = PageImageStore(tmp.root, FakePageFetcher(), RawDeflateMaskCodec, rasterize = solidRasterize)
        // Stop the pack (its collector goes away) after five pages.
        store.downloadAll(120, concurrency = 1).takeWhile { it.done < 5 }.collect {}
        val kept = (1..604).count { store.isCached(it) }
        assertTrue("kept $kept", kept in 5..8)
        // Starting again continues where it stopped.
        assertEquals(kept, store.downloadAll(120).toList().first().done)
    }
}
