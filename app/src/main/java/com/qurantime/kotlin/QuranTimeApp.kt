package com.qurantime.kotlin

import android.app.Application
import android.content.ComponentCallbacks2

class QuranTimeApp : Application() {
    private val containerLazy = lazy { AppContainer(this) }

    /** Every dependency inside is lazy: building the container does no I/O. */
    val container: AppContainer by containerLazy

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Decoded pages come back from disk in a few ms; give the memory back.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && containerLazy.isInitialized()) {
            container.pageBitmaps.clear()
        }
    }
}
