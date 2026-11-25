package com.qurantime.kotlin.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qurantime.kotlin.R
import kotlin.coroutines.cancellation.CancellationException

/** The state of something loaded once. */
sealed interface Async<out T> {
    data object Loading : Async<Nothing>
    data class Data<T>(val value: T) : Async<T>
    data class Failed(val error: Throwable) : Async<Nothing>
}

/**
 * Loads data once and shows a spinner, an error with Retry, an empty message,
 * or the data. [keys] restart the load when they change.
 */
@Composable
fun <T> AsyncView(
    load: suspend () -> T,
    vararg keys: Any?,
    isEmpty: (T) -> Boolean = { false },
    emptyText: String? = null,
    content: @Composable (T) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(*keys) { mutableStateOf<Async<T>>(Async.Loading) }
    LaunchedEffect(attempt, *keys) {
        state = Async.Loading
        state = try {
            Async.Data(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Async.Failed(e)
        }
    }
    when (val s = state) {
        Async.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Async.Failed -> ErrorWithRetry(onRetry = { attempt++ })
        is Async.Data -> if (isEmpty(s.value)) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(emptyText.orEmpty(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            content(s.value)
        }
    }
}

/** The shared "couldn't load" message with a Retry button. */
@Composable
fun ErrorWithRetry(onRetry: () -> Unit, message: String = stringResource(R.string.error_generic)) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
