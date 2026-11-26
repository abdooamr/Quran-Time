package com.qurantime.feature.mushaf.image

import androidx.compose.ui.geometry.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaskCodecTest {
    private val codecs = listOf<MaskCodec>(RawDeflateMaskCodec)

    /** A 50×80 mask with every alpha value present, already quantised. */
    private fun sample(): PageMask {
        val w = 50
        val h = 80
        return PageMask(w, h, ByteArray(w * h) { i -> quantise(i % 256).toByte() })
    }

    private fun MaskCodec.roundTrip(m: PageMask): ByteArray {
        val out = ByteArrayOutputStream()
        encode(m, out)
        val bmp = decode(ByteArrayInputStream(out.toByteArray()))
        assertEquals(m.width, bmp.width)
        assertEquals(m.height, bmp.height)
        return ByteArray(m.width * m.height) { i -> bmp.getPixel(i % m.width, i / m.width).ushr(24).toByte() }
    }

    @Test
    fun `round trip keeps every pixel`() {
        val m = sample()
        for (c in codecs) assertArrayEquals(c.ext, m.alpha, c.roundTrip(m))
    }

    @Test
    fun `quantises to 16 levels`() {
        val levels = (0..255).map(::quantise).toSet()
        assertEquals(16, levels.size)
        assertEquals(0, quantise(0))
        assertEquals(255, quantise(255))
        assertEquals(0x77, quantise(0x7A))
        assertTrue(levels.all { it and 0x0F == it ushr 4 })
    }

    @Test
    fun `truncated file throws CorruptMaskException`() {
        for (c in codecs) {
            val out = ByteArrayOutputStream()
            c.encode(sample(), out)
            val bytes = out.toByteArray()
            for (n in listOf(0, 5, bytes.size / 2)) {
                assertThrows("${c.ext} cut to $n", CorruptMaskException::class.java) {
                    c.decode(ByteArrayInputStream(bytes.copyOf(n)))
                }
            }
        }
    }

    @Test
    fun `bucket rounds up to 120`() {
        assertEquals(1080, renderWidthBucket(1080))
        assertEquals(1200, renderWidthBucket(1081))
        assertEquals(120, renderWidthBucket(1))
    }

    @Test
    fun `rasterizer draws the page at the requested width`() {
        val svg = javaClass.classLoader!!.getResourceAsStream("svg/page-003.svg")!!
        val m = SvgRasterizer.render(svg, Size(345f, 550f), 345)
        assertEquals(345, m.width)
        assertEquals(550, m.height)
        val inked = m.alpha.count { it.toInt() != 0 }
        assertTrue("some ink: $inked", inked > m.alpha.size / 50)
        assertTrue("mostly paper: $inked", inked < m.alpha.size / 2)
        assertTrue(m.alpha.all { val v = it.toInt() and 0xFF; v == quantise(v) })
    }

    // Review (Focus 1): a damaged header must not make the app allocate
    // gigabytes or crash; it is a corrupt file like any other.
    @Test
    fun `absurd dimensions are rejected before allocating`() {
        val bytes = java.io.ByteArrayOutputStream().also { o ->
            java.io.DataOutputStream(o).apply {
                write("QTM1".toByteArray())
                writeShort(65535)
                writeShort(65535)
                writeInt(10)
                write(ByteArray(10))
            }
        }.toByteArray()
        org.junit.Assert.assertThrows(CorruptMaskException::class.java) {
            RawDeflateMaskCodec.decodeMask(bytes.inputStream())
        }
    }

    // Release review: the payload length was not bounded, so a damaged length
    // allocated up to 2 GB (OutOfMemoryError, not caught) before failing.
    @Test
    fun `an absurd payload length is rejected before allocating`() {
        val bytes = java.io.ByteArrayOutputStream().also { o ->
            java.io.DataOutputStream(o).apply {
                write("QTM1".toByteArray())
                writeShort(100)
                writeShort(100)
                writeInt(Int.MAX_VALUE)
                write(ByteArray(10))
            }
        }.toByteArray()
        org.junit.Assert.assertThrows(CorruptMaskException::class.java) {
            RawDeflateMaskCodec.decodeMask(bytes.inputStream())
        }
    }
}
