package com.qurantime.kotlin.feature.downloads.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.db.DownloadEntity
import com.qurantime.kotlin.core.db.DownloadStatus
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.feature.audio.SurahTrack
import java.util.Locale
import kotlinx.coroutines.launch

/** `3.0` for 3 MB; in Arabic with Eastern Arabic digits and the Arabic decimal mark (`٣٫٠`). */
fun formatMb(bytes: Long, locale: Locale = Locale.ROOT): String {
    val s = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))
    if (locale.language != "ar") return s
    return buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') '٠' + (c - '0') else if (c == '.') '٫' else c)
    }
}

/** A surah row's download control: its state, and what tapping does in that state. */
@Composable
fun DownloadButton(track: SurahTrack, row: DownloadEntity?) {
    val c = LocalAppContainer.current
    val repo = c.downloads
    fun go(block: suspend () -> Unit) = c.mainScope.launch { block() }
    when (row?.status) {
        null -> IconButton(onClick = { go { repo.enqueueSurah(track) } }) { Icon(Icons.Rounded.Download, stringResource(R.string.download)) }
        DownloadStatus.QUEUED -> IconButton(onClick = { go { repo.cancel(row.id) } }) { Icon(Icons.Rounded.Schedule, stringResource(R.string.waiting_to_download)) }
        DownloadStatus.RUNNING -> IconButton(onClick = { go { repo.pause(row.id) } }) {
            val total = row.totalBytes ?: 0L
            val label = stringResource(R.string.downloading)
            if (total > 0) {
                CircularProgressIndicator(progress = { row.receivedBytes.toFloat() / total }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
            Icon(Icons.Rounded.Pause, label, Modifier.size(12.dp))
        }
        DownloadStatus.PAUSED -> IconButton(onClick = { go { repo.resume(row.id) } }) { Icon(Icons.Rounded.PauseCircleOutline, stringResource(R.string.download_paused)) }
        DownloadStatus.DONE -> IconButton(onClick = {}, enabled = false) {
            Icon(Icons.Rounded.DownloadDone, stringResource(R.string.downloaded), tint = MaterialTheme.colorScheme.primary)
        }
        DownloadStatus.FAILED, DownloadStatus.MISSING -> IconButton(onClick = { go { repo.enqueueSurah(track) } }) {
            Icon(Icons.Rounded.Refresh, stringResource(R.string.retry_download), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val c = LocalAppContainer.current
    val locale = LocalConfiguration.current.locales[0]
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    val rows by remember { c.downloads.watchAll() }.collectAsStateWithLifecycle(emptyList())
    // Names for rows; offline with nothing cached, rows fall back to numbers.
    val names by produceState(Pair(emptyMap<Int, String>(), emptyMap<Int, Pair<String, String>>()), lang) {
        val surahs = runCatching { c.catalog.suwar(lang) }.getOrDefault(emptyList()).associate { it.id to it.name }
        val moshafs = runCatching { c.reciters.all(lang) }.getOrDefault(emptyList())
            .flatMap { r -> r.moshaf.map { it.id to (r.name to it.name) } }.toMap()
        value = surahs to moshafs
    }
    val active = rows.filter { it.status != DownloadStatus.DONE }
    val byMoshaf = rows.filter { it.status == DownloadStatus.DONE }.groupBy { it.moshafId ?: 0 }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.downloads)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { PagePackCard() }
            if (active.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.active_downloads)) }
                items(active, key = { it.id }) { r ->
                    ListItem(
                        headlineContent = { Text(names.first[r.surah] ?: localDigits(r.surah ?: 0, locale)) },
                        supportingContent = {
                            Column {
                                names.second[r.moshafId]?.first?.let { Text(it) }
                                if (r.status == DownloadStatus.RUNNING) {
                                    val total = r.totalBytes ?: 0L
                                    Spacer(Modifier.height(6.dp))
                                    if (total > 0) LinearProgressIndicator(progress = { r.receivedBytes.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                if (r.status == DownloadStatus.RUNNING) {
                                    IconButton(onClick = { c.mainScope.launch { c.downloads.pause(r.id) } }) { Icon(Icons.Rounded.Pause, stringResource(R.string.pause)) }
                                }
                                if (r.status == DownloadStatus.PAUSED) {
                                    IconButton(onClick = { c.mainScope.launch { c.downloads.resume(r.id) } }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play)) }
                                }
                                IconButton(onClick = { c.mainScope.launch { c.downloads.cancel(r.id) } }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                            }
                        },
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.saved_recordings)) }
            if (byMoshaf.isEmpty()) {
                item { Text(stringResource(R.string.no_downloads), Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            items(byMoshaf.entries.toList(), key = { "m-${it.key}" }) { (moshafId, list) ->
                val bytes = list.sumOf { it.totalBytes ?: 0L }
                val name = names.second[moshafId]
                ListItem(
                    headlineContent = { Text(name?.first ?: "#$moshafId") },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                name?.second,
                                stringResource(R.string.surahs_saved, localDigits(list.size, locale)),
                                stringResource(R.string.size_mb, formatMb(bytes, locale)),
                            ).joinToString(" · "),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { confirmDelete = moshafId }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_recording_title)) },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                FilledTonalButton(onClick = {
                    confirmDelete = null
                    c.mainScope.launch { c.downloads.deleteMoshaf(id) }
                }) { Text(stringResource(R.string.delete)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagePackCard() {
    val c = LocalAppContainer.current
    val locale = LocalConfiguration.current.locales[0]
    val progress by remember { c.pagePack.progress }.collectAsStateWithLifecycle(null)
    var refresh by remember { mutableIntStateOf(0) }
    val bytes by produceState(0L, refresh, progress == null) { value = c.pages.cachedBytes() }
    OutlinedCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.mushaf_pages), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pages_cached, stringResource(R.string.size_mb, formatMb(bytes, locale))))
            Spacer(Modifier.height(12.dp))
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("${localDigits(p.done, locale)} / ${localDigits(p.total, locale)}")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { c.pagePack.start(c.mushaf.pageWidthPx) }) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.download_pages))
                    }
                    TextButton(onClick = { c.mainScope.launch { c.pages.clear(); c.pageBitmaps.clear(); refresh++ } }) {
                        Text(stringResource(R.string.clear_pages))
                    }
                }
            }
        }
    }
}
