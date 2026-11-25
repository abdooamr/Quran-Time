package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pinch zoom for a page, up to 4×. Once zoom settles above 1.25× the page is
 * drawn again from its SVG at 2.5× ([loadHiRes]), so letters stay sharp; the
 * sharp copy is dropped when zoom returns to 1×. Offline with no SVG at hand,
 * the scaled mask keeps showing.
 */
class ZoomState(private val scope: CoroutineScope, private val loadHiRes: suspend () -> ImageBitmap?) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var hiRes by mutableStateOf<ImageBitmap?>(null)
        private set
    private var job: Job? = null

    fun onTransform(zoomChange: Float, pan: Offset, size: Size = Size.Unspecified) {
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        job?.cancel()
        if (scale <= 1f) {
            offset = Offset.Zero
            hiRes = null
            return
        }
        val o = offset + pan
        offset = if (size != Size.Unspecified) {
            val maxX = (scale - 1) * size.width / 2
            val maxY = (scale - 1) * size.height / 2
            Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        } else {
            o
        }
        if (scale > 1.25f && hiRes == null) {
            job = scope.launch {
                delay(150)
                hiRes = loadHiRes()
            }
        }
    }
}

/**
 * Two-finger pinch zooms; once zoomed, one finger pans. A single finger at
 * 1× is left alone, so the page swipe keeps working.
 */
fun Modifier.zoomGestures(zoom: ZoomState): Modifier = pointerInput(zoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2 || zoom.scale > 1f) {
                zoom.onTransform(event.calculateZoom(), event.calculatePan(), Size(size.width.toFloat(), size.height.toFloat()))
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
