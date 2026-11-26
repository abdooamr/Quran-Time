package com.qurantime.kotlin.feature.mushaf.image

import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Stores a [PageMask] on disk and reads it back as an `ALPHA_8` bitmap, which
 * Compose draws tinted with the theme's ink colour at no extra cost.
 *
 * Every file starts with `QTM1`, width (u16), height (u16) and the payload
 * length (u32); a short or damaged file throws [CorruptMaskException].
 */
interface MaskCodec {
    val ext: String
    fun encode(m: PageMask, out: OutputStream)
    fun decode(input: InputStream): Bitmap
}

private const val MAX_MASK_WIDTH = 4096
private const val MAX_MASK_HEIGHT = 8192

private val MAGIC = byteArrayOf('Q'.code.toByte(), 'T'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())

private fun writeFile(out: OutputStream, m: PageMask, payload: ByteArray) {
    DataOutputStream(out).apply {
        write(MAGIC)
        writeShort(m.width)
        writeShort(m.height)
        writeInt(payload.size)
        write(payload)
        flush()
    }
}

/** Reads the header and payload; returns width, height and payload bytes. */
private fun readFile(input: InputStream): Triple<Int, Int, ByteArray> = try {
    val d = DataInputStream(input)
    val magic = ByteArray(4).also { d.readFully(it) }
    if (!magic.contentEquals(MAGIC)) throw CorruptMaskException("not a mask file")
    val w = d.readUnsignedShort()
    val h = d.readUnsignedShort()
    val n = d.readInt()
    // Pages are made at most a few thousand pixels wide; anything larger is a
    // damaged header (and would overflow or exhaust memory when allocated).
    if (w == 0 || h == 0 || n < 0 || w > MAX_MASK_WIDTH || h > MAX_MASK_HEIGHT) throw CorruptMaskException("bad header ${w}x$h/$n")
    // Deflate never grows data by more than a few bytes per 16 KB block, so a
    // payload longer than the raw pixels is a damaged length, checked before
    // anything is allocated for it.
    if (n > w.toLong() * h + w.toLong() * h / 1000 + 1024) throw CorruptMaskException("bad payload length $n for ${w}x$h")
    val payload = ByteArray(n).also { d.readFully(it) }
    Triple(w, h, payload)
} catch (e: IOException) {
    throw CorruptMaskException("short mask file", e)
}

private fun alphaBitmap(w: Int, h: Int, alpha: ByteArray): Bitmap {
    val bmp = createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val row = bmp.rowBytes
    val buf = if (row == w) {
        ByteBuffer.wrap(alpha)
    } else {
        ByteBuffer.allocate(row * h).also { b -> for (y in 0 until h) b.put(alpha, y * w, w).position((y + 1) * row) }.rewind()
    }
    bmp.copyPixelsFromBuffer(buf)
    return bmp
}

/**
 * The 16-level mask bytes deflated as they are: decoding is one native zlib
 * call straight into the bitmap's bytes, with no per-pixel Kotlin loop.
 *
 * Chosen by measurement (Task 10): about 130 KB a page and the fastest decode
 * of the three candidates (nibble-packed deflate, lossless WebP, this).
 */
object RawDeflateMaskCodec : MaskCodec {
    override val ext = "qtr"

    override fun encode(m: PageMask, out: OutputStream) {
        val bytes = ByteArrayOutputStream(m.alpha.size / 8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        DeflaterOutputStream(bytes, deflater, 64 * 1024).use { it.write(m.alpha) }
        deflater.end()
        writeFile(out, m, bytes.toByteArray())
    }

    /** The mask bytes, without making a bitmap. */
    fun decodeMask(input: InputStream): PageMask {
        val (w, h, payload) = readFile(input)
        val alpha = ByteArray(w * h)
        val inflater = Inflater()
        try {
            inflater.setInput(payload)
            var n = 0
            while (n < alpha.size) {
                val got = inflater.inflate(alpha, n, alpha.size - n)
                if (got == 0 && (inflater.finished() || inflater.needsInput())) break
                n += got
            }
            if (n != alpha.size) throw CorruptMaskException("short mask data: $n of ${alpha.size}")
        } catch (e: java.util.zip.DataFormatException) {
            throw CorruptMaskException("damaged mask data", e)
        } finally {
            inflater.end()
        }
        return PageMask(w, h, alpha)
    }

    override fun decode(input: InputStream): Bitmap = decodeMask(input).let { alphaBitmap(it.width, it.height, it.alpha) }
}
