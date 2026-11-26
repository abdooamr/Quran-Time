package com.qurantime.kotlin.core.l10n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitsTest {
    @Test
    fun `western digits in English`() {
        assertEquals("302", localDigits(302, Locale.ENGLISH))
    }

    @Test
    fun `eastern arabic digits in Arabic`() {
        assertEquals("٣", localDigits(3, Locale.forLanguageTag("ar")))
        assertEquals("٣٠٢", localDigits(302, Locale.forLanguageTag("ar")))
    }

    @Test
    fun `parses western, eastern arabic and persian digits`() {
        assertEquals(302, parseLocalDigits("٣٠٢"))
        assertEquals(302, parseLocalDigits(" 302 "))
        assertEquals(45, parseLocalDigits("۴۵"))
        assertNull(parseLocalDigits("12a"))
        assertNull(parseLocalDigits(""))
    }

    @Test
    fun `sizes use the locale's digits and decimal mark`() {
        assertEquals("3.0", com.qurantime.kotlin.feature.downloads.ui.formatMb(3L * 1024 * 1024, java.util.Locale.ENGLISH))
        assertEquals("٣٫٥", com.qurantime.kotlin.feature.downloads.ui.formatMb(3L * 1024 * 1024 + 512 * 1024, java.util.Locale.forLanguageTag("ar")))
    }
}
