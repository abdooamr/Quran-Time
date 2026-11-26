package com.qurantime.core.api

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlsTest {
    @Test
    fun `upgradeToHttps cases`() {
        assertEquals("https://mp3quran.de/x.jpg", upgradeToHttps("http://mp3quran.de/x.jpg"))
        assertEquals("https://a.b/c", upgradeToHttps("https://a.b/c"))
        assertEquals("https://a.b/c", upgradeToHttps("HTTP://a.b/c"))
    }

    @Test
    fun `surahAudioUrl pads to three digits`() {
        assertTrue(surahAudioUrl("https://server6.mp3quran.net/akdr/", 1).endsWith("/akdr/001.mp3"))
        assertTrue(surahAudioUrl("https://server6.mp3quran.net/akdr/", 114).endsWith("/akdr/114.mp3"))
        assertEquals("https://server6.mp3quran.net/akdr/018.mp3", surahAudioUrl("https://server6.mp3quran.net/akdr", 18))
        assertEquals("https://server6.mp3quran.net/akdr/018.mp3", surahAudioUrl("http://server6.mp3quran.net/akdr/", 18))
    }

    @Test
    fun `normalizeServer cases`() {
        assertEquals("https://server10.mp3quran.net/ajm", normalizeServer("http://Server10.mp3quran.net/ajm/"))
        assertEquals("https://server10.mp3quran.net/ajm", normalizeServer("https://server10.mp3quran.net/ajm"))
    }

    @Test
    fun `mushafPageUrl cases`() {
        assertEquals("https://www.mp3quran.net/api/quran_pages_svg/003.svg", mushafPageUrl(3))
        assertEquals("https://www.mp3quran.net/api/quran_pages_svg/604.svg", mushafPageUrl(604))
        assertThrows(IllegalArgumentException::class.java) { mushafPageUrl(0) }
        assertThrows(IllegalArgumentException::class.java) { mushafPageUrl(605) }
    }

    @Test
    fun `ApiLanguage fromLocale`() {
        assertEquals("eng", ApiLanguage.fromLocale(Locale.forLanguageTag("en")).code)
        assertEquals("ar", ApiLanguage.fromLocale(Locale.forLanguageTag("ar")).code)
        assertEquals("ar", ApiLanguage.fromLocale(Locale.forLanguageTag("fr")).code)
    }
}
