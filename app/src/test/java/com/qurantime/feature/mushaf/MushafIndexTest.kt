package com.qurantime.feature.mushaf

import androidx.compose.ui.geometry.Size
import com.qurantime.core.api.MUSHAF_PAGES
import com.qurantime.feature.catalog.parseSuwar
import com.qurantime.support.loadFixtureJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafIndexTest {
    private val suwar = parseSuwar(loadFixtureJson("suwar_eng.json"))

    @Test
    fun `juzForPage cases`() {
        assertEquals(1, juzForPage(1))
        assertEquals(1, juzForPage(21))
        assertEquals(2, juzForPage(22))
        assertEquals(29, juzForPage(581))
        assertEquals(30, juzForPage(582))
        assertEquals(30, juzForPage(604))
        assertEquals(30, JUZ_START_PAGES.size)
        assertEquals(604, MUSHAF_PAGES)
    }

    @Test
    fun `juzForPage rejects out-of-range pages`() {
        assertThrows(IllegalArgumentException::class.java) { juzForPage(0) }
        assertThrows(IllegalArgumentException::class.java) { juzForPage(605) }
    }

    @Test
    fun `surahsOnPage cases`() {
        assertEquals(listOf(1), surahsOnPage(1, suwar).map { it.id })
        assertEquals(listOf(2), surahsOnPage(2, suwar).map { it.id })
        assertEquals(listOf(112, 113, 114), surahsOnPage(604, suwar).map { it.id })
        assertTrue(18 in surahsOnPage(293, suwar).map { it.id })
    }

    @Test
    fun `viewBoxForPage cases`() {
        assertEquals(Size(235f, 235f), viewBoxForPage(1))
        assertEquals(Size(235f, 235f), viewBoxForPage(2))
        assertEquals(Size(345f, 550f), viewBoxForPage(3))
        assertEquals(Size(345f, 550f), viewBoxForPage(604))
    }
}
