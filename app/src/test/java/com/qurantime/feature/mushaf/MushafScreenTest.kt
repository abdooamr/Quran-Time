package com.qurantime.feature.mushaf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.qurantime.feature.mushaf.ui.PageInk
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.theme.AppThemeMode
import com.qurantime.support.await
import com.qurantime.support.awaitTag
import com.qurantime.support.fixtureHandler
import com.qurantime.support.launchApp
import com.qurantime.support.TestContainer
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter mushaf_screen_test.dart and offline_recovery_test.dart (header cases). */
@RunWith(AndroidJUnit4::class)

class MushafScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `opens at the last page`() {
        rule.launchApp(lastPage = 50)
        rule.awaitTag("mushaf-page-50")
    }

    private fun nextPageIsToTheLeft(locale: String) {
        val c = rule.launchApp(locale = locale)
        rule.awaitTag("mushaf-page-1")
        rule.onNodeWithTag("mushaf-pager").performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 2 }
        rule.awaitTag("mushaf-page-2")
    }

    @Test
    fun `next page is to the left (ar)`() = nextPageIsToTheLeft("ar")

    @Test
    fun `next page is to the left (en)`() = nextPageIsToTheLeft("en")

    @Test
    fun `header shows surah and juz`() {
        rule.launchApp(lastPage = 2)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Al-Baqarah").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Juz 1").assertExists()
        rule.onNodeWithContentDescription("Page 2").assertExists()
    }

    @Test
    fun `arabic header uses eastern arabic numerals`() {
        rule.launchApp(locale = "ar", lastPage = 604)
        rule.onNodeWithText("٦٠٤", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("٣٠", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `offline page shows retry`() {
        val c = TestContainer().also { it.pageFetcher.failNext = true }
        rule.launchApp(c)
        rule.awaitTag("mushaf-page-offline-1")
        val onPage1 = hasAnyAncestor(hasTestTag("mushaf-page-offline-1"))
        rule.onNode(hasText("This page isn't downloaded yet. Connect to the internet to load it.") and onPage1).assertExists()
        c.pageFetcher.failNext = false
        rule.onNode(hasText("Retry") and onPage1).performClick()
        rule.awaitTag("mushaf-page-art-1")
    }

    @Test
    fun `dark theme recolours the page ink`() {
        rule.launchApp(theme = AppThemeMode.DARK, lastPage = 3)
        rule.awaitTag("mushaf-page-art-3")
        rule.onNodeWithTag("mushaf-page-art-3").assert(SemanticsMatcher.expectValue(PageInk, Color(0xFFE6E1D6)))
    }

    @Test
    fun `the header offers Retry after an offline first launch`() {
        val c = TestContainer()
        c.fakeOnline.set(false)
        c.fixtureApi.handler = { _, _ -> throw com.qurantime.core.api.ApiException(null, "offline") }
        rule.launchApp(c, lastPage = 3)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() || headerRetry() }
        assertTrue(rule.onAllNodesWithText("Al-Baqarah").fetchSemanticsNodes().isEmpty())
        c.fixtureApi.handler = ::fixtureHandler
        rule.onNodeWithContentDescription("Retry").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Al-Baqarah").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the header recovers by itself when the connection returns`() {
        val c = TestContainer()
        c.fakeOnline.set(false)
        c.fixtureApi.handler = { _, _ -> throw com.qurantime.core.api.ApiException(null, "offline") }
        rule.launchApp(c, lastPage = 3)
        rule.waitUntil(5_000) { headerRetry() }
        c.fixtureApi.handler = ::fixtureHandler
        c.fakeOnline.set(true)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Al-Baqarah").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun headerRetry() =
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Retry")).fetchSemanticsNodes().isNotEmpty()

    // Review I3 / Focus 5: pages on screen give their bitmaps back under
    // memory pressure and are drawn again from disk.
    @Test
    fun `memory pressure releases shown pages and redraws them from disk`() {
        val c = rule.launchApp(lastPage = 50)
        rule.awaitTag("mushaf-page-art-50")
        rule.await { c.pageBitmaps.get(50, com.qurantime.feature.mushaf.image.renderWidthBucket(c.mushaf.pageWidthPx)) != null || c.pageBitmaps.anyFor(50) != null }
        val before = com.qurantime.support.LegacyFriendlyCodec.decodes.get()
        c.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertNull(c.pageBitmaps.anyFor(50))
        rule.await { com.qurantime.support.LegacyFriendlyCodec.decodes.get() > before }
        rule.awaitTag("mushaf-page-art-50")
    }

    // Review (Focus 1): a page file damaged in place (right length, bad data)
    // is made again instead of leaving the page "offline" for good.
    @Test
    fun `a damaged page file is made again`() {
        val c = rule.launchApp(lastPage = 50)
        rule.awaitTag("mushaf-page-art-50")
        val file = c.root.walkTopDown().first { it.isFile && it.name == "050.qtr" }
        val bytes = file.readBytes()
        for (i in 12 until bytes.size) bytes[i] = 0x5A // keep the header and length, ruin the data
        file.writeBytes(bytes)
        c.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) // drop it and read it again
        rule.await {
            runCatching { file.inputStream().use { com.qurantime.feature.mushaf.image.RawDeflateMaskCodec.decodeMask(it) } }.isSuccess
        }
        rule.awaitTag("mushaf-page-art-50")
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag("mushaf-page-offline-50")).fetchSemanticsNodes().isEmpty())
    }

    // Release review: "With reduce-motion on, every one of these is an instant
    // cut" (DESIGN.md, Motion) - the header's juz and surah fades included.
    @Test
    fun `with animations off the header cuts to the new juz`() {
        val resolver = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val c = rule.launchApp(lastPage = 21) // the last page of juz 1
            rule.awaitTag("mushaf-page-21")
            rule.mainClock.autoAdvance = false
            rule.runOnUiThread { c.mushaf.goTo(22) } // juz 2 starts on page 22
            var frames = 0
            while (rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Juz 2")).fetchSemanticsNodes().isEmpty() && frames < 200) {
                rule.mainClock.advanceTimeByFrame()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                frames++
            }
            // The frame the new juz appears, the old one is already gone.
            assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Juz 1")).fetchSemanticsNodes().isEmpty())
        } finally {
            rule.mainClock.autoAdvance = true
            android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }
}
