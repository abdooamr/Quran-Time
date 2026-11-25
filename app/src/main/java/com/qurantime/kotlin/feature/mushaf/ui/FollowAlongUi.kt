package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import com.qurantime.kotlin.core.ui.LocalSnackbar
import com.qurantime.kotlin.core.ui.say
import com.qurantime.kotlin.feature.mushaf.AyahShape
import com.qurantime.kotlin.feature.mushaf.ayahAt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The ayah a page's highlight shows (for tests and accessibility tools). */
val HighlightedAyah = SemanticsPropertyKey<Int>("HighlightedAyah")
private var SemanticsPropertyReceiver.highlightedAyah by HighlightedAyah

/** The recited ayah's outline, filled with the theme's highlight, in viewBox units × [scale]. */
@Composable
fun BoxScope.HighlightOverlay(page: Int, ayah: Int, polygon: List<Offset>, scale: Float) {
    val color = LocalMushafPalette.current.highlight
    val path = remember(polygon, scale) {
        Path().apply {
            polygon.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * scale, p.y * scale) else lineTo(p.x * scale, p.y * scale) }
            close()
        }
    }
    Canvas(Modifier.fillMaxSize().testTag("mushaf-highlight-$page").semantics { highlightedAyah = ayah }) {
        drawPath(path, color)
    }
}

/** A page with the recited ayah highlighted when it is on this page; taps on an ayah call [onAyah]. */
@Composable
fun HighlightedPage(page: Int, onAyah: (AyahShape) -> Unit) {
    val c = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val error = stringResource(R.string.error_generic)
    // Only this page's highlight is read here, so other pages don't redraw when the ayah changes.
    val highlight by remember(page) {
        c.follow.state.map { f -> f.current?.takeIf { f.active && it.page == page && it.polygon != null } }
    }.collectAsStateWithLifecycle(null)
    MushafPageView(
        page = page,
        overlays = { scale -> highlight?.let { HighlightOverlay(page, it.ayah, it.polygon!!, scale) } },
        onTapViewBox = { point ->
            scope.launch {
                val shapes = try {
                    c.geometry.forPage(page)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    snackbar.say(error)
                    return@launch
                }
                ayahAt(point, shapes)?.let(onAyah)
            }
        },
    )
}

/**
 * Turns to the page being recited, unless the reader swiped away (paused) or
 * switched auto-follow off. A new recitation resumes following.
 */
@Composable
fun FollowEffect() {
    val c = LocalAppContainer.current
    LaunchedEffect(Unit) {
        var wasFollowing = false
        combine(c.follow.state, c.settings.state.map { it.autoFollow }) { f, auto -> f to auto }.collect { (f, auto) ->
            if (f.active && !wasFollowing) c.mushaf.resumeFollow()
            wasFollowing = f.active
            val page = f.currentPage ?: return@collect
            if (!f.active || c.mushaf.followPaused.value || !auto) return@collect
            if (page != c.mushaf.currentPage.value) c.mushaf.goTo(page)
        }
    }
}

/** Shown after the reader swipes away while a recitation is following. */
@Composable
fun BoxScope.BackToRecitation() {
    val c = LocalAppContainer.current
    val f by c.follow.state.collectAsStateWithLifecycle()
    val paused by c.mushaf.followPaused.collectAsStateWithLifecycle()
    val current by c.mushaf.currentPage.collectAsStateWithLifecycle()
    val page = f.currentPage
    AnimatedVisibility(
        visible = f.active && paused && page != null && page != current,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
    ) {
        FilledTonalButton(
            onClick = {
                c.mushaf.resumeFollow()
                page?.let { c.mushaf.goTo(it) }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back_to_recitation))
        }
    }
}
