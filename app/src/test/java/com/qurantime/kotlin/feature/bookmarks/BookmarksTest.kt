package com.qurantime.kotlin.feature.bookmarks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.qurantime.kotlin.core.db.AppDatabase
import com.qurantime.kotlin.support.awaitTag
import com.qurantime.kotlin.support.launchApp
import com.qurantime.kotlin.support.memoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter bookmarks_repository_test.dart. */
@RunWith(AndroidJUnit4::class)
class BookmarksRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BookmarksRepository

    @Before fun setUp() = runTest {
        db = memoryDb()
        repo = BookmarksRepository(db.bookmarksDao())
        repo.load()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `togglePage adds then removes`() = runTest {
        repo.togglePage(5)
        assertTrue(repo.isPageBookmarked(5))
        assertTrue(repo.hasPage(5))
        repo.togglePage(5)
        assertFalse(repo.isPageBookmarked(5))
        assertFalse(repo.hasPage(5))
    }

    @Test
    fun `all is sorted by page`() = runTest {
        repo.togglePage(300)
        repo.togglePage(2)
        repo.togglePage(50)
        assertEquals(listOf(2, 50, 300), repo.all().map { it.page })
    }

    @Test
    fun `ayah bookmarks keep surah and ayah and do not count as page bookmarks`() = runTest {
        repo.addAyah(2, 255, 42)
        val b = repo.all().single()
        assertEquals(Triple(42, 2, 255), Triple(b.page, b.surah, b.ayah))
        assertFalse(repo.hasPage(42))
    }

    @Test
    fun `remove by id and notify`() = runTest {
        repo.items.test {
            awaitItem() // loaded (empty)
            repo.togglePage(7)
            val id = awaitItem().single().id
            repo.remove(id)
            assertTrue(awaitItem().isEmpty())
            expectNoEvents()
        }
    }

    @Test
    fun `adding the same ayah twice keeps one`() = runTest {
        repo.addAyah(2, 255, 42)
        repo.addAyah(2, 255, 42)
        assertEquals(1, repo.all().size)
    }
}

/** Ports of the Flutter bookmarks_flow_test.dart (the jump-sheet case is in Task 16's JumpSheetTest). */
@RunWith(AndroidJUnit4::class)
class BookmarksFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `bookmark a page, then open it from More - Bookmarks`() {
        val c = rule.launchApp(lastPage = 2)
        rule.onNodeWithContentDescription("Bookmark this page").performClick()
        rule.waitUntil(5_000) { c.bookmarks.hasPage(2) }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Remove bookmark").fetchSemanticsNodes().isNotEmpty() }
        c.mushaf.goTo(40)
        rule.waitForIdle()
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Bookmarks").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Page 2").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Page 2").performClick()
        rule.waitUntil(5_000) { c.mushaf.currentPage.value == 2 }
        rule.awaitTag("mushaf-page-2")
    }

    @Test
    fun `empty bookmarks explain how to add one`() {
        rule.launchApp()
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Bookmarks").performClick()
        rule.onNodeWithText("No bookmarks yet. Tap the bookmark icon on any page to save it.").assertExists()
    }
}
