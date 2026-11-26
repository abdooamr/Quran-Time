package com.qurantime.kotlin.feature.explore

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.feature.audio.AudioClip
import com.qurantime.kotlin.support.FakeVideoPlayerFactory
import com.qurantime.kotlin.support.TestContainer
import com.qurantime.kotlin.support.await
import com.qurantime.kotlin.support.fixtureHandler
import com.qurantime.kotlin.support.launchApp
import com.qurantime.kotlin.support.loadFixtureJson
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter explore_test.dart. */
@RunWith(AndroidJUnit4::class)
class ExploreTest {
    @get:Rule val rule = createComposeRule()
    private val groups = parseVideos(loadFixtureJson("videos_ar.json"))
    private val videos = groups.flatMap { it.videos }

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Opens Explore, then [entry] (a section title) if given. */
    private fun open(entry: String? = null, c: TestContainer = TestContainer()): TestContainer {
        rule.launchApp(c)
        rule.onNodeWithText("Explore").performClick()
        rule.await { exists("Videos") }
        if (entry != null) rule.onNodeWithText(entry).performClick()
        return c
    }

    /** Opens a deep route the way a link (or the Mushaf's ayah actions) would. */
    private fun go(c: TestContainer, route: Any): TestContainer {
        rule.launchApp(c, onNav = { navRef = it })
        rule.onNodeWithText("Explore").performClick()
        rule.await { exists("Videos") }
        rule.runOnUiThread { navRef!!.navigate(route) }
        return c
    }

    private var navRef: androidx.navigation.NavController? = null

    @Test
    fun `Explore shows its three sections`() {
        open()
        rule.onNodeWithText("Videos").assertExists()
        rule.onNodeWithText("Tafsir").assertExists()
        rule.onNodeWithText("Reflections").assertExists()
    }

    @Test
    fun `video type filter shows only that type`() {
        open("Videos")
        val other = videos.first { it.typeId != 2 }
        val first2 = videos.first { it.typeId == 2 }
        val types = parseVideoTypes(loadFixtureJson("video_types_ar.json"))
        rule.await { exists(types.first { it.id == 2 }.name) }
        rule.onNodeWithText(types.first { it.id == 2 }.name).performClick()
        rule.await { rule.onAllNodes(hasTestTag("video-${other.id}")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasScrollAction() and hasTestTag("videos-grid")).performScrollToNode(hasTestTag("video-${first2.id}"))
        rule.onNode(hasTestTag("video-${first2.id}")).assertExists()
    }

    @Test
    fun `tapping a video plays it full screen`() {
        val c = open("Videos")
        rule.await { rule.onAllNodes(hasTestTag("video-${videos.first().id}")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag("video-${videos.first().id}")).performClick()
        val v = c.videoPlayers as FakeVideoPlayerFactory
        rule.await { v.created.isNotEmpty() }
        assertEquals(videos.first().url to false, v.created.single())
    }

    @Test
    fun `tafsir - collection, surah, clip plays`() {
        val c = open("Tafsir")
        val name = parseTafasir(loadFixtureJson("tafasir_ar.json")).single().name
        rule.await { exists(name) }
        rule.onNodeWithText(name).performClick()
        rule.await { exists("Al-Fatihah") }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("An-Nas"))
        rule.onNodeWithText("An-Nas").performClick()
        val clip = parseTafsirClips(loadFixtureJson("tafsir_1_114_ar.json")).single()
        rule.await { exists(clip.name) }
        rule.onNodeWithText(clip.name).performClick()
        rule.await { c.playerController.state.value.item != null }
        assertTrue((c.playerController.state.value.item as AudioClip).url.endsWith("/114.mp3"))
        c.playerController.stop()
        rule.waitForIdle()
    }

    @Test
    fun `the tafsir deep link opens a surah directly`() {
        val c = TestContainer()
        go(c, com.qurantime.kotlin.core.nav.TafsirSurahRoute(1, 114))
        val clip = parseTafsirClips(loadFixtureJson("tafsir_1_114_ar.json")).single()
        rule.await { exists(clip.name) }
    }

    @Test
    fun `a video reflection opens in the video player`() {
        val c = TestContainer()
        go(c, com.qurantime.kotlin.core.nav.TadaborRoute(1))
        val clip = parseTadabor(loadFixtureJson("tadabor_1_ar.json")).first()
        rule.await { exists(clip.title) }
        rule.await { rule.onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("Play").onFirst().performScrollTo().performClick()
        val v = c.videoPlayers as FakeVideoPlayerFactory
        rule.await { v.created.isNotEmpty() }
        assertEquals(clip.videoUrl, v.created.single().first)
    }

    @Test
    fun `a surah without reflections says so`() {
        val c = TestContainer()
        c.fixtureApi.handler = { path, q -> if (path == "tadabor") buildJsonObject { put("tadabor", buildJsonArray {}) } else fixtureHandler(path, q) }
        go(c, com.qurantime.kotlin.core.nav.TadaborRoute(5))
        rule.await { exists("No reflections for this surah yet.") }
    }

    @Test
    fun `reflections pick a surah first`() {
        open("Reflections")
        rule.await { exists("Al-Fatihah") }
        rule.onNodeWithText("Al-Fatihah").performClick()
        val clip = parseTadabor(loadFixtureJson("tadabor_1_ar.json")).first()
        rule.await { exists(clip.title) }
    }
}
