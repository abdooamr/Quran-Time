package com.qurantime.feature.mushaf

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.l10n.LocalizedContent
import com.qurantime.core.theme.AppThemeMode
import com.qurantime.core.theme.QuranTimeTheme
import com.qurantime.feature.catalog.parseSuwar
import com.qurantime.feature.mushaf.ui.JumpSheetContent
import com.qurantime.feature.mushaf.ui.ZoomState
import com.qurantime.support.launchApp
import com.qurantime.support.loadFixtureJson
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter jump_sheet_test.dart, plus the cartouche case of bookmarks_flow_test.dart. */
@RunWith(AndroidJUnit4::class)
class JumpSheetTest {
    @get:Rule val rule = createComposeRule()
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))

    private fun openSheet(locale: String = "en"): MutableList<Int> {
        val results = mutableListOf<Int>()
        rule.setContent {
            LocalizedContent(locale) {
                QuranTimeTheme(AppThemeMode.LIGHT) { JumpSheetContent(suwar, currentPage = null, onJump = { results += it }) }
            }
        }
        rule.waitForIdle()
        return results
    }

    @Test
    fun `tapping a surah returns its start page`() {
        val results = openSheet()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Al-Kahf"))
        rule.onNodeWithText("Al-Kahf").performClick()
        assertEquals(listOf(293), results)
    }

    @Test
    fun `tapping juz 30 returns 582`() {
        val results = openSheet()
        rule.onNodeWithText("Juz").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Juz 30"))
        rule.onNodeWithText("Juz 30").performClick()
        assertEquals(listOf(582), results)
    }

    @Test
    fun `page 605 shows an error and stays open`() {
        val results = openSheet()
        rule.onNodeWithText("Page").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("605")
        rule.onNodeWithText("Go").performClick()
        rule.onNodeWithText("Enter a page from 1 to 604.").assertExists()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `page 300 returns 300`() {
        val results = openSheet()
        rule.onNodeWithText("Page").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("300")
        rule.onNodeWithText("Go").performClick()
        assertEquals(listOf(300), results)
    }

    @Test
    fun `eastern arabic digits are accepted`() {
        val results = openSheet(locale = "ar")
        rule.onNodeWithText("الصفحة").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("٣٠٠")
        rule.onNodeWithText("انتقال").performClick()
        assertEquals(listOf(300), results)
    }

    @Test
    fun `tapping the surah cartouche opens the jump sheet`() {
        val c = rule.launchApp(lastPage = 2)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Al-Baqarah").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Al-Baqarah").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Surahs").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Al-Kahf"))
        rule.onNodeWithText("Al-Kahf").performClick()
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 293 }
    }
}

@RunWith(AndroidJUnit4::class)
class ZoomStateTest {
    @Test
    fun `hi-res render appears after zoom settles and is dropped at 1x`() = runTest {
        var renders = 0
        val z = ZoomState(backgroundScope) {
            renders++
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        z.onTransform(zoomChange = 2f, pan = Offset.Zero)
        advanceTimeBy(149)
        runCurrent()
        assertNull(z.hiRes)
        advanceTimeBy(1)
        runCurrent()
        assertNotNull(z.hiRes)
        assertEquals(2f, z.scale)
        z.onTransform(zoomChange = 0.25f, pan = Offset.Zero)
        assertEquals(1f, z.scale)
        assertNull(z.hiRes)
        assertEquals(1, renders)
    }

    // Review I3 / Focus 5: memory pressure drops the zoom render.
    @Test
    fun `memory pressure drops the hi-res render`() = runTest {
        val z = ZoomState(backgroundScope) {
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        z.onTransform(zoomChange = 2f, pan = Offset.Zero)
        advanceTimeBy(200)
        runCurrent()
        assertNotNull(z.hiRes)
        z.dropHiRes()
        assertNull(z.hiRes)
        assertEquals(2f, z.scale)
    }

    @Test
    fun `offline without svg keeps scaled mask`() = runTest {
        val z = ZoomState(backgroundScope) { null }
        z.onTransform(zoomChange = 3f, pan = Offset.Zero)
        advanceTimeBy(500)
        runCurrent()
        assertNull(z.hiRes)
        assertEquals(3f, z.scale)
    }
}
