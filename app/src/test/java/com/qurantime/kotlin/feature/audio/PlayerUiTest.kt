package com.qurantime.kotlin.feature.audio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.support.TestContainer
import com.qurantime.kotlin.support.launchApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter player_ui_test.dart. */
@RunWith(AndroidJUnit4::class)
class PlayerUiTest {
    @get:Rule val rule = createComposeRule()

    private val kahf = SurahTrack(5, "Ahmad Al-Ajmy", 5, "Murattal", "https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/", 18, "Al-Kahf")

    private fun TestContainer.play(vararg items: PlayableItem) {
        runBlocking { playerController.playItems(items.toList()) }
        rule.waitForIdle()
    }

    private fun openFull(locale: String = "en"): TestContainer {
        val c = rule.launchApp(locale = locale)
        c.play(kahf, kahf)
        rule.onNodeWithTag("mini-player").performClick()
        rule.waitForIdle()
        return c
    }

    @Test
    fun `mini player is hidden when nothing is loaded`() {
        rule.launchApp()
        assertTrue(rule.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `mini player shows the surah and reciter`() {
        val c = rule.launchApp()
        c.play(kahf)
        rule.onNodeWithTag("mini-player").assertExists()
        rule.onNodeWithText("Al-Kahf").assertExists()
        rule.onNodeWithText("Ahmad Al-Ajmy").assertExists()
        rule.onNode(hasContentDescription("Pause") and hasAnyAncestor(hasTestTag("mini-player"))).assertExists()
    }

    @Test
    fun `tapping the mini player opens the full player`() {
        openFull()
        rule.onNodeWithTag("full-player").assertExists()
        rule.onNodeWithTag("player-seek").assertExists()
    }

    @Test
    fun `live radio has no seek slider`() {
        val c = rule.launchApp()
        c.play(RadioStream(id = 1, name = "Radio Alzain", url = "https://r"))
        rule.onNodeWithTag("mini-player").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("player-seek").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Live").assertExists()
    }

    @Test
    fun `speed menu sets 1_5x`() {
        val c = openFull()
        rule.onNodeWithContentDescription("Playback speed").performClick()
        rule.onAllNodesWithText("1.5×").onLast().performClick()
        rule.waitForIdle()
        assertEquals(1.5f, c.playerController.state.value.speed)
    }

    @Test
    fun `sleep 30 min starts the timer`() {
        val c = openFull()
        rule.onNodeWithContentDescription("Sleep timer").performClick()
        rule.onNodeWithText("30 min").performClick()
        rule.waitForIdle()
        val left = c.playerController.sleep.remainingMs()!!
        assertTrue("$left", left in 29 * 60_000L..30 * 60_000L)
    }

    @Test
    fun `sleep at end of surah`() {
        val c = openFull()
        rule.onNodeWithContentDescription("Sleep timer").performClick()
        rule.onNodeWithText("End of surah").performClick()
        rule.waitForIdle()
        assertTrue(c.playerController.sleep.state.value.endOfTrack)
    }

    @Test
    fun `transport controls stay left-to-right in Arabic (Material media rule)`() {
        openFull(locale = "ar")
        val prev = rule.onNodeWithContentDescription("السابق").fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithContentDescription("التالي").fetchSemanticsNode().boundsInRoot
        assertTrue("$prev vs $next", prev.center.x < next.center.x)
    }

    @Test
    fun `a recording that fails to load says so in the mini player`() {
        val c = rule.launchApp()
        c.backend.failSetSources = true
        c.play(kahf)
        rule.onNodeWithText("couldn't be played", substring = true).assertExists()
    }

    // Release review: every target is at least 48 dp (DESIGN.md, Layout).
    @Test
    fun `the mini player's play seal is a full 48dp target`() {
        val c = rule.launchApp()
        c.play(kahf)
        rule.onNodeWithTag("mini-play").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
