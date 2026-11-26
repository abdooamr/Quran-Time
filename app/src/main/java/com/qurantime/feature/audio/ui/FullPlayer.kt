package com.qurantime.feature.audio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SliderDefaults
import com.qurantime.feature.mushaf.ui.rememberReduceMotion
import com.qurantime.feature.mushaf.ui.giltRing
import com.qurantime.feature.mushaf.ui.Roundel
import com.qurantime.feature.mushaf.ui.PlaySeal
import com.qurantime.feature.mushaf.ui.HighlightedPage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.l10n.localDigits
import com.qurantime.core.theme.LocalMushafPalette
import com.qurantime.feature.audio.PlayerController
import com.qurantime.feature.audio.PlayerUiState
import com.qurantime.feature.audio.RepeatMode
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.audio.subtitle
import com.qurantime.feature.audio.title
import com.qurantime.feature.mushaf.ui.SurahCartouche
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-screen player. While a timed recitation plays, the page being
 * recited is its centrepiece, with the reading sweep on it (tap it to read
 * there); otherwise the surah's number in a large roundel. [extras] adds
 * controls after sleep (the ayah repeat button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(onClose: () -> Unit, onOpenPage: (Int) -> Unit = {}, extras: @Composable () -> Unit = {}) {
    val c = LocalAppContainer.current
    val player = c.playerController
    val s by player.state.collectAsStateWithLifecycle()
    val follow by c.follow.state.collectAsStateWithLifecycle()
    val palette = LocalMushafPalette.current
    Scaffold(
        modifier = Modifier.testTag("full-player"),
        containerColor = palette.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = palette.background),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ExpandMore, stringResource(R.string.close)) } },
                title = { Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        },
    ) { padding ->
        val item = s.item ?: return@Scaffold
        val page = follow.currentPage?.takeIf { follow.active } ?: 0
        val reduceMotion = rememberReduceMotion()
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Pages turn here as they do in the Mushaf: one hands over to the next.
                Crossfade(targetState = page, animationSpec = tween(if (reduceMotion) 0 else 320), label = "page") { pg ->
                    if (pg > 0) {
                        HighlightedPage(pg, preview = true, onAyah = { onOpenPage(pg) })
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Medallion(item) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SurahCartouche(name = item.title, onTap = null, onTapLabel = null)
            Spacer(Modifier.height(10.dp))
            if (s.error != null) {
                Text(stringResource(R.string.playback_error), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
            }
            item.subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            (item as? SurahTrack)?.moshafName?.takeIf { it.isNotEmpty() }?.let {
                Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.isLive) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.live_badge)) },
                    leadingIcon = { Icon(Icons.Rounded.Circle, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.error) },
                )
            }
            Spacer(Modifier.height(18.dp))
            if (!s.isLive) Seek(player, s.durationMs)
            Spacer(Modifier.height(6.dp))
            Transport(player, s)
            Spacer(Modifier.height(14.dp))
            Extras(player, s, extras)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Without a page to show: the surah's number in a large roundel, or a ring with the sound mark. */
