package com.qurantime.kotlin.feature.settings

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.qurantime.kotlin.core.nav.AppTab
import com.qurantime.kotlin.core.nav.openTab
import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.core.nav.AboutRoute
import com.qurantime.kotlin.core.nav.SettingsRoute
import com.qurantime.kotlin.core.theme.AppThemeMode
import com.qurantime.kotlin.support.TestContainer
import com.qurantime.kotlin.support.await
import com.qurantime.kotlin.support.launchApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ports of the Flutter settings_screen_test.dart. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()
    private var nav: NavHostController? = null

    private fun exists(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun open(route: Any, locale: String): TestContainer {
        val c = rule.launchApp(TestContainer(), locale = locale, onNav = { nav = it })
        // As a user gets there: the More tab, then the screen.
        rule.runOnUiThread { nav!!.openTab(AppTab.MORE) }
        rule.waitForIdle()
        rule.runOnUiThread { nav!!.navigate(route) }
        rule.waitForIdle()
        return c
    }

    private fun left(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun `switching to English turns the app LTR and asks the API in English`() {
        val c = open(SettingsRoute, locale = "ar")
        rule.await { exists("English") }
        // RTL: Listen (the second tab) is right of More (the last). "Mushaf" is
        // also a section title on this screen, so the tabs are told apart by these two.
        assertTrue(left("استماع") > left("المزيد"))
        rule.onNodeWithText("English").performClick()
        rule.await { exists("Listen") }
        assertEquals("en", c.settings.state.value.locale)
        assertTrue(left("Listen") < left("More"))
        rule.runOnUiThread { nav!!.openTab(AppTab.MUSHAF) }
        rule.await { c.fixtureApi.callsTo("suwar").lastOrNull()?.query?.get("language") == "eng" }
    }

    @Test
    fun `choosing Sepia saves it`() {
        val c = open(SettingsRoute, locale = "ar")
        rule.await { exists("ورقي") }
        rule.onNodeWithText("ورقي").performClick()
        rule.await { c.settings.state.value.themeMode == AppThemeMode.SEPIA }
    }

    @Test
    fun `the auto-follow switch persists`() {
        val c = open(SettingsRoute, locale = "en")
        rule.await { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.await { !c.settings.state.value.autoFollow }
        // The state is what DataStore wrote: a fresh load reads it back.
        kotlinx.coroutines.runBlocking { c.settings.load() }
        assertFalse(c.settings.state.value.autoFollow)
    }

    @Test
    fun `About credits MP3Quran_net and states the privacy promise`() {
        open(AboutRoute, locale = "en")
        rule.await { exists("MP3Quran.net", substring = true) }
        assertTrue(exists("collects no personal data", substring = true))
        assertTrue(exists("Version 1.0.0"))
    }

    @Test
    fun `the Mushaf reciter row names the chosen reciter`() {
        open(SettingsRoute, locale = "en")
        rule.await { exists("Mushaf reciter") }
        rule.await { exists(" · ", substring = true) }
    }
}
