package com.qurantime.feature.mushaf

import androidx.compose.ui.geometry.Offset
import com.qurantime.feature.mushaf.ui.SweepSegment
import com.qurantime.feature.mushaf.ui.ayahLines
import com.qurantime.feature.mushaf.ui.sweepSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reading sweep: an ayah's outline as lines, filled right to left, top to bottom. */
class RecitationSweepTest {
    // Page 3, ayah 7 (from ayat_timing_2_5.json): the end of one line, then the start of the next.
    private val ayah7 = "255.17,46.11 0,46.11 0,82.63 84.45,82.63 84.45,118.63 343,118.63 343,82.11 255.17,82.11 255.17,46.11"
        .split(' ').map { p -> p.split(',').let { Offset(it[0].toFloat(), it[1].toFloat()) } }

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.05f)

    @Test
    fun `an ayah outline becomes its text lines, in reading order`() {
        val lines = ayahLines(ayah7)
        assertEquals(2, lines.size)
        // First line: from the right edge of the outline (255) to the page's left.
        near(0f, lines[0].left); near(255.17f, lines[0].right); near(46.11f, lines[0].top)
        // Second line: the full measure down to 84; the two lines meet with no gap
        // (the outline's 82.11 / 82.63 steps are one boundary).
        near(84.45f, lines[1].left); near(343f, lines[1].right); near(118.63f, lines[1].bottom)
        near(lines[0].bottom, lines[1].top)
    }

    @Test
    fun `nothing is filled at the start and everything at the end`() {
        val lines = ayahLines(ayah7)
        assertTrue(sweepSegments(lines, 0f).isEmpty())
        val all = sweepSegments(lines, 1f)
        assertEquals(2, all.size)
        assertTrue(all.all { !it.leading })
        near(lines[0].width, all[0].rect.width)
        near(lines[1].width, all[1].rect.width)
    }

    @Test
    fun `halfway fills the first line, then the next from its right edge`() {
        val lines = ayahLines(ayah7)
        val total = lines.sumOf { it.width.toDouble() }.toFloat()
        val segs = sweepSegments(lines, (lines[0].width + 40f) / total)
        assertEquals(2, segs.size)
        assertEquals(SweepSegment(lines[0], leading = false), segs[0])
        // Arabic reads right to left: the second line fills from x = 343 leftwards.
        near(343f, segs[1].rect.right)
        near(303f, segs[1].rect.left)
        assertTrue(segs[1].leading)
    }

    @Test
    fun `progress outside 0 to 1 is clamped`() {
        val lines = ayahLines(ayah7)
        assertTrue(sweepSegments(lines, -0.3f).isEmpty())
        assertEquals(sweepSegments(lines, 1f), sweepSegments(lines, 7f))
    }
}
