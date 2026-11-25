package com.qurantime.kotlin

import android.app.Application

class QuranTimeApp : Application() {
    /** Every dependency inside is lazy: building the container does no I/O. */
    val container: AppContainer by lazy { AppContainer(this) }
}
