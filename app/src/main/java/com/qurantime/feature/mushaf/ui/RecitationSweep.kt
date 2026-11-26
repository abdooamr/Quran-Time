package com.qurantime.feature.mushaf.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One text line of an ayah's outline, in viewBox units. */
typealias AyahLine = Rect

/** A filled part of the sweep: a whole line, or the [leading] part of the line being recited. */
data class SweepSegment(val rect: Rect, val leading: Boolean)

/**
 * The text lines an ayah's outline covers, top to bottom. Outlines are
 * stair-steps of whole lines whose y values differ by fractions where lines
 * meet (82.11 / 82.63); those are merged into one boundary so lines touch.
 */
fun ayahLines(polygon: List<Offset>): List<AyahLine> {
    if (polygon.size < 3) return emptyList()
    val ys = polygon.map { it.y }.distinct().sorted()
    val boundaries = mutableListOf<MutableList<Float>>()
    for (y in ys) {
        val last = boundaries.lastOrNull()
        if (last != null && y - last.last() < MERGE_UNITS) last += y else boundaries += mutableListOf(y)
    }
    val cuts = boundaries.map { it.average().toFloat() }
    val lines = mutableListOf<AyahLine>()
    for (i in 0 until cuts.lastIndex) {
        val top = cuts[i]
        val bottom = cuts[i + 1]
        val mid = (top + bottom) / 2
        val xs = crossings(polygon, mid)
        if (xs.size >= 2) lines += Rect(xs.min(), top, xs.max(), bottom)
    }
    return lines
}

/** Where the outline's edges cross the horizontal line at [y]. */
private fun crossings(polygon: List<Offset>, y: Float): List<Float> {
    val out = mutableListOf<Float>()
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
            out += a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
        }
    }
    return out
}

/**
 * The part of [lines] recited at [progress] (0..1 of the ayah's time),
 * measured along the text: each line is read right to left, top to bottom.
 */
fun sweepSegments(lines: List<AyahLine>, progress: Float): List<SweepSegment> {
    val total = lines.sumOf { it.width.toDouble() }.toFloat()
    var left = progress.coerceIn(0f, 1f) * total
    val out = mutableListOf<SweepSegment>()
    for (line in lines) {
        if (left <= 0f) break
        if (left >= line.width) {
            out += SweepSegment(line, leading = false)
            left -= line.width
        } else {
            out += SweepSegment(Rect(line.right - left, line.top, line.right, line.bottom), leading = true)
            left = 0f
        }
    }
    return out
}

private const val MERGE_UNITS = 2f

/**
 * How far the ayah [startMs]..[endMs] has been recited, 0..1, gliding between
 * the player's position ticks (four a second) so the sweep moves smoothly.
 */
@Composable
fun rememberAyahProgress(positions: kotlinx.coroutines.flow.Flow<Long>, nowMs: () -> Long, startMs: Long, endMs: Long): State<Float> {
    val span = (endMs - startMs).coerceAtLeast(1L).toFloat()
    fun of(pos: Long) = ((pos - startMs) / span).coerceIn(0f, 1f)
    val progress = remember(startMs, endMs) { Animatable(of(nowMs())) }
    LaunchedEffect(progress) {
        positions.collect { pos ->
            val target = of(pos)
            // Small steps glide over one tick; a seek (a big jump) moves at once.
            if (abs(target - progress.value) * span > TICK_MS * 4) progress.snapTo(target)
            else progress.animateTo(target, tween(TICK_MS.toInt(), easing = LinearEasing))
        }
    }
    return progress.asState()
}

private const val TICK_MS = 260L

/** Whether the system's animations are off (Remove animations / animator scale 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** The outline with its corners rounded, scaled to pixels. */
fun roundedOutline(polygon: List<Offset>, scale: Float, radiusPx: Float): Path {
    val src = android.graphics.Path().apply {
        polygon.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * scale, p.y * scale) else lineTo(p.x * scale, p.y * scale) }
        close()
    }
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        pathEffect = android.graphics.CornerPathEffect(radiusPx)
    }
    val dst = android.graphics.Path()
    @Suppress("DEPRECATION")
    paint.getFillPath(src, dst)
    return dst.asComposePath()
}

/** Corner radius for an ayah's wash: a fifth of a text line, so the wash reads as soft ink, not a box. */
fun washRadius(lines: List<AyahLine>, scale: Float): Float {
    val lineHeight = lines.map { it.height }.sorted().let { if (it.isEmpty()) 36f else it[it.size / 2] }
    return lineHeight * 0.2f * scale
}

/**
 * Draws the recitation wash: the whole ayah faintly, and the recited part at
 * full strength with a feathered leading edge.
 */
fun DrawScope.drawRecitationWash(outline: Path, lines: List<AyahLine>, scale: Float, progress: Float, color: Color) {
    drawPath(outline, color.copy(alpha = color.alpha * UPCOMING))
    val segments = sweepSegments(lines, progress)
    if (segments.isEmpty()) return
    clipPath(outline) {
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            for (s in segments) {
                val r = s.rect
                if (!s.leading) {
                    drawRect(color, r.topLeft, r.size)
                } else {
                    // A soft leading edge about one word wide, so the fill reads as ink spreading.
                    val feather = min(r.width, r.height * 0.9f)
                    drawRect(color, Offset(r.left + feather, r.top), androidx.compose.ui.geometry.Size(max(0f, r.width - feather), r.height))
                    drawRect(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color), startX = r.left, endX = r.left + feather),
                        Offset(r.left, r.top),
                        androidx.compose.ui.geometry.Size(feather, r.height),
                    )
                }
            }
        }
    }
}

/** How strongly the not-yet-recited part of the ayah is washed, relative to the recited part. */
private const val UPCOMING = 0.38f

/** The tapped ayah while its actions are open: a faint wash and a gilt hairline around it. */
fun DrawScope.drawSelection(outline: Path, wash: Color, gilt: Color) {
    drawPath(outline, wash.copy(alpha = wash.alpha * 0.45f))
    drawPath(outline, gilt, style = Stroke(1.2.dp.toPx()))
}
