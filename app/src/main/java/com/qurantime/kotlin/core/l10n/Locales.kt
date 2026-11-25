package com.qurantime.kotlin.core.l10n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Shows [content] in the app's chosen language (`ar` or `en`), whatever the
 * phone's language is. Switching needs no activity restart: strings and the
 * layout direction follow at once.
 */
@Composable
fun LocalizedContent(tag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    val localized = remember(base, tag) { LocalizedContext(base, locale) }
    val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/** Keeps the activity chain (for result launchers) but swaps the resources. */
private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {
    private val res: Resources = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        },
    ).resources

    override fun getResources(): Resources = res
}
