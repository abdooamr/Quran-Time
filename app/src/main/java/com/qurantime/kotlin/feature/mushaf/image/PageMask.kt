package com.qurantime.kotlin.feature.mushaf.image

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.io.InputStream
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

/** Draws a Mushaf SVG page into a [PageMask]. Slow (tens of ms): never call it on the main thread. */
object SvgRasterizer {
    fun render(svg: InputStream, viewBox: Size, widthPx: Int): PageMask {
        val doc = SVG.getFromInputStream(svg)
        val heightPx = (widthPx * viewBox.height / viewBox.width).roundToInt()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            doc.renderToCanvas(Canvas(bmp), RenderOptions().viewPort(0f, 0f, widthPx.toFloat(), heightPx.toFloat()))
            val pixels = IntArray(widthPx * heightPx)
            bmp.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            val alpha = ByteArray(pixels.size) { i -> quantise(pixels[i] ushr 24).toByte() }
            return PageMask(widthPx, heightPx, alpha)
        } finally {
            bmp.recycle()
        }
    }
}
