package com.qurantime.kotlin.core.l10n

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
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }.apply()
    }

    /** [base] with the saved language applied (for attachBaseContext). */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(saved(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
