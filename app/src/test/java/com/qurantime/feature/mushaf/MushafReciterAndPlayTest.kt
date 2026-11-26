package com.qurantime.feature.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.api.ApiException
import com.qurantime.core.l10n.LocalizedContent
import com.qurantime.core.theme.AppThemeMode
import com.qurantime.core.theme.MushafPalette
import com.qurantime.core.theme.QuranTimeTheme
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.catalog.parseSuwar
import com.qurantime.feature.mushaf.ui.JumpSheetContent
import com.qurantime.feature.timing.parseTimedReads
import com.qurantime.support.TestContainer
import com.qurantime.support.await
import com.qurantime.support.fixtureHandler
import com.qurantime.support.launchApp
import com.qurantime.support.loadFixtureJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter mushaf_reciter_and_play_test.dart and offline_recovery_test.dart cases 3–4. */
@RunWith(AndroidJUnit4::class)
class MushafReciterAndPlayTest {
    @get:Rule val rule = createComposeRule()
    private val reads = parseTimedReads(loadFixtureJson("ayat_timing_reads.json"))

    private fun awaitText(text: String, substring: Boolean = false) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun openMushafReciter() {
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Mushaf reciter").performClick()
        awaitText(reads.first().riwaya)
    }

    @Test
    fun `the Mushaf reciter screen lists timed reads under riwaya headers`() {
        rule.launchApp()
        openMushafReciter()
        val r5 = reads.first { it.id == 5 }
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(r5.name))
        rule.onNodeWithText(r5.name).assertExists()
    }

    @Test
    fun `choosing a read saves it and goes back`() {
        val c = TestContainer()
        runBlocking { c.settings.setMushafReadId(reads.first().id) }
        rule.launchApp(c)
        openMushafReciter()
        val r5 = reads.first { it.id == 5 }
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(r5.name))
        rule.onNodeWithText(r5.name).performClick()
        rule.await { c.settings.state.value.mushafReadId == 5 }
        rule.await { rule.onAllNodesWithText(r5.name).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `play on page 3 starts at its first ayah (Al-Baqarah 6)`() {
        val c = rule.launchApp(lastPage = 3)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Play this page").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Play this page").performClick()
        rule.await { (c.playerController.state.value.item as? SurahTrack)?.surah == 2 }
        assertEquals(54_884L, c.backend.positionMs)
    }

    @Test
    fun `play with a reciter that lacks the surah shows a message`() {
        val c = TestContainer()
        runBlocking { c.settings.setMushafReadId(99999) }
        rule.launchApp(c, lastPage = 3)
        rule.onNodeWithContentDescription("Play this page").performClick()
        awaitText("The Mushaf reciter has no recording", substring = true)
        assertFalse("play" in c.backend.calls)
    }

    @Test
    fun `tapping an ayah offline says so instead of failing silently`() {
        val c = TestContainer()
        c.fixtureApi.handler = { _, _ -> throw ApiException(null, "offline") }
        c.fakeOnline.set(false)
        rule.launchApp(c, lastPage = 3)
        rule.tapViewBox(3, Offset(172f, 275f))
        awaitText("Couldn't load this. Check your connection and try again.")
    }

    @Test
    fun `Play page offline ends in a message, not silence`() {
        val c = TestContainer()
        // The surah list is cached from an earlier visit; the audio can't load.
        c.fixtureApi.handler = { path, q -> if (path == "suwar") fixtureHandler(path, q) else throw ApiException(null, "offline") }
        c.backend.failSetSources = true
        rule.launchApp(c, lastPage = 3)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Play this page").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Play this page").performClick()
        awaitText("couldn't be played", substring = true)
        assertFalse("play" in c.backend.calls)
    }
}

/** Ports of the Flutter review_fixes_test.dart. */
@RunWith(AndroidJUnit4::class)
class ReviewFixesTest {
    @get:Rule val rule = createComposeRule()
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))

    private fun openSheet(currentPage: Int) {
        rule.setContent {
            LocalizedContent("en") { QuranTimeTheme(AppThemeMode.LIGHT) { JumpSheetContent(suwar, currentPage, onJump = {}) } }
        }
        rule.waitForIdle()
    }

    @Test
    fun `jump sheet opens at the current surah and marks it`() {
        openSheet(50)
        rule.onNode(hasText("Aal Imran") and hasTestTag("jump-selected")).assertExists()
        assertTrue("the list is scrolled to the current surah", rule.onAllNodesWithText("Al-Fatihah").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `jump sheet marks the current juz`() {
        openSheet(50)
        rule.onNodeWithText("Juz").performClick()
        rule.onNode(hasText("Juz 3") and hasTestTag("jump-selected")).assertExists()
    }

    @Test
    fun `deleting a bookmark can be undone`() {
        val c = TestContainer()
        runBlocking {
            c.bookmarks.load()
            c.bookmarks.togglePage(9)
        }
        rule.launchApp(c)
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Bookmarks").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Delete").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.waitUntil(5_000) { !c.bookmarks.hasPage(9) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5_000) { c.bookmarks.hasPage(9) }
    }

    @Test
    fun `a slow page shows its frame and, after a moment, a spinner`() {
        val c = TestContainer().also { it.pageFetcher.hang = true }
        rule.mainClock.autoAdvance = false
        rule.launchApp(c)
        rule.mainClock.advanceTimeBy(100)
        assertTrue(rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("Page 1").assertExists()
        rule.mainClock.advanceTimeBy(400)
        assertTrue(rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `More credits MP3Quran_net`() {
        rule.launchApp()
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("MP3Quran.net", substring = true).assertExists()
    }

    @Test
    fun `light theme uses the ivory book paper, sepia is deeper`() {
        assertEquals(Color(0xFFF4EEDD), MushafPalette.Light.background)
        assertEquals(Color(0xFFEBDDC2), MushafPalette.Sepia.background)
        assertEquals(Color(0xFF4A3B28), MushafPalette.Sepia.ink)
    }
}
