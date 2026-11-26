package com.qurantime.feature.followalong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.l10n.localDigits
import com.qurantime.core.theme.BookFont
import com.qurantime.core.ui.LocalSnackbar
import com.qurantime.core.ui.say
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** A request to open the repeat sheet for [surah], starting with [from]..[to]. */
data class RepeatRequest(val surah: Int, val surahName: String, val from: Int? = null, val to: Int? = null)

private val TIMES = listOf(1, 3, 5, 10, 0)

/**
 * Loads the ayahs of the request's surah, then asks from, to and how many
 * times, and starts the repeat. [onDone] closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatSheetHost(request: RepeatRequest, onDone: () -> Unit) {
    val c = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val missing = stringResource(R.string.reciter_missing_surah)
    val error = stringResource(R.string.error_generic)
    var ayahs by remember(request) { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(request) {
        val list = c.follow.ayahsOf(request.surah)
        if (list.isEmpty()) {
            snackbar.say(missing)
            onDone()
        } else {
            ayahs = list
        }
    }
    val list = ayahs ?: return
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet) {
        RepeatForm(request, list) { from, to, times ->
            onDone()
            c.appScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                val ok = try {
                    c.follow.repeatAyahs(request.surah, from, to, times)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    snackbar.say(error)
                    return@launch
                }
                if (!ok) snackbar.say(missing)
            }
            scope.launch { sheet.hide() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatForm(request: RepeatRequest, ayahs: List<Int>, onStart: (Int, Int, Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    fun n(v: Int) = localDigits(v, locale)
    var from by remember { mutableIntStateOf(request.from ?: ayahs.first()) }
    var to by remember { mutableIntStateOf(request.to ?: request.from ?: ayahs.first()) }
    var times by remember { mutableIntStateOf(3) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        Text(stringResource(R.string.repeat_ayahs), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(request.surahName, style = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(20.dp))
        Row {
            AyahPicker(stringResource(R.string.repeat_from), from, ayahs, ::n, Modifier.weight(1f)) {
                from = it
                if (to < from) to = from
            }
            Spacer(Modifier.width(12.dp))
            AyahPicker(stringResource(R.string.repeat_to), to, ayahs.filter { it >= from }, ::n, Modifier.weight(1f)) { to = it }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.repeat_times), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in TIMES) FilterChip(selected = times == t, onClick = { times = t }, label = { Text(if (t == 0) "∞" else "${n(t)}×") })
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onStart(from, to, times) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Repeat, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.repeat_start))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahPicker(label: String, value: Int, choices: List<Int>, n: (Int) -> String, modifier: Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = n(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (a in choices) {
                DropdownMenuItem(text = { Text(n(a)) }, onClick = {
                    onPick(a)
                    open = false
                })
            }
        }
    }
}

/** The full player's "Repeat ayahs" button: needs a recitation that follows the Mushaf. */
@Composable
fun RepeatAyahsButton() {
    val c = LocalAppContainer.current
    val player by c.playerController.state.collectAsStateWithLifecycle()
    val follow by c.follow.state.collectAsStateWithLifecycle()
    var request by remember { mutableStateOf<RepeatRequest?>(null) }
    request?.let { RepeatSheetHost(it, onDone = { request = null }) }
    if (player.repeating) {
        TextButton(onClick = c.playerController::cancelRepeat) {
            Icon(Icons.Outlined.StopCircle, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.repeat_stop))
        }
        return
    }
    val surah = follow.surah
    val label = stringResource(R.string.repeat_ayahs)
    val unavailable = stringResource(R.string.repeat_ayahs_unavailable)
    TextButton(
        enabled = surah != null,
        onClick = {
            val s = surah ?: return@TextButton
            request = RepeatRequest(s, (player.item as? com.qurantime.feature.audio.SurahTrack)?.surahName.orEmpty(), (follow.current?.ayah ?: 1).coerceAtLeast(1))
        },
        modifier = if (surah == null) Modifier.semantics { contentDescription = unavailable } else Modifier,
    ) {
        Icon(Icons.Rounded.RepeatOne, null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
