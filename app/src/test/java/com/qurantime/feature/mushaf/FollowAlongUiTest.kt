package com.qurantime.feature.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.mushaf.ui.HighlightedAyah
import com.qurantime.feature.reciters.parseReciters
import com.qurantime.feature.timing.TimedReadMatcher
import com.qurantime.feature.timing.parseAyahTimings
import com.qurantime.feature.timing.parseTimedReads
import com.qurantime.support.await
import com.qurantime.support.TestContainer
import com.qurantime.support.awaitTag
import com.qurantime.support.launchApp
import com.qurantime.support.loadFixtureJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val timings = parseAyahTimings(loadFixtureJson("ayat_timing_2_5.json"))
private fun ayah(n: Int) = timings.first { it.ayah == n }
private val fixtureMatcher = TimedReadMatcher(parseTimedReads(loadFixtureJson("ayat_timing_reads.json")), parseReciters(loadFixtureJson("reciters_eng.json")))

fun baqarah(): SurahTrack {
    val hit = fixtureMatcher.moshafForRead(5)!!
    return SurahTrack(hit.reciter.id, hit.reciter.name, hit.moshaf.id, hit.moshaf.name, hit.moshaf.server, 2, "Al-Baqarah")
}

/** Ports of the Flutter follow_along_ui_test.dart. */
@RunWith(AndroidJUnit4::class)
class FollowAlongUiTest {
    @get:Rule val rule = createComposeRule()

    private fun start(page: Int, autoFollow: Boolean = true): TestContainer {
        val c = TestContainer()
        runBlocking { c.settings.setAutoFollow(autoFollow) }
        rule.launchApp(c, lastPage = page)
        runBlocking { c.playerController.playItems(listOf(baqarah())) }
        rule.await { c.follow.state.value.active }
        return c
    }

    private fun TestContainer.recite(n: Int) {
        backend.emitPosition(ayah(n).startMs + 10)
        rule.waitForIdle()
    }

    @Test
    fun `paints the recited ayah on its page`() {
        val c = start(page = 3)
        rule.awaitTag("mushaf-page-art-3")
        c.recite(7)
        rule.awaitTag("mushaf-highlight-3")
        rule.onNodeWithTag("mushaf-highlight-3").assert(SemanticsMatcher.expectValue(HighlightedAyah, 7))
    }

    @Test
    fun `the full player shows the page being recited, highlighted`() {
        val c = start(page = 3)
        c.recite(7)
        rule.onNodeWithTag("mini-player").performClick()
        val inPlayer = { tag: String -> androidx.compose.ui.test.hasTestTag(tag) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("full-player")) }
        rule.await { rule.onAllNodes(inPlayer("mushaf-highlight-3"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(inPlayer("mushaf-highlight-3"), useUnmergedTree = true).assert(SemanticsMatcher.expectValue(HighlightedAyah, 7))
        c.playerController.stop()
        rule.waitForIdle()
    }

    @Test
    fun `turns the page when the recitation moves on`() {
        val c = start(page = 2)
        c.recite(1)
        assertEquals(2, c.mushaf.currentPage.value)
        c.recite(6)
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 3 }
        rule.awaitTag("mushaf-page-3")
    }

    @Test
    fun `a manual swipe pauses following until Back to recitation`() {
        val c = start(page = 3)
        c.recite(6)
        rule.onNodeWithTag("mushaf-pager").performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 4 }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Back to recitation").fetchSemanticsNodes().isNotEmpty() }
        c.recite(7)
        assertEquals("following is paused", 4, c.mushaf.currentPage.value)
        rule.onNodeWithText("Back to recitation").performClick()
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 3 }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Back to recitation").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `with auto-follow off the page stays put`() {
        val c = start(page = 2, autoFollow = false)
        c.recite(6)
        rule.waitForIdle()
        assertEquals(2, c.mushaf.currentPage.value)
    }
}

/** Taps the screen point that maps to viewBox point [vb] on [page]. */
fun ComposeContentTestRule.tapViewBox(page: Int, vb: Offset) {
    awaitTag("mushaf-page-art-$page")
    onNodeWithTag("mushaf-page-art-$page").performTouchInput {
        click(Offset(vb.x / 345f * width, vb.y / 550f * height))
    }
    waitForIdle()
}

/** Ports of the Flutter ayah_actions_test.dart. */
@RunWith(AndroidJUnit4::class)
class AyahActionsTest {
    @get:Rule val rule = createComposeRule()

    private fun open(readId: Int? = null): TestContainer {
        val c = TestContainer()
        readId?.let { runBlocking { c.settings.setMushafReadId(it) } }
        return rule.launchApp(c, lastPage = 3)
    }

    private fun awaitText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `tapping inside an ayah opens its actions`() {
        open()
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Al-Baqarah: 7")
        rule.onNodeWithText("Play from here").assertExists()
    }

    @Test
    fun `the tapped ayah is marked on the page while its actions are open`() {
        open()
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Al-Baqarah: 7")
        rule.onNode(androidx.compose.ui.test.hasTestTag("mushaf-selection-3"), useUnmergedTree = true).assertExists()
        // Choosing an action closes the sheet; the mark goes with it.
        rule.onNodeWithText("Bookmark this ayah").performClick()
        rule.await {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("mushaf-selection-3"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `play from here plays the Mushaf reciter from that ayah`() {
        val c = open()
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Play from here")
        rule.onNodeWithText("Play from here").performClick()
        rule.waitUntil(5_000) { (c.playerController.state.value.item as? SurahTrack)?.surah == 2 }
        assertEquals(65_566L, c.backend.positionMs)
    }

    @Test
    fun `bookmark this ayah`() {
        val c = open()
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Bookmark this ayah")
        rule.onNodeWithText("Bookmark this ayah").performClick()
        rule.waitUntil(5_000) { c.bookmarks.items.value.isNotEmpty() }
        val b = c.bookmarks.items.value.single()
        assertEquals(Triple(2, 7, 3), Triple(b.surah, b.ayah, b.page))
    }

    @Test
    fun `a Mushaf reciter without the surah gets a message, not playback`() {
        val c = open(readId = 99999)
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Play from here")
        rule.onNodeWithText("Play from here").performClick()
        awaitText("The Mushaf reciter has no recording of this surah. Choose another reciter in Settings.")
        assertTrue("play" !in c.backend.calls)
    }

    @Test
    fun `tapping outside any ayah does nothing`() {
        open()
        rule.tapViewBox(3, Offset(170f, 4f))
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Play from here").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `repeat this ayah starts a repeat at its start`() {
        val c = open()
        rule.tapViewBox(3, Offset(300f, 100f))
        awaitText("Repeat this ayah")
        rule.onNodeWithText("Repeat this ayah").performClick()
        awaitText("Repeat ayahs")
        rule.onNodeWithText("Start repeating").performClick()
        rule.await { c.playerController.state.value.repeating }
        assertEquals(65_566L, c.backend.positionMs)
        c.playerController.cancelRepeat()
    }
}
