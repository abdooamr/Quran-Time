package com.qurantime.kotlin.feature.mushaf

import androidx.compose.ui.geometry.Size
import com.qurantime.kotlin.core.api.MUSHAF_PAGES
import com.qurantime.kotlin.feature.catalog.Surah

/** First page of each juz in the 604-page Madinah Mushaf. */
val JUZ_START_PAGES = intArrayOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
    201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
    402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
)

fun juzForPage(page: Int): Int {
    require(page in 1..MUSHAF_PAGES) { "page $page not in 1..$MUSHAF_PAGES" }
    return JUZ_START_PAGES.indexOfLast { it <= page } + 1
}

/** Surahs that appear on [page], in order. */
fun surahsOnPage(page: Int, suwar: List<Surah>): List<Surah> = suwar.filter { page in it.startPage..it.endPage }

private val SQUARE = Size(235f, 235f)
private val TALL = Size(345f, 550f)

/** The SVG viewBox size: pages 1–2 are square, the rest are 345×550. */
fun viewBoxForPage(page: Int): Size = if (page <= 2) SQUARE else TALL
