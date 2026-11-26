package com.qurantime.core.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.core.l10n.LocalizedContent
import com.qurantime.core.theme.AppThemeMode
import com.qurantime.core.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private var direction: LayoutDirection? = null

    private fun launch(tag: String) {
        rule.setContent {
            LocalizedContent(tag) {
                direction = LocalLayoutDirection.current
                nav = rememberNavController()
                AppNavHost(nav)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `shows five tabs in Arabic with RTL`() {
        launch("ar")
        assertEquals(LayoutDirection.Rtl, direction)
        for (label in listOf("المصحف", "استماع", "مباشر", "استكشاف", "المزيد")) {
            assertTrue(label, rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `shows English labels LTR`() {
        launch("en")
        assertEquals(LayoutDirection.Ltr, direction)
        for (label in listOf("Mushaf", "Listen", "Live", "Explore", "More")) {
            assertTrue(label, rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `starts on Mushaf tab`() {
        launch("en")
        assertTrue(nav.currentDestination!!.hasRoute<MushafRoute>())
    }

    @Test
    fun `tapping Listen tab navigates to listen`() {
        launch("en")
        rule.onAllNodesWithText("Listen").onFirst().performClick()
        rule.waitForIdle()
        assertTrue(nav.currentDestination!!.hasRoute<ListenRoute>())
    }

    @Test
    fun `sepia palette`() {
        assertEquals(Color(0xFFEBDDC2), paletteFor(AppThemeMode.SEPIA, systemDark = false).background)
        assertEquals(Color(0xFFE6E1D6), paletteFor(AppThemeMode.DARK, systemDark = false).ink)
        assertEquals(Color(0xFF121212), paletteFor(AppThemeMode.SYSTEM, systemDark = true).background)
        assertEquals(Color(0xFF231F20), paletteFor(AppThemeMode.LIGHT, systemDark = true).ink)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = this[0]
