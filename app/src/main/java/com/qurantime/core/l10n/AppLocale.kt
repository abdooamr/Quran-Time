package com.qurantime.core.l10n

import androidx.core.content.edit
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language at the Android level. Compose content follows the
 * setting live ([LocalizedContent]); dialogs, bottom sheets and anything
 * outside Compose use the activity's own resources, so the activity is
 * created with this language too. Kept in a tiny SharedPreferences file
 * because it must be read synchronously before the activity exists.
 */
object AppLocale {
    private const val FILE = "app_locale"
    private const val KEY = "tag"

    fun saved(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: "ar"

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }
    }

    /**
     * A configuration that sets only the language and its layout direction.
     * Everything left undefined (night mode, orientation, screen size, font
     * scale) keeps following the system when it changes.
     */
    fun override(tag: String): Configuration {
        val locale = Locale.forLanguageTag(tag)
        return Configuration().apply {
            fontScale = 0f // setToDefaults() made it 1: leave it to the phone
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }

    /** [base] with the saved language applied (for attachBaseContext). */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        Locale.setDefault(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(override(tag))
    }
}
