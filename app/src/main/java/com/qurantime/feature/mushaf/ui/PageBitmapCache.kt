package com.qurantime.feature.mushaf.ui

import android.graphics.Bitmap

/**
 * Decoded page masks (`ALPHA_8`, about 1.9 MB each at 1080 px) for the page
 * on screen and its neighbours. Cleared when Android asks for memory back.
 */
class PageBitmapCache(private val maxEntries: Int = 5) {
    private val map = object : LinkedHashMap<Pair<Int, Int>, Bitmap>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, Bitmap>): Boolean = size > maxEntries
    }

    @Synchronized fun get(page: Int, bucket: Int): Bitmap? = map[page to bucket]

    /** Any decoded width of [page] (for example the one decoded at start-up), to show while the exact one loads. */
    @Synchronized fun anyFor(page: Int): Pair<Int, Bitmap>? = map.entries.firstOrNull { it.key.first == page }?.let { it.key.second to it.value }

    @Synchronized fun put(page: Int, bucket: Int, bitmap: Bitmap) {
        map[page to bucket] = bitmap
    }

    /** Drops every bitmap; pages on screen decode again from disk. */
    @Synchronized fun clear() = map.clear()
}
