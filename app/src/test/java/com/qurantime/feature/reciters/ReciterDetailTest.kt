package com.qurantime.feature.reciters

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.catalog.parseSuwar
import com.qurantime.feature.reciters.ui.tracksFor
import com.qurantime.support.TestContainer
import com.qurantime.support.await
import com.qurantime.support.launchApp
import com.qurantime.support.loadFixtureJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter reciter_detail_test.dart. */
@RunWith(AndroidJUnit4::class)
class ReciterDetailTest {
    @get:Rule val rule = createComposeRule()
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))
    private val hazza = parseReciters(loadFixtureJson("reciters_231_eng.json")).single()
    private val m = hazza.moshaf.first()

    @Test
    fun `tracksFor lists only the surahs the recording has, in order`() {
        val tracks = tracksFor(hazza, m, suwar)
        assertEquals(m.surahTotal, tracks.size)
        assertEquals(m.surahs, tracks.map { it.surah })
        for (t in tracks) {
            assertTrue(t.url.endsWith("/" + t.surah.toString().padStart(3, '0') + ".mp3"))
            assertEquals(suwar.first { it.id == t.surah }.name, t.surahName)
            assertEquals("Hazza Al-Balushi", t.reciterName)
        }
    }

    private fun openHazza(): TestContainer {
        val c = rule.launchApp()
        rule.onNodeWithText("Listen").performClick()
        rule.await { rule.onAllNodesWithText("Recently added").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("hazza")
        rule.waitForIdle()
        rule.onNode(hasTestTag("reciter-231")).performClick()
        rule.await { rule.onAllNodesWithText(m.name).fetchSemanticsNodes().isNotEmpty() }
        return c
    }

    @Test
    fun `reciter screen shows one card per recording`() {
        openHazza()
        assertTrue(rule.onAllNodesWithText("Hazza Al-Balushi").fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, rule.onAllNodesWithText(m.name).fetchSemanticsNodes().size)
    }

    @Test
    fun `tapping a surah plays the recording from it`() {
        val c = openHazza()
        rule.onNodeWithText(m.name).performClick()
        val third = suwar.first { it.id == m.surahs[2] }.name
        rule.await { rule.onAllNodesWithText(third).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(third).onFirst().performClick()
        rule.await { (c.playerController.state.value.item as? SurahTrack)?.surah == m.surahs[2] }
        assertEquals(m.surahTotal, c.queue.items.size)
        c.playerController.stop()
    }

    @Test
    fun `recently added is newest first`() {
        rule.launchApp()
        rule.onNodeWithText("Listen").performClick()
        rule.await { rule.onAllNodesWithText("Recently added").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Recently added").performClick()
        val reads = parseRecentReads(loadFixtureJson("recent_reads_eng.json")).sortedByDescending { it.recentDate!! }
        rule.await { rule.onAllNodesWithText(reads[0].name).fetchSemanticsNodes().isNotEmpty() }
        val first = rule.onAllNodesWithText(reads[0].name).onFirst().fetchSemanticsNode().boundsInRoot.top
        val second = rule.onAllNodesWithText(reads[1].name).onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue("$first < $second", first < second)
    }
}