@Composable
private fun Medallion(item: com.qurantime.feature.audio.PlayableItem) {
    val locale = LocalConfiguration.current.locales[0]
    if (item is SurahTrack) {
        val n = localDigits(item.surah, locale)
        Roundel(numeral = n, semanticLabel = n, size = 132.dp)
    } else {
        Box(Modifier.size(132.dp).giltRing(LocalMushafPalette.current.gilt), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The position along a gilt hairline: a slim green track and a small seal
 * for a thumb, left to right in every language (Material's media rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Seek(player: PlayerController, durationMs: Long?) {
    val locale = LocalConfiguration.current.locales[0]
    val palette = LocalMushafPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val live by player.positionMs.collectAsStateWithLifecycle(initialValue = player.currentPositionMs)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = (durationMs ?: 0L).coerceAtLeast(0L)
    val now = if (total > 0) live.coerceAtMost(total) else live
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            val value = dragging ?: if (total <= 0) 0f else now.toFloat()
            val max = if (total <= 0) 1f else total.toFloat()
            val clear = Color.Transparent
            // Material's slider keeps the touch handling and accessibility; its own
            // drawing is cleared and the gilt track and seal thumb drawn beneath it.
            Slider(
                value = value,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seek(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..max,
                enabled = total > 0,
                interactionSource = interaction,
                colors = SliderDefaults.colors(
                    thumbColor = clear, activeTrackColor = clear, inactiveTrackColor = clear,
                    disabledThumbColor = clear, disabledActiveTrackColor = clear, disabledInactiveTrackColor = clear,
                    activeTickColor = clear, inactiveTickColor = clear,
                ),
                modifier = Modifier.testTag("player-seek").drawBehind {
                    val y = size.height / 2
                    val inset = 9.dp.toPx()
                    val x = inset + (size.width - 2 * inset) * (value / max).coerceIn(0f, 1f)
                    drawLine(palette.gilt.copy(alpha = 0.55f), Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 0.8.dp.toPx())
                    drawLine(primary, Offset(inset, y), Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(palette.field, inset, Offset(x, y))
                    drawCircle(palette.gilt, inset - 0.7.dp.toPx(), Offset(x, y), style = Stroke(1.2.dp.toPx()))
                },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val clock = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                Text(formatClock(dragging?.toLong() ?: now, locale), style = clock, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatClock(total, locale), style = clock, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Transport(player: PlayerController, s: PlayerUiState) {
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (!s.isLive) {
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(28.dp))
            PlaySeal(
                playing = s.playing,
                onClick = { scope.launch { player.togglePlay() } },
                label = stringResource(if (s.playing) R.string.pause else R.string.play),
                size = 76.dp,
            )
            Spacer(Modifier.width(28.dp))
            if (!s.isLive) {
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Extras(player: PlayerController, s: PlayerUiState, extras: @Composable () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!s.isLive) {
            var open by remember { mutableStateOf(false) }
            Box {
                Pill(Icons.Rounded.Speed, speedLabel(s.speed), stringResource(R.string.playback_speed), selected = false) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (speed in PlayerController.SPEEDS) {
                        DropdownMenuItem(text = { Text(speedLabel(speed)) }, onClick = {
                            player.setSpeed(speed)
                            open = false
                        })
                    }
                }
            }
            val repeatLabel = stringResource(
                when (s.repeat) {
                    RepeatMode.ONE -> R.string.repeat_one
                    RepeatMode.ALL -> R.string.repeat_all
                    RepeatMode.OFF -> R.string.repeat_off
                },
            )
            IconToggleButton(checked = s.repeat != RepeatMode.OFF, onCheckedChange = { player.cycleRepeat() }) {
                Icon(if (s.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, repeatLabel)
            }
        }
        SleepPill(player, s, locale)
        if (!s.isLive) extras()
    }
}

@Composable
private fun SleepPill(player: PlayerController, s: PlayerUiState, locale: java.util.Locale) {
    var open by remember { mutableStateOf(false) }
    // The remaining time ticks once a second only while a timed sleep is set.
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s.sleep.endsAtMs) {
        while (s.sleep.endsAtMs != null) {
            left = player.sleep.remainingMs() ?: 0L
            delay(1000)
        }
    }
    val label = when {
        s.sleep.endOfTrack -> stringResource(R.string.sleep_end_of_surah)
        s.sleep.endsAtMs != null -> stringResource(R.string.sleep_left, formatClock(left, locale))
        else -> stringResource(R.string.sleep_timer)
    }
    Box {
        Pill(Icons.Outlined.Bedtime, label, stringResource(R.string.sleep_timer), selected = s.sleep.active) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in listOf(15, 30, 45, 60)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sleep_minutes, localDigits(m, locale))) }, onClick = {
                    player.sleep.start(m * 60_000L)
                    open = false
                })
            }
            if (!s.isLive) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sleep_end_of_surah)) }, onClick = {
                    player.sleep.startEndOfTrack()
                    open = false
                })
            }
            if (s.sleep.active) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sleep_off)) }, onClick = {
                    player.sleep.cancel()
                    open = false
                })
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surface.copy(alpha = 0f),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
