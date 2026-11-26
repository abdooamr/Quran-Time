package com.qurantime.support

import android.content.Context
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.qurantime.feature.video.VideoPlayerFactory

/** Port of fake_video.dart: records each player made; [fail] makes the next ones fail to start. */
class FakeVideoPlayerFactory : VideoPlayerFactory {
    var fail = false
    val created = mutableListOf<Pair<String, Boolean>>()

    override fun create(context: Context, url: String, live: Boolean): Player {
        created += url to live
        return FakeVideoPlayer(fail)
    }
}

/** A player with no video output: ready and playing, or failed. */
class FakeVideoPlayer(private val failed: Boolean) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var playWhenReady = false
    var released = false

    override fun getState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_RELEASE).build())
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(if (failed || released) Player.STATE_IDLE else Player.STATE_READY)
        .setPlayerError(if (failed) PlaybackException("no stream", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) else null)
        .setPlaylist(listOf(MediaItemData.Builder("video").build()))
        .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        return Futures.immediateVoidFuture()
    }
}
