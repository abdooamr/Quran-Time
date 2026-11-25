package com.qurantime.kotlin.feature.mushaf.image

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * A Mushaf page as an 8-bit coverage mask: one byte per pixel, 0 = paper,
 * 255 = full ink. Values are quantised to 16 levels, which keeps letter
 * edges smooth while compressing far better than 256 levels.
 */
class PageMask(val width: Int, val height: Int, val alpha: ByteArray) {
    init {
        require(alpha.size == width * height) { "mask ${width}x$height needs ${width * height} bytes, got ${alpha.size}" }
    }
}

/** One of 16 levels (0x00, 0x11, … 0xFF) closest below [v]. */
fun quantise(v: Int): Int = (v and 0xF0) or (v ushr 4)

/** Page images are made for widths rounded up to 120 px, so close screen sizes share files. */
fun renderWidthBucket(px: Int): Int = ((px + 119) / 120) * 120

class CorruptMaskException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Draws a Mushaf SVG page straight into an 8-bit alpha bitmap (the ink's
 * coverage), with no full-colour copy. Slow (tens of ms): never call it on
 * the main thread.
 */
object SvgRasterizer {
    /** The page as an `ALPHA_8` bitmap [widthPx] wide (used as-is for sharp zoom). */
    fun renderBitmap(svg: InputStream, viewBox: Size, widthPx: Int): Bitmap {
        val doc = SVG.getFromInputStream(svg)
        val heightPx = (widthPx * viewBox.height / viewBox.width).roundToInt()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
        doc.renderToCanvas(Canvas(bmp), RenderOptions().viewPort(0f, 0f, widthPx.toFloat(), heightPx.toFloat()))
        return bmp
    }

    /** The page as a quantised [PageMask], ready to store. */
    fun render(svg: InputStream, viewBox: Size, widthPx: Int): PageMask {
        val bmp = renderBitmap(svg, viewBox, widthPx)
        try {
            val w = bmp.width
            val h = bmp.height
            val row = bmp.rowBytes
            val buf = ByteBuffer.allocate(row * h)
            bmp.copyPixelsToBuffer(buf)
            val raw = buf.array()
            val alpha = ByteArray(w * h)
            for (y in 0 until h) {
                val src = y * row
                val dst = y * w
                for (x in 0 until w) alpha[dst + x] = QUANTISED[raw[src + x].toInt() and 0xFF]
            }
            return PageMask(w, h, alpha)
        } finally {
            bmp.recycle()
        }
    }

    private val QUANTISED = ByteArray(256) { quantise(it).toByte() }
}
