package com.qurantime.kotlin

import android.content.Context

/**
 * The app's dependency graph, written by hand (no DI framework).
 *
 * The constructor must not touch disk or network; every member is `by lazy`
 * so start-up only pays for what the first screen uses.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
}
