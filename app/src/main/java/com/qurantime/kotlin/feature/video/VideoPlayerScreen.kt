package com.qurantime.kotlin.feature.video

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R

/** Makes the player for a video; injected so tests can fake playback. */
fun interface VideoPlayerFactory {
    fun create(context: Context, url: String, live: Boolean): Player
}

/**
 * The app's video player: its own ExoPlayer (separate from the recitation
 * player, released when the screen closes), HLS for `.m3u8` live TV.
 */
val DefaultVideoPlayerFactory = VideoPlayerFactory { context, url, _ ->
    ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            val item = MediaItem.Builder().setUri(url)
            if (url.contains(".m3u8", ignoreCase = true)) item.setMimeType(MimeTypes.APPLICATION_M3U8)
            setMediaItem(item.build())
        }
}

/** Full-screen video for live TV, videos and tadabor clips; in landscape the system bars hide too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(url: String, title: String, live: Boolean, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val player = remember(attempt) { c.videoPlayers.create(context, url, live) }
    DisposableEffect(player) {
        failed = false
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        if (player.playerError != null) failed = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // No video in the background: pause when the app is hidden, go on when back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        var resume = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> {
                    resume = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resume) player.play()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ImmersiveWhen(landscape)
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (!landscape) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close)) } },
                    actions = { if (live) LiveChip() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black), contentAlignment = Alignment.Center) {
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.video_error), color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            keepScreenOn = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowShuffleButton(false)
                        }
                    },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveChip() {
    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.live_badge)) },
        leadingIcon = { Icon(Icons.Rounded.Circle, null, tint = Color(0xFFFF5252), modifier = Modifier.size(10.dp)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White),
        modifier = Modifier.padding(end = 12.dp),
    )
}

/** Hides the status and navigation bars while [on] (swipe from an edge shows them briefly). */
@Composable
private fun ImmersiveWhen(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (on && ctl != null) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
