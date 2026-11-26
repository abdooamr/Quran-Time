package com.qurantime

import android.app.Application
import androidx.work.Configuration

/**
 * The application. WorkManager is initialised on first use (not at process
 * start) via [Configuration.Provider], keeping start-up lean.
 */
class QuranTimeApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    private val containerLazy = lazy { AppContainer(this) }

    /** Every dependency inside is lazy: building the container does no I/O. */
    val container: AppContainer by containerLazy

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Decoded pages come back from disk in a few ms; give the memory back.
        if (containerLazy.isInitialized()) container.onTrimMemory(level)
    }
}
