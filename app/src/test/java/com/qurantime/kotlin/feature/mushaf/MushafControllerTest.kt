package com.qurantime.kotlin.feature.mushaf

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.feature.mushaf.ui.PageBitmapCache
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter mushaf_controller_test.dart. */
class MushafControllerTest {
    @Test
    fun `saves the last page 500ms after it is shown, once per burst`() = runTest {
        val saved = mutableListOf<Int>()
        val prefetched = mutableListOf<Int>()
        val c = MushafController(
            initialPage = 1,
            saveLastPage = { saved += it },
            prefetch = { page, _ -> prefetched += page },
            scope = backgroundScope,
        )
        c.onPageShown(48)
        c.onPageShown(49)
        c.onPageShown(50)
        assertEquals(50, c.currentPage.value)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(emptyList<Int>(), saved)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(50), saved)
        assertEquals(listOf(48, 49, 50), prefetched)
    }

    @Test
    fun `starts at the saved last page`() = runTest {
        val c = MushafController(initialPage = 77, saveLastPage = {}, prefetch = { _, _ -> }, scope = backgroundScope)
        assertEquals(77, c.currentPage.value)
    }

    @Test
    fun `goTo emits a jump request and clamps to 1 to 604`() = runTest {
        val c = MushafController(initialPage = 1, saveLastPage = {}, prefetch = { _, _ -> }, scope = backgroundScope)
        val jumps = mutableListOf<Int>()
        backgroundScope.launch { c.jumpRequests.toList(jumps) }
        runCurrent()
        c.goTo(293)
        c.goTo(900)
        runCurrent()
        assertEquals(listOf(293, 604), jumps)
    }
}

@RunWith(AndroidJUnit4::class)
class PageBitmapCacheTest {
    private fun bmp() = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)

    @Test
    fun `evicts beyond five`() {
        val cache = PageBitmapCache(maxEntries = 5)
        for (p in 1..6) cache.put(p, 1080, bmp())
        assertNull(cache.get(1, 1080))
        assertNotNull(cache.get(6, 1080))
        assertNotNull(cache.get(2, 1080))
    }

    @Test
    fun `trim memory clears`() {
        val cache = PageBitmapCache()
        cache.put(3, 1080, bmp())
        cache.clear()
        assertNull(cache.get(3, 1080))
    }
}
