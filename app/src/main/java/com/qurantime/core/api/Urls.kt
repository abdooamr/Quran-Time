package com.qurantime.core.api

import java.util.Locale

/** The two content languages the app requests from the API. */
enum class ApiLanguage(val code: String) {
    AR("ar"),
    ENG("eng"),
    ;

    companion object {
        fun fromLocale(locale: Locale): ApiLanguage = if (locale.language == "en") ENG else AR
        fun fromTag(tag: String): ApiLanguage = fromLocale(Locale.forLanguageTag(tag))
    }
}

const val API_BASE = "https://www.mp3quran.net/api/v3/"
const val MUSHAF_PAGES = 604

fun upgradeToHttps(url: String): String {
    val t = url.trim()
    return if (t.length >= 7 && t.substring(0, 7).equals("http://", ignoreCase = true)) "https://" + t.substring(7) else t
}

/** https, lowercase, no trailing slash — for comparing audio folders. */
fun normalizeServer(url: String): String = upgradeToHttps(url).lowercase(Locale.ROOT).trimEnd('/')

fun surahAudioUrl(server: String, surah: Int): String {
    val base = upgradeToHttps(server)
    val folder = if (base.endsWith('/')) base else "$base/"
    return folder + surah.toString().padStart(3, '0') + ".mp3"
}

fun mushafPageUrl(page: Int): String {
    require(page in 1..MUSHAF_PAGES) { "page $page not in 1..$MUSHAF_PAGES" }
    return "https://www.mp3quran.net/api/quran_pages_svg/" + page.toString().padStart(3, '0') + ".svg"
}
