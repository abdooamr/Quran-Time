package com.qurantime.kotlin.feature.audio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import com.qurantime.kotlin.feature.audio.PlayerController
import com.qurantime.kotlin.feature.audio.subtitle
import com.qurantime.kotlin.feature.audio.title
import kotlinx.coroutines.launch

/** The bar above the tabs while something is loaded; tapping it opens the full player. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalAppContainer.current.playerController
    val s by player.state.collectAsStateWithLifecycle()
    val item = s.item ?: return
    val scope = rememberCoroutineScope()
    Surface(color = LocalMushafPalette.current.chrome) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("mini-player")) {
            if (!s.isLive) Progress(player, s.durationMs)
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(16.dp))
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
                IconButton(onClick = { scope.launch { player.togglePlay() } }) {
                    Icon(
                        if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (s.playing) R.string.pause else R.string.play),
                        modifier = Modifier.width(32.dp),
                    )
                }
                if (!s.isLive) {
                    IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next)) }
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** A thin progress line; it alone follows the position, so the bar doesn't redraw four times a second. */
@Composable
private fun Progress(player: PlayerController, durationMs: Long?) {
    val now by player.positionMs.collectAsStateWithLifecycle(initialValue = player.currentPositionMs)
    val total = durationMs ?: 0L
    val value = if (total <= 0) 0f else (now.toFloat() / total).coerceIn(0f, 1f)
    // Playback progress runs left-to-right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}
