package com.qurantime.kotlin.feature.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.qurantime.kotlin.MainActivity
import com.qurantime.kotlin.QuranTimeApp
import com.qurantime.kotlin.R

/**
 * Keeps recitations playing with the screen off and shows the lock-screen,
 * Bluetooth and notification controls. The player belongs to the app; the
 * service only puts a media session on it.
 */
// Media3 marks the session customisation used here as unstable API.
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val c = (application as QuranTimeApp).container
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_stat_quran) },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, SessionPlayer(c.player, c.queue, onStop = c::stopPlayback))
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops the service unless something is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * The player as the notification, lock screen and headsets see it. Skips go
 * through [QueueHandler] so they never count as a surah finishing (the
 * end-of-surah sleep timer and range repeat rely on that); Stop goes through
 * the app so it also cancels the sleep timer and any repeat. Radio offers no
 * seeking or skipping.
 */
// Media3 marks the session customisation used here as unstable API.
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SessionPlayer(
    player: Player,
    private val queue: QueueHandler,
    private val onStop: () -> Unit,
) : ForwardingPlayer(player) {
    private val liveBlocked = listOf(
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
    )

    private val live get() = queue.state.value.isLive

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        return if (live) commands.buildUpon().removeAll(*liveBlocked.toIntArray()).build() else commands
    }

    override fun isCommandAvailable(command: Int): Boolean =
        if (live && command in liveBlocked) false else super.isCommandAvailable(command)

    override fun seekToNext() = queue.skipToNext()
    override fun seekToNextMediaItem() = queue.skipToNext()
    override fun seekToPrevious() = queue.skipToPrevious()
    override fun seekToPreviousMediaItem() = queue.skipToPrevious()
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = queue.seekToItem(positionMs, mediaItemIndex)
    override fun stop() = onStop()
}
