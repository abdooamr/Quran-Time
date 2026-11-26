package com.qurantime.kotlin.feature.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class Media3BackendTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun tearDown() = scope.cancel()

    private fun playing(): androidx.media3.exoplayer.ExoPlayer = buildPlayer(context).apply {
        setMediaSource(FakeMediaSource())
        prepare()
        play()
        TestPlayerRunHelper.run(this).untilState(Player.STATE_READY)
    }

    @Test
    fun `buildPlayer handles audio focus and becoming noisy`() {
        val p = playing()
        assertEquals(C.USAGE_MEDIA, p.audioAttributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_SPEECH, p.audioAttributes.contentType)

        // Losing audio focus (a phone call) pauses and keeps the position.
        val am = context.getSystemService(AudioManager::class.java)
        shadowOf(am).lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()
        TestPlayerRunHelper.run(p).untilPendingCommandsAreFullyHandled()
        assertFalse(p.playWhenReady)

        // Unplugging headphones pauses too.
        p.play()
        ShadowLooper.idleMainLooper()
        TestPlayerRunHelper.run(p).untilPendingCommandsAreFullyHandled()
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        ShadowLooper.idleMainLooper()
        TestPlayerRunHelper.run(p).untilPendingCommandsAreFullyHandled()
        assertFalse(p.playWhenReady)
        p.release()
    }

    @Test
    fun `index and completion flows follow the player`() {
        // Media3's test player runs on a fake clock, so playing to the end is instant.
        val p = androidx.media3.test.utils.TestExoPlayerBuilder(context).build()
        val backend = Media3Backend(p, scope)
        p.setMediaSources(listOf(FakeMediaSource(), FakeMediaSource()))
        p.prepare()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
        ShadowLooper.idleMainLooper()
        assertEquals(0, backend.currentIndex.value)
        backend.seek(0, index = 1)
        TestPlayerRunHelper.run(p).untilPendingCommandsAreFullyHandled()
        ShadowLooper.idleMainLooper()
        assertEquals(1, backend.currentIndex.value)
        p.play()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_ENDED)
        ShadowLooper.idleMainLooper()
        assertEquals(Processing.COMPLETED, backend.state.value.processing)
        p.release()
    }
    // Review C1: ExoPlayer is idle after stop() or an error and plays nothing
    // until prepare(); Flutter's player reloaded the source on play.
    private fun preparedPlayer(): Pair<androidx.media3.exoplayer.ExoPlayer, Media3Backend> {
        val p = androidx.media3.test.utils.TestExoPlayerBuilder(context).build()
        p.setMediaSources(listOf(FakeMediaSource(), FakeMediaSource()))
        p.prepare()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
        return p to Media3Backend(p, scope)
    }

    @Test
    fun `play after stop prepares again and plays`() {
        val (p, backend) = preparedPlayer()
        backend.stop()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_IDLE)
        backend.play()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
        assertTrue(p.isPlaying)
        p.release()
    }

    // Found on the A50: after stop() (opening a TV channel) the mini player
    // showed Pause over a silent, idle player, so the first tap paused it.
    // stop() keeps ExoPlayer's playWhenReady; an idle player is not playing.
    @Test
    fun `after stop the player is reported as not playing`() {
        val (p, backend) = preparedPlayer()
        backend.play()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
        ShadowLooper.idleMainLooper()
        assertTrue(backend.state.value.playing)
        backend.stop()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_IDLE)
        ShadowLooper.idleMainLooper()
        assertFalse(backend.state.value.playing)
        p.release()
    }

    @Test
    fun `seeking to the next item after an error prepares again`() {
        val (p, backend) = preparedPlayer()
        backend.stop() // the player is idle, as after a PlaybackException
        TestPlayerRunHelper.run(p).untilState(Player.STATE_IDLE)
        backend.seek(0, index = 1)
        backend.play()
        TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
        assertEquals(1, p.currentMediaItemIndex)
        assertTrue(p.isPlaying)
        p.release()
    }
}

/** Ports of the MediaItem cases of the Flutter playable_item_test.dart. */
@RunWith(AndroidJUnit4::class)
class MediaItemsTest {
    private val track = SurahTrack(
        reciterId = 5, reciterName = "Ahmad Al-Ajmy", moshafId = 5,
        moshafName = "Rewayat Hafs A'n Assem - Murattal",
        server = "https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/", surah = 2, surahName = "Al-Baqarah",
    )

    @Test
    fun `surah track maps to a media item`() {
        val m = track.toMediaItem()
        assertEquals("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/002.mp3", m.mediaId)
        assertEquals("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/002.mp3", m.localConfiguration!!.uri.toString())
        assertEquals("Al-Baqarah", m.mediaMetadata.title)
        assertEquals("Ahmad Al-Ajmy", m.mediaMetadata.artist)
        assertEquals("Rewayat Hafs A'n Assem - Murattal", m.mediaMetadata.albumTitle)
        assertEquals(track.url, m.mediaMetadata.extras!!.getString(EXTRA_REMOTE_URL))
        assertFalse(m.isLive())
    }

    @Test
    fun `a downloaded file plays from disk but keeps its remote url`() {
        val m = track.toMediaItem(localPath = "/x/002.mp3")
        assertEquals("file:///x/002.mp3", m.localConfiguration!!.uri.toString())
        assertEquals("https://cdn.mp3quran.net/audio/ahmad-ajmi/r1/002.mp3", m.mediaMetadata.extras!!.getString(EXTRA_REMOTE_URL))
    }

    @Test
    fun `radio is live with no duration`() {
        val radio = RadioStream(id = 10, name = "Radio Alzain", url = "https://qurango.net/radio/alzain")
        val m = radio.toMediaItem()
        assertEquals(radio.url, m.mediaId)
        assertEquals("Radio Alzain", m.mediaMetadata.title)
        assertTrue(m.isLive())
        assertNull(m.mediaMetadata.durationMs)
    }

    @Test
    fun `clip keeps title, subtitle and art`() {
        val clip = AudioClip(id = "tafsir-316", title = "Surat An-Nas", subtitle = "Al-Tabari", url = "https://server17.mp3quran.net/tafseer/tabri/114.mp3", artUrl = "https://x/y.jpg")
        val m = clip.toMediaItem()
        assertEquals("Al-Tabari", m.mediaMetadata.artist)
        assertEquals("https://x/y.jpg", m.mediaMetadata.artworkUri.toString())
    }

    @Test
    fun `round trip through MediaItem`() {
        val radio = RadioStream(id = 10, name = "R", url = "https://r")
        val clip = AudioClip(id = "c", title = "t", subtitle = "s", url = "https://c")
        assertEquals(track, track.toMediaItem().toPlayable())
        assertEquals(track, track.toMediaItem(localPath = "/x/002.mp3").toPlayable())
        assertEquals(radio, radio.toMediaItem().toPlayable())
        assertEquals(clip, clip.toMediaItem().toPlayable())
    }
}
