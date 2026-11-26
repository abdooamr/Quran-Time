package com.qurantime.kotlin.feature.audio

import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val EXTRA_ITEM_JSON = "qt.item"
const val EXTRA_REMOTE_URL = "qt.remoteUrl"
const val EXTRA_IS_LIVE = "qt.isLive"

/**
 * The media-session item for this item: the title and artist the lock screen
 * and notification show. A [localPath] (a downloaded file) is played instead
 * of the network URL; the extras always keep the URL and the item itself.
 */
fun PlayableItem.toMediaItem(localPath: String? = null): MediaItem {
    val uri = localPath?.let { "file://$it" } ?: url
    val extras = Bundle().apply {
        putString(EXTRA_ITEM_JSON, toJson().toString())
        putString(EXTRA_REMOTE_URL, url)
        putBoolean(EXTRA_IS_LIVE, this@toMediaItem is RadioStream)
    }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(subtitle)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .apply {
            when (val item = this@toMediaItem) {
                is SurahTrack -> setAlbumTitle(item.moshafName)
                is AudioClip -> item.artUrl?.let { setArtworkUri(it.toUri()) }
                is RadioStream -> {}
            }
        }
        .build()
    return MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(meta).build()
}

/** The media item for a queue entry (its uri is a `file://` path when downloaded). */
fun QueueSource.toMediaItem(): MediaItem =
    item.toMediaItem(localPath = uri.takeIf { it.startsWith("file://") }?.removePrefix("file://"))

fun MediaItem.isLive(): Boolean = mediaMetadata.extras?.getBoolean(EXTRA_IS_LIVE) == true

/** Recovers the item a media item was made from, or null if it isn't ours. */
fun MediaItem.toPlayable(): PlayableItem? = try {
    mediaMetadata.extras?.getString(EXTRA_ITEM_JSON)?.let { PlayableItem.fromJson(Json.parseToJsonElement(it).jsonObject) }
} catch (_: Exception) {
    null
}
