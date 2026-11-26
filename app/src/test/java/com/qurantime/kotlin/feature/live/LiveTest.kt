package com.qurantime.kotlin.feature.live

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.feature.audio.RadioStream
import com.qurantime.kotlin.support.FakeVideoPlayerFactory
import com.qurantime.kotlin.support.TestContainer
import com.qurantime.kotlin.support.await
import com.qurantime.kotlin.support.launchApp
import com.qurantime.kotlin.support.loadFixtureJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun tile(id: Int) = hasTestTag("radio-$id")

/** Ports of the Flutter radio_list_test.dart. */
@RunWith(AndroidJUnit4::class)
class RadioListTest {
    @get:Rule val rule = createComposeRule()
    private val radios = parseRadios(loadFixtureJson("radios_eng.json"))

    private fun openLive(): TestContainer {
        val c = TestContainer()
        rule.launchApp(c)
        rule.onNodeWithText("Live").performClick()
        rule.await { rule.onAllNodes(tile(radios[0].id)).fetchSemanticsNodes().isNotEmpty() }
        return c
    }

    @Test
    fun `search narrows the stations`() {
        openLive()
        val target = radios[5]
        rule.onNode(hasSetTextAction()).performTextInput(target.name.split(' ').last())
        rule.await { rule.onAllNodes(tile(radios[0].id)).fetchSemanticsNodes().isEmpty() }
        rule.onNode(tile(target.id)).assertExists()
    }

    @Test
    fun `tapping a station plays it live`() {
        val c = openLive()
        rule.onNode(tile(radios[0].id)).performClick()
        rule.await { c.playerController.state.value.item == RadioStream(radios[0].id, radios[0].name, radios[0].url) }
        rule.await {
            rule.onAllNodes(hasContentDescription("Now playing") and hasAnyAncestor(tile(radios[0].id)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
        c.playerController.stop()
        rule.waitForIdle()
    }

    @Test
    fun `favourite stations come first`() {
        val c = openLive()
        val last = radios.last()
        runBlocking { c.db.favoritesDao().toggle("radio", last.id, 0) }
        rule.await { rule.onAllNodes(tile(last.id)).fetchSemanticsNodes().isNotEmpty() }
        val favTop = rule.onNode(tile(last.id)).fetchSemanticsNode().boundsInRoot.top
        val firstOther = rule.onNode(tile(radios.first().id)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("$favTop < $firstOther", favTop < firstOther)
    }
}

/** Ports of the Flutter live_tv_test.dart. */
@RunWith(AndroidJUnit4::class)
class LiveTvTest {
    @get:Rule val rule = createComposeRule()

    private fun openTv(failVideo: Boolean = false): TestContainer {
        val c = TestContainer()
        (c.videoPlayers as FakeVideoPlayerFactory).fail = failVideo
        rule.launchApp(c)
        rule.onNodeWithText("Live").performClick()
        rule.await { rule.onAllNodesWithText("Live TV").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Live TV").performClick()
        rule.await { rule.onAllNodesWithText("Quran channel").fetchSemanticsNodes().isNotEmpty() }
        return c
    }

    @Test
    fun `shows both channels`() {
        openTv()
        rule.onNodeWithText("Quran channel").assertExists()
        rule.onNodeWithText("Sunna channel").assertExists()
    }

    @Test
    fun `opening a channel stops audio and plays the stream full screen`() {
        val c = openTv()
        runBlocking { c.playerController.playItems(listOf(RadioStream(1, "R", "https://r"))) }
        rule.waitForIdle()
        rule.onNodeWithText("Quran channel").performClick()
        val video = c.videoPlayers as FakeVideoPlayerFactory
        rule.await { video.created.isNotEmpty() }
        assertTrue(c.backend.calls.contains("stop"))
        assertEquals(listOf("https://win.holol.com/live/quran/playlist.m3u8" to true), video.created)
        // Full screen: the tab bar is gone, and the live badge shows.
        rule.await { rule.onAllNodesWithText("Mushaf").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Live").assertExists()
    }

    @Test
    fun `a stream that fails to start shows an error with retry`() {
        val c = openTv(failVideo = true)
        rule.onNodeWithText("Quran channel").performClick()
        rule.await { rule.onAllNodesWithText("This video couldn't be played.").fetchSemanticsNodes().isNotEmpty() }
        val video = c.videoPlayers as FakeVideoPlayerFactory
        video.fail = false
        rule.onNodeWithText("Retry").performClick()
        rule.await { rule.onAllNodesWithText("This video couldn't be played.").fetchSemanticsNodes().isEmpty() }
        assertEquals(2, video.created.size)
    }
}
