package com.qurantime.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf

/** The app-wide snackbar (shown above the bottom bar). */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Replaces any snackbar on screen with [message]; returns true when [action] was tapped. */
suspend fun SnackbarHostState.say(message: String, action: String? = null): Boolean {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short) == SnackbarResult.ActionPerformed
}
