package com.qurantime.kotlin.core.l10n

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
}
