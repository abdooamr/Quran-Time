package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The Madinah Mushaf Edition ornaments: gilt hairlines only, no fills except
// the green field of the surah cartouche.

private fun lozenge(r: Rect): Path {
    val tip = r.height / 2
    return Path().apply {
        moveTo(r.left + tip, r.top)
        lineTo(r.right - tip, r.top)
        lineTo(r.right, r.center.y)
        lineTo(r.right - tip, r.bottom)
        lineTo(r.left + tip, r.bottom)
        lineTo(r.left, r.center.y)
        close()
    }
}

private fun DrawScope.rosette(c: Offset, r: Float, gilt: Color) {
    val d = density
    for (i in 0 until 8) {
        val a = i * PI / 4
        drawCircle(gilt, 1.1f * d, c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()))
    }
    drawCircle(gilt, 1.4f * d, c)
}

private object LozengeShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(lozenge(Rect(Offset.Zero, size)))
}

/** The surah name on a green lozenge with gilt rules; tapping it opens the jump sheet. */
@Composable
fun SurahCartouche(name: String, onTap: (() -> Unit)?, onTapLabel: String?, modifier: Modifier = Modifier) {
    val p = LocalMushafPalette.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(LozengeShape)
            .then(if (onTap != null) Modifier.clickable(onClickLabel = onTapLabel, role = Role.Button, onClick = onTap) else Modifier)
            .drawBehind {
                val outer = Rect(Offset.Zero, size)
                drawPath(lozenge(outer), p.field)
                drawPath(lozenge(outer.deflate(1.dp.toPx())), p.gilt, style = Stroke(1.4.dp.toPx()))
                drawPath(lozenge(outer.deflate(4.5.dp.toPx())), p.gilt, style = Stroke(0.8.dp.toPx()))
                rosette(Offset(outer.left + 12.dp.toPx(), outer.center.y), 4.dp.toPx(), p.gilt)
                rosette(Offset(outer.right - 12.dp.toPx(), outer.center.y), 4.dp.toPx(), p.gilt)
            }
            .padding(PaddingValues(horizontal = 34.dp, vertical = 8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
            color = p.onField,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A gilt roundel holding a number (page or juz), with an optional caption above it. */
@Composable
fun Roundel(numeral: String, semanticLabel: String, caption: String? = null, size: Dp = 48.dp) {
    val p = LocalMushafPalette.current
    val color = MaterialTheme.colorScheme.onSurface
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    val dimension = size * scale
    Box(
        Modifier
            .size(dimension)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .drawBehind {
                val c = center
                val r = this.size.minDimension / 2
                drawCircle(p.gilt, r - 1.dp.toPx(), c, style = Stroke(1.3.dp.toPx()))
                drawCircle(p.gilt, r - 4.5.dp.toPx(), c, style = Stroke(0.7.dp.toPx()))
                for (i in 0 until 8) {
                    val a = i * PI / 4 - PI / 2
                    val rr = r - 1.dp.toPx()
                    drawCircle(p.gilt, 1.6.dp.toPx(), c + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (caption != null) {
                Text(caption, fontSize = maxOf(10f, size.value * 0.2f).sp, lineHeight = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
            val factor = if (caption != null) 0.34f else if (numeral.length > 2) 0.3f else 0.4f
            Text(
                numeral,
                fontFamily = BookFont,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * factor).sp,
                lineHeight = (size.value * factor * 1.15f).sp,
                color = color,
            )
        }
    }
}

/** A double gilt rule around the page, with small rosettes at the corners. */
@Composable
fun GiltFrame(modifier: Modifier = Modifier, gap: Dp = 6.dp, content: @Composable () -> Unit) {
    val gilt = LocalMushafPalette.current.gilt
    Box(
        modifier
            .drawBehind {
                val r = Rect(Offset.Zero, size)
                val outer = r.deflate(0.6.dp.toPx())
                drawRect(gilt, outer.topLeft, outer.size, style = Stroke(1.2.dp.toPx()))
                val inner = r.deflate(3.6.dp.toPx())
                drawRect(gilt, inner.topLeft, inner.size, style = Stroke(0.6.dp.toPx()))
                for (c in listOf(inner.topLeft, inner.topRight, inner.bottomLeft, inner.bottomRight)) rosette(c, 2.4.dp.toPx(), gilt)
            }
            .padding(gap + 2.dp),
    ) { content() }
}
