package com.qurantime.feature.mushaf.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import com.qurantime.feature.timing.AyahTiming
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
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
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.theme.LocalMushafPalette
import com.qurantime.core.ui.LocalSnackbar
import com.qurantime.core.ui.say
import com.qurantime.feature.mushaf.AyahShape
import com.qurantime.feature.mushaf.ayahAt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The ayah a page's highlight shows (for tests and accessibility tools). */
val HighlightedAyah = SemanticsPropertyKey<Int>("HighlightedAyah")
private var SemanticsPropertyReceiver.highlightedAyah by HighlightedAyah

/**
 * The recited ayah: a faint wash over the whole ayah, filled at full strength
 * right to left, line by line, as it is recited (the reading sweep). With
 * animations off, the whole ayah is lit at once.
 */
@Composable
fun BoxScope.HighlightOverlay(page: Int, timing: AyahTiming, scale: Float) {
    val c = LocalAppContainer.current
    val color = LocalMushafPalette.current.highlight
    val polygon = timing.polygon ?: return
    val lines = remember(polygon) { ayahLines(polygon) }
    val outline = remember(polygon, scale) { roundedOutline(polygon, scale, washRadius(lines, scale)) }
    val reduceMotion = rememberReduceMotion()
    val progress = rememberAyahProgress(c.playerController.positionMs, { c.playerController.currentPositionMs }, timing.startMs, timing.endMs)
    Canvas(Modifier.fillMaxSize().testTag("mushaf-highlight-$page").semantics { highlightedAyah = timing.ayah }) {
        // Progress is read here, while drawing: only this layer redraws as it moves.
        drawRecitationWash(outline, lines, scale, if (reduceMotion) 1f else progress.value, color)
    }
}

/** The tapped ayah, marked while its actions are open. */
@Composable
fun BoxScope.SelectionOverlay(page: Int, shape: AyahShape, scale: Float) {
    val palette = LocalMushafPalette.current
    val outline = remember(shape.polygon, scale) {
        roundedOutline(shape.polygon, scale, washRadius(ayahLines(shape.polygon), scale))
    }
    Canvas(Modifier.fillMaxSize().testTag("mushaf-selection-$page")) {
        drawSelection(outline, palette.highlight, palette.gilt)
    }
}

/**
 * A page with the recited ayah highlighted when it is on this page, and the
 * [selected] (tapped) ayah marked; taps on an ayah call [onAyah].
 */
@Composable
fun HighlightedPage(page: Int, selected: AyahShape? = null, preview: Boolean = false, onAyah: (AyahShape) -> Unit) {
    val c = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val error = stringResource(R.string.error_generic)
    val reduceMotion = rememberReduceMotion()
    // Only this page's highlight is read here, so other pages don't redraw when the ayah changes.
    val highlight by remember(page) {
        c.follow.state.map { f -> f.current?.takeIf { f.active && it.page == page && it.polygon != null } }
    }.collectAsStateWithLifecycle(null)
    MushafPageView(
        page = page,
        preview = preview,
        overlays = { scale ->
            // One ayah hands over to the next: the old wash dissolves as the new one appears.
            Crossfade(targetState = highlight, animationSpec = tween(if (reduceMotion) 0 else 260), label = "ayah") { h ->
                Box(Modifier.fillMaxSize()) { h?.let { HighlightOverlay(page, it, scale) } }
            }
            selected?.takeIf { it.page == page }?.let { SelectionOverlay(page, it, scale) }
        },
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
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = f.active && paused && page != null && page != current,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(),
        // In the folio band under the page frame: it may cover the page number for a moment, never the text.
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
    ) {
        // A chrome pill with a gilt hairline: the book's apparatus, floating over the page's ground.
        val palette = LocalMushafPalette.current
        Surface(
            onClick = {
                c.mushaf.resumeFollow()
                page?.let { c.mushaf.goTo(it) }
            },
            shape = CircleShape,
            color = palette.chrome,
            border = BorderStroke(0.8.dp, palette.gilt),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.back_to_recitation), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
