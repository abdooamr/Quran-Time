package com.qurantime.kotlin.core.l10n

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review I2: the activity handles uiMode and orientation changes itself, so
 * the localized configuration must follow them, not freeze at the first frame.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `system dark mode and orientation changes reach the localized content`() {
        val start = Configuration(ApplicationProvider.getApplicationContext<android.content.Context>().resources.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        var system by mutableStateOf(start)
        var dark: Boolean? = null
        var seenOrientation = -1
        var language = ""
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides system) {
                LocalizedContent("ar") {
                    dark = isSystemInDarkTheme()
                    seenOrientation = LocalConfiguration.current.orientation
                    language = LocalConfiguration.current.locales[0].language
                }
            }
        }
        rule.waitForIdle()
        assertEquals(false, dark)
        system = Configuration(start).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
            orientation = Configuration.ORIENTATION_LANDSCAPE
            // A rotation swaps the screen's width and height too.
            screenWidthDp = start.screenHeightDp
            screenHeightDp = start.screenWidthDp
        }
        rule.waitForIdle()
        assertEquals(true, dark)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, seenOrientation)
        assertEquals("ar", language)
    }
}
