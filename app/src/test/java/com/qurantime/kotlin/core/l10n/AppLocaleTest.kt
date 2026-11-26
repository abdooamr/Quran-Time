package com.qurantime.kotlin.core.l10n

import android.content.res.Configuration
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qurantime.kotlin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dialogs and bottom sheets draw in their own window with the activity's own
 * resources, so the app language must be applied to the activity's context,
 * not only inside Compose.
 */
@RunWith(AndroidJUnit4::class)
class AppLocaleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the wrapped context speaks the saved language`() {
        AppLocale.save(context, "ar")
        val ar = AppLocale.wrap(context)
        assertEquals("ar", ar.resources.configuration.locales[0].language)
        assertEquals("المزيد", ar.getString(R.string.tab_more))

        AppLocale.save(context, "en")
        assertEquals("More", AppLocale.wrap(context).getString(R.string.tab_more))
    }

    @Test
    fun `Arabic is the default`() {
        AppLocale.save(context, null)
        assertEquals("ar", AppLocale.saved(context))
    }

    // Found on the A50: the activity's context copied the whole configuration,
    // which pinned dark mode, orientation and screen size to their values at
    // launch (Theme = System stopped following the phone). Only the language
    // may be overridden; everything else must follow the system.
    @Test
    fun `the activity override sets only the language`() {
        val o = AppLocale.override("ar")
        assertEquals("ar", o.locales[0].language)
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, o.layoutDirection)
        assertEquals(Configuration.UI_MODE_TYPE_UNDEFINED or Configuration.UI_MODE_NIGHT_UNDEFINED, o.uiMode)
        assertEquals(Configuration.ORIENTATION_UNDEFINED, o.orientation)
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, o.screenWidthDp)
        assertEquals(Configuration.DENSITY_DPI_UNDEFINED, o.densityDpi)
        assertEquals(0f, o.fontScale) // undefined: the phone's font size still applies
    }
}
