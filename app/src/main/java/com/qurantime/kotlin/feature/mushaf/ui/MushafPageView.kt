package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import com.qurantime.kotlin.feature.mushaf.image.PageUnavailableException
import com.qurantime.kotlin.feature.mushaf.image.SvgRasterizer
import com.qurantime.kotlin.feature.mushaf.image.renderWidthBucket
import com.qurantime.kotlin.feature.mushaf.viewBoxForPage
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ink colour a page is drawn in (for tests and accessibility tools). */
val PageInk = SemanticsPropertyKey<Color>("PageInk")
private var SemanticsPropertyReceiver.pageInk by PageInk

/** A page decoded at start-up (any width) shows at once; the exact width replaces it when ready. */
private fun initialLoad(container: com.qurantime.kotlin.AppContainer, page: Int): PageLoad =
    container.pageBitmaps.anyFor(page)?.let { PageLoad.Ready(it.second.asImageBitmap()) } ?: PageLoad.Loading

private sealed interface PageLoad {
    data object Loading : PageLoad
    data object Offline : PageLoad
    data class Ready(val image: ImageBitmap) : PageLoad
}

/**
 * One Mushaf page: the page mask fitted to the space inside a gilt frame,
 * with the page number below. The mask is drawn tinted with the theme's ink.
 *
 * [overlays] get the scale from viewBox units to pixels, so timing polygons
 * line up exactly; [onTapViewBox] receives taps in viewBox units.
 */
@Composable
fun MushafPageView(
    page: Int,
    overlays: @Composable BoxScope.(scale: Float) -> Unit = {},
    onTapViewBox: ((Offset) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val box = viewBoxForPage(page)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    BoxWithConstraints(Modifier.fillMaxSize().testTag("mushaf-page-$page"), contentAlignment = Alignment.Center) {
        val frame = 16.dp // gilt frame gap + rule on both sides
        val folio = 52.dp // the page-number roundel under the page
        val scaleDp = min((maxWidth - frame).value / box.width, (maxHeight - frame - folio).value / box.height).coerceAtLeast(0.1f)
        val fittedW = (box.width * scaleDp).dp
        val fittedH = (box.height * scaleDp).dp
        val density = LocalDensity.current
        val shownPx = with(density) { fittedW.roundToPx() }.coerceAtLeast(1)
        // The image is made for the full available width, not the height-fitted
        // width: the mini player or a banner changing the height then doesn't
        // make every page convert again. Drawing scales it down slightly.
        val renderPx = with(density) { (maxWidth - frame).roundToPx() }.coerceAtLeast(shownPx)
        if (page > 2) SideEffect { container.mushaf.pageWidthPx = renderPx }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GiltFrame {
                Box(Modifier.size(fittedW, fittedH)) {
                    PageArt(page, renderPx, scale = shownPx / box.width, overlays, onTapViewBox)
                }
            }
            Box(Modifier.padding(top = 6.dp)) {
                val n = localDigits(page, locale)
                Roundel(numeral = n, semanticLabel = stringResource(R.string.page_number, n), size = 44.dp)
            }
        }
    }
}

@Composable
private fun BoxScope.PageArt(
    page: Int,
    widthPx: Int,
    scale: Float,
    overlays: @Composable BoxScope.(scale: Float) -> Unit,
    onTapViewBox: ((Offset) -> Unit)?,
) {
    val container = LocalAppContainer.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by produceState(initialLoad(container, page), page, widthPx, attempt) {
        val bucket = renderWidthBucket(widthPx)
        container.pageBitmaps.get(page, bucket)?.let {
            value = PageLoad.Ready(it.asImageBitmap())
            return@produceState
        }
        // Neighbouring pages wait until the opening page is on screen, so it gets the CPU first.
        if (page != container.mushaf.currentPage.value) container.mushaf.firstPageDrawn.first { it }
        value = try {
            val r = container.pages.mask(page, widthPx)
            val bmp = withContext(Dispatchers.Default) {
                r.file.inputStream().buffered().use { container.maskCodec.decode(it) }.also {
                    // Upload to the GPU now, on the render thread, not in the first frame that shows it.
                    it.prepareToDraw()
                }
            }
            container.pageBitmaps.put(page, r.bucket, bmp)
            if (!r.exact && container.online.online.value) {
                // Made for another screen size: show it scaled now, re-make it for this one.
                launch {
                    runCatching { container.pages.remake(page, widthPx) }.onSuccess { attempt++ }
                }
            }
            PageLoad.Ready(bmp.asImageBitmap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageLoad.Offline
        }
    }
    when (val l = load) {
        PageLoad.Loading -> DelayedSpinner()
        PageLoad.Offline -> OfflinePage(page) { attempt++ }
        is PageLoad.Ready -> {
            val ink = LocalMushafPalette.current.ink
            val tint = remember(ink) { ColorFilter.tint(ink, BlendMode.SrcIn) }
            val scope = rememberCoroutineScope()
            val zoom = remember(page, widthPx) {
                ZoomState(scope) {
                    val svg = container.pages.svgFor(page) ?: return@ZoomState null
                    withContext(Dispatchers.Default) {
                        GZIPInputStream(svg.inputStream().buffered()).use {
                            SvgRasterizer.renderBitmap(it, viewBoxForPage(page), (widthPx * 2.5f).roundToInt()).asImageBitmap()
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .zoomGestures(zoom)
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
            ) {
                val image = zoom.hiRes ?: l.image
                LaunchedEffect(Unit) { container.mushaf.onPageDrawn(page) }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag("mushaf-page-art-$page")
                        .semantics { pageInk = ink }
                        .then(
                            if (onTapViewBox != null) {
                                Modifier.pointerInput(scale) { detectTapGestures { onTapViewBox(it / scale) } }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), colorFilter = tint)
                }
                overlays(scale)
            }
        }
    }
}

@Composable
private fun OfflinePage(page: Int, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("mushaf-page-offline-$page"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.page_offline), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Shows a small spinner only if loading takes longer than a moment. */
@Composable
private fun DelayedSpinner() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        show = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (show) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}
