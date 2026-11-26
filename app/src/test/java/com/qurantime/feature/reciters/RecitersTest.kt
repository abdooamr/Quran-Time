package com.qurantime.feature.reciters

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.api.ApiException
import com.qurantime.core.api.normalizeServer
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.catalog.parseMoshafTypes
import com.qurantime.feature.timing.parseTimedReads
import com.qurantime.support.TestContainer
import com.qurantime.support.await
import com.qurantime.support.launchApp
import com.qurantime.support.loadFixtureJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter reciter_filter_test.dart. */
@RunWith(AndroidJUnit4::class)
class ReciterFilterTest {
    @get:Rule val rule = createComposeRule()
    private val reciters = parseReciters(loadFixtureJson("reciters_eng.json"))
    private val types = parseMoshafTypes(loadFixtureJson("moshaf_eng.json")).associateBy { it.id }
    private val timed = parseTimedReads(loadFixtureJson("ayat_timing_reads.json")).map { normalizeServer(it.folderUrl) }.toSet()
    private fun isTimed(m: Moshaf) = normalizeServer(m.server) in timed

    private fun run(f: ReciterFilter, favorites: Set<Int> = emptySet(), from: List<Reciter> = reciters) =
        applyReciterFilter(from, f, types, ::isTimed, favorites)

    @Test
    fun `query is case-insensitive`() {
        assertTrue(231 in run(ReciterFilter(query = "hazza")).map { it.id })
    }

    @Test
    fun `riwaya keeps reciters with a recording in that riwaya`() {
        val out = run(ReciterFilter(riwayaId = 2))
        assertTrue(out.isNotEmpty())
        for (r in out) assertTrue(r.name, r.moshaf.any { types[it.moshafType]?.riwayaId == 2 })
    }

    @Test
    fun `style keeps reciters with that exact recording type`() {
        val out = run(ReciterFilter(moshafTypeId = 11))
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { r -> r.moshaf.any { it.moshafType == 11 } })
    }

    @Test
    fun `timedOnly keeps reciters with a recording that follows the Mushaf`() {
        val out = run(ReciterFilter(timedOnly = true))
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { r -> r.moshaf.any(::isTimed) })
    }

    @Test
    fun `favoritesOnly`() {
        assertEquals(231, run(ReciterFilter(favoritesOnly = true), favorites = setOf(231)).single().id)
    }

    @Test
    fun `arabic diacritics and tatweel are ignored`() {
        val synthetic = listOf(
            Reciter(id = 1, name = "مِشَارِي راشـد العفاسي", letter = "م", moshaf = emptyList()),
            Reciter(id = 2, name = "سعد الغامدي", letter = "س", moshaf = emptyList()),
        )
        assertEquals(1, run(ReciterFilter(query = "مشاري راشد"), from = synthetic).single().id)
    }

    @Test
    fun `results are sorted by letter, then name`() {
        val out = run(ReciterFilter())
        for (i in 1 until out.size) {
            val a = out[i - 1]
            val b = out[i]
            val byLetter = a.letter.compareTo(b.letter)
            assertTrue("${a.name} / ${b.name}", byLetter < 0 || (byLetter == 0 && a.name <= b.name))
        }
    }

    @Test
    fun `AsyncView - an error shows Retry, which loads again`() {
        var calls = 0
        rule.setContent { AsyncView(load = { if (++calls == 1) error("offline") else listOf(1) }) { Text("rows ${it.size}") } }
        rule.onNodeWithText("Couldn't load this. Check your connection and try again.").assertExists()
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()
        assertEquals(2, calls)
        rule.onNodeWithText("rows 1").assertExists()
    }

    @Test
    fun `AsyncView - empty data shows the empty text`() {
        rule.setContent { AsyncView(load = { emptyList<Int>() }, isEmpty = { it.isEmpty() }, emptyText = "Nothing here") { Text("rows") } }
        rule.onNodeWithText("Nothing here").assertExists()
    }
}

/** Ports of the Flutter reciters_screen_test.dart. */
@RunWith(AndroidJUnit4::class)
class RecitersScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun openListen(c: TestContainer = TestContainer()): TestContainer {
        rule.launchApp(c)
        rule.onNodeWithText("Listen").performClick()
        rule.await { rule.onAllNodesWithText("Recently added").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        return c
    }

    private fun tile(id: Int) = hasTestTag("reciter-$id")

    @Test
    fun `reciters are grouped under letter headers`() {
        openListen()
        assertTrue(rule.onAllNodesWithText("A").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Recently added").assertExists()
    }

    @Test
    fun `a reciter with a timed recording says it follows the Mushaf`() {
        openListen()
        val ajmi = TestContainer.fixtureMatcher.moshafForRead(5)!!.reciter
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(tile(ajmi.id))
        rule.onNode(hasText("Follows the Mushaf") and hasAnyAncestor(tile(ajmi.id)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `search narrows the list`() {
        openListen()
        rule.onNode(hasSetTextAction()).performTextInput("hazza")
        rule.waitForIdle()
        rule.onNode(tile(231)).assertExists()
        val tiles = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a reciter row") { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("reciter-") == true
        }).fetchSemanticsNodes().size
        assertEquals("recently-added row + Hazza: one reciter row", 1, tiles)
        rule.onNodeWithText("Recently added").assertExists()
    }

    @Test
    fun `the star toggles a favourite`() {
        val c = openListen()
        rule.onNode(hasSetTextAction()).performTextInput("hazza")
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Add to favourites") and hasAnyAncestor(tile(231)), useUnmergedTree = true).performClick()
        rule.await { runBlocking { c.db.favoritesDao().watch("reciter").first() } == listOf(231) }
        rule.await {
            rule.onAllNodes(hasContentDescription("Remove from favourites") and hasAnyAncestor(tile(231)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `first launch offline shows Retry, not an endless spinner`() {
        val c = TestContainer()
        c.fixtureApi.handler = { _, _ -> throw ApiException(null, "offline") }
        openListen(c)
        rule.onAllNodesWithText("Retry").onFirst().assertExists()
        assertTrue(rule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty())
    }
}
