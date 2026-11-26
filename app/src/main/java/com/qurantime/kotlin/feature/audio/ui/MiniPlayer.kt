package com.qurantime.kotlin.feature.audio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import com.qurantime.kotlin.feature.audio.PlayerController
import com.qurantime.kotlin.feature.audio.SurahTrack
import com.qurantime.kotlin.feature.audio.subtitle
import com.qurantime.kotlin.feature.audio.title
import com.qurantime.kotlin.feature.mushaf.ui.PlaySeal
import com.qurantime.kotlin.feature.mushaf.ui.Roundel
import com.qurantime.kotlin.feature.mushaf.ui.giltRing
import kotlinx.coroutines.launch

/**
 * The recitation, docked above the tabs: a gilt hairline carrying the
 * progress, the surah's number in a roundel, its name in the book face, and
 * the play seal. Tapping it opens the full player.
 */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalAppContainer.current.playerController
    val s by player.state.collectAsStateWithLifecycle()
    val item = s.item ?: return
    val scope = rememberCoroutineScope()
    val palette = LocalMushafPalette.current
    val locale = LocalConfiguration.current.locales[0]
    Surface(color = palette.chrome) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("mini-player")) {
            ProgressRule(player, if (s.isLive) null else s.durationMs)
            Row(Modifier.height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(12.dp))
                if (item is SurahTrack) {
                    val n = localDigits(item.surah, locale)
                    Roundel(numeral = n, semanticLabel = n, size = 44.dp)
                } else {
                    Box(Modifier.size(44.dp).giltRing(palette.gilt), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold),
                    )
                    if (s.error != null) {
                        Text(
                            stringResource(R.string.playback_error),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        item.subtitle?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                PlaySeal(
                    playing = s.playing,
                    onClick = { scope.launch { player.togglePlay() } },
                    label = stringResource(if (s.playing) R.string.pause else R.string.play),
                    size = 44.dp,
                    modifier = Modifier.testTag("mini-play"),
                )
                if (!s.isLive) {
                    IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next)) }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/**
 * A gilt hairline along the top; while a recording plays, the green progress
 * runs along it, left to right in every language (Material's media rule). It
 * alone follows the position, so the bar doesn't redraw four times a second.
 */
@Composable
private fun ProgressRule(player: PlayerController, durationMs: Long?) {
    val palette = LocalMushafPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val now by player.positionMs.collectAsStateWithLifecycle(initialValue = player.currentPositionMs)
    val total = durationMs ?: 0L
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(palette.gilt.copy(alpha = 0.55f), Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 0.8.dp.toPx())
        if (total > 0) {
            val x = size.width * (now.toFloat() / total).coerceIn(0f, 1f)
            drawLine(primary, Offset(0f, size.height / 2), Offset(x, size.height / 2), strokeWidth = size.height)
        }
    }
}
