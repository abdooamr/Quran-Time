package com.qurantime.kotlin.core.settings

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.qurantime.kotlin.core.nav.AppNavHost
import com.qurantime.kotlin.core.theme.AppThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun open(file: File = File(tmp.root, "settings.preferences_pb")): SettingsStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun `defaults`() = runTest {
        val s = open().also { it.load() }.state.value
        assertEquals("ar", s.locale)
        assertEquals(AppThemeMode.SYSTEM, s.themeMode)
        assertEquals(5, s.mushafReadId)
        assertTrue(s.autoFollow)
        assertEquals(1, s.lastPage)
        assertNull(s.lastPlaybackJson)
    }

    @Test
    fun `persists`() = runTest {
        val file = File(tmp.root, "p.preferences_pb")
        val s = open(file).also { it.load() }
        s.setLastPage(50)
        s.setLocale("en")
        s.setThemeMode(AppThemeMode.SEPIA)
        s.setMushafReadId(7)
        s.setAutoFollow(false)
        s.setLastPlaybackJson("""{"index":0}""")
        scopes.removeAt(scopes.lastIndex).cancel()

        val again = open(file).also { it.load() }.state.value
        assertEquals(50, again.lastPage)
        assertEquals("en", again.locale)
        assertEquals(AppThemeMode.SEPIA, again.themeMode)
        assertEquals(7, again.mushafReadId)
        assertFalse(again.autoFollow)
        assertEquals("""{"index":0}""", again.lastPlaybackJson)
    }

    @Test
    fun `notifies once per setter`() = runTest {
        val s = open().also { it.load() }
        s.state.test {
            awaitItem() // current
            s.setLastPage(3)
            assertEquals(3, awaitItem().lastPage)
            s.setThemeMode(AppThemeMode.DARK)
            assertEquals(AppThemeMode.DARK, awaitItem().themeMode)
            expectNoEvents()
        }
    }

    @Test
    fun `switching locale rebuilds LTR`() {
        val s = open().also { runBlocking { it.load() } }
        var direction: LayoutDirection? = null
        compose.setContent {
            SettingsDrivenApp(s) {
                direction = LocalLayoutDirection.current
                AppNavHost(rememberNavController())
            }
        }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Rtl, direction)

        runBlocking { s.setLocale("en") }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Ltr, direction)
        assertTrue(compose.onAllNodesWithText("Listen").fetchSemanticsNodes().isNotEmpty())
    }
}
