package com.qurantime.feature.mushaf.image

import androidx.compose.ui.geometry.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.support.FakePageFetcher
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Ports of the Flutter mushaf_page_store_test.dart, plus the image-pipeline cases. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageImageStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fetcher = FakePageFetcher()
    private val rasterThreads: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** A tiny stand-in for the SVG renderer, so 604-page tests stay fast. */
    private val fakeRasterize: (InputStream, Size, Int) -> PageMask = { input, _, width ->
        rasterThreads += Thread.currentThread().name
        input.readBytes()
        PageMask(width, 2, ByteArray(width * 2) { 0x77 })
    }

    private fun store(rasterize: (InputStream, Size, Int) -> PageMask = fakeRasterize, svgLruSize: Int = 30) =
        PageImageStore(tmp.root, fetcher, RawDeflateMaskCodec, rasterize = rasterize, svgLruSize = svgLruSize)

    private val mushaf get() = File(tmp.root, "mushaf")

    private suspend fun expectUnavailable(page: Int, block: suspend () -> Unit) {
        try {
            block()
            fail("expected PageUnavailableException")
        } catch (e: PageUnavailableException) {
            assertEquals(page, e.page)
        }
    }

    // --- ported ---

    @Test
    fun `downloads once then serves from disk`() = runBlocking {
        val s = store()
        val a = s.mask(3, 1080)
        val b = s.mask(3, 1080)
        assertEquals(listOf("https://www.mp3quran.net/api/quran_pages_svg/003.svg"), fetcher.fetched)
        assertEquals(a.file.path, b.file.path)
        assertTrue(a.file.path.replace('\\', '/').endsWith("mushaf/w1080/003.qtr"))
        assertTrue(s.isCached(3))
        assertTrue("a new store sees the file on disk", store().isCached(3))
    }

    @Test
    fun `concurrent requests coalesce`() = runBlocking {
        val s = store()
        listOf(async { s.mask(3, 1080) }, async { s.mask(3, 1080) }).awaitAll()
        assertEquals(1, fetcher.fetched.size)
    }

    @Test
    fun `failed fetch leaves no file`() = runBlocking {
        val s = store()
        fetcher.failNext = true
        expectUnavailable(3) { s.mask(3, 1080) }
        assertFalse(s.isCached(3))
        val left = mushaf.walkTopDown().filter { it.isFile }.toList()
        assertTrue("left behind: $left", left.isEmpty())
        fetcher.failNext = false
        assertTrue("retry works after a failure", s.mask(3, 1080).file.exists())
    }

    @Test
    fun `prefetch bounds`() = runBlocking {
        val s = store()
        s.prefetchAround(1, 1080)
        assertEquals(setOf("002.svg", "003.svg"), fetcher.fetchedPages().toSet())
        fetcher.fetched.clear()
        s.prefetchAround(604, 1080)
        assertEquals(setOf("602.svg", "603.svg"), fetcher.fetchedPages().toSet())
    }

    @Test
    fun `prefetch skips cached pages and swallows errors`() = runBlocking {
        val s = store()
        s.mask(2, 1080)
        fetcher.fetched.clear()
        fetcher.failNext = true
        s.prefetchAround(1, 1080)
        assertEquals(listOf("003.svg"), fetcher.fetchedPages())
    }

    @Test
    fun `downloadAll skips cached and reports`() = runBlocking {
        val s = store()
        for (p in 1..10) s.mask(p, 1080)
        fetcher.fetched.clear()
        val events = s.downloadAll(1080).toList()
        assertEquals(594, fetcher.fetched.size)
        assertEquals(604, events.last().done)
        assertEquals(604, events.last().total)
        assertEquals(0, events.last().failed)
        assertTrue(s.cachedBytes() > 0)
    }

    @Test
    fun `clear removes cached pages`() = runBlocking {
        val s = store()
        s.mask(5, 1080)
        s.clear()
        assertFalse(s.isCached(5))
    }

    // --- image pipeline ---

    @Test
    fun `fetches, converts and keeps only the mask and a gz svg`() = runBlocking {
        val s = store(rasterize = SvgRasterizer::render)
        val r = s.mask(3, 400)
        assertTrue(r.exact)
        assertEquals(480, r.bucket)
        val files = mushaf.walkTopDown().filter { it.isFile }.map { it.relativeTo(mushaf).path.replace('\\', '/') }.toSet()
        assertEquals(setOf("w480/003.qtr", "svg/003.svg.gz"), files)
        val bmp = r.file.inputStream().use { RawDeflateMaskCodec.decode(it) }
        assertEquals(480, bmp.width)
        assertEquals(765, bmp.height) // 480 * 550 / 345
    }

    @Test
    fun `svg cache keeps the 30 most recent`() = runBlocking {
        val s = store()
        for (p in 3..33) s.mask(p, 1080)
        assertFalse(File(mushaf, "svg/003.svg.gz").exists())
        assertTrue(File(mushaf, "svg/033.svg.gz").exists())
        assertEquals(30, File(mushaf, "svg").listFiles()!!.count { it.name.endsWith(".svg.gz") })
        assertNotNull(s.svgFor(33))
    }

    @Test
    fun `corrupt mask is re-made`() = runBlocking {
        val s = store()
        val f = s.mask(3, 1080).file
        f.writeBytes(f.readBytes().copyOf(5))
        val again = s.mask(3, 1080)
        val bmp = again.file.inputStream().use { RawDeflateMaskCodec.decode(it) }
        assertEquals(1080, bmp.width)
    }

    @Test
    fun `other bucket returns exact=false and re-make deletes the old bucket file`() = runBlocking {
        val s = store()
        val old = s.mask(3, 1080).file
        val other = s.mask(3, 1200)
        assertFalse(other.exact)
        assertEquals(old.path, other.file.path)
        val remade = s.remake(3, 1200)
        assertTrue(remade.exact)
        assertEquals(1200, remade.bucket)
        assertFalse("old bucket file deleted", old.exists())
        assertTrue(s.mask(3, 1200).exact)
    }

    @Test
    fun `conversion never runs on the caller dispatcher`() = runBlocking {
        val cpu = Executors.newSingleThreadExecutor { Thread(it, "qt-cpu") }.asCoroutineDispatcher()
        val s = PageImageStore(tmp.root, fetcher, RawDeflateMaskCodec, rasterize = fakeRasterize, cpu = cpu)
        s.mask(3, 1080)
        assertEquals(1, rasterThreads.size)
        assertTrue(rasterThreads.single(), rasterThreads.single().startsWith("qt-cpu"))
        cpu.close()
    }
}
