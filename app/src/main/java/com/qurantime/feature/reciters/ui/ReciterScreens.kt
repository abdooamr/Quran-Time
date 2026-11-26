package com.qurantime.feature.reciters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.api.normalizeServer
import com.qurantime.core.l10n.localDigits
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.MoshafRoute
import com.qurantime.core.theme.BookFont
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.audio.SurahTrack
import com.qurantime.feature.catalog.Surah
import com.qurantime.feature.mushaf.ui.Roundel
import com.qurantime.feature.reciters.Moshaf
import com.qurantime.feature.reciters.Reciter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** The recording's surahs as playable tracks: only the ones it has, in order. */
fun tracksFor(r: Reciter, m: Moshaf, suwar: List<Surah>): List<SurahTrack> {
    val names = suwar.associate { it.id to it.name }
    return m.surahs.map { s -> SurahTrack(r.id, r.name, m.id, m.name, m.server, s, names[s] ?: "$s") }
}

/** A reciter's recordings, one card each. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReciterScreen(reciterId: Int) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    val locale = LocalConfiguration.current.locales[0]
    AsyncView(
        load = {
            val r = c.reciters.byId(reciterId, lang)
            val timed = try {
                c.timing.reads().map { normalizeServer(it.folderUrl) }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptySet() // No timing data: no "follows the Mushaf" marks.
            }
            r to timed
        },
        lang,
        isEmpty = { it.first == null },
        emptyText = stringResource(R.string.no_reciters),
    ) { (reciter, timed) ->
        val r = reciter!!
        Scaffold(topBar = { TopAppBar(title = { Text(r.name) }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(r.moshaf, key = { it.id }) { m ->
                    OutlinedCard(
                        onClick = { nav.navigate(MoshafRoute(r.id, m.id)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                            headlineContent = { Text(m.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text(stringResource(R.string.surah_count, localDigits(m.surahTotal, locale)))
                                    if (normalizeServer(m.server) in timed) Text(stringResource(R.string.follows_mushaf), color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A recording's surahs: tapping one plays the recording from it.
 * [trailing] adds each surah's download button (Task 30).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoshafSurahsScreen(
    reciterId: Int,
    moshafId: Int,
    onDownloadAll: (Reciter, Moshaf, List<Surah>) -> Unit = { _, _, _ -> },
    trailing: @Composable (SurahTrack) -> Unit = {},
) {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    val locale = LocalConfiguration.current.locales[0]
    AsyncView(
        load = {
            val r = c.reciters.byId(reciterId, lang)
            val m = r?.moshaf?.firstOrNull { it.id == moshafId }
            if (r == null || m == null) null else Triple(r, m, c.catalog.suwar(lang))
        },
        lang,
        isEmpty = { it == null },
        emptyText = stringResource(R.string.no_reciters),
    ) { data ->
        val (r, m, suwar) = data!!
        val tracks = tracksFor(r, m, suwar)
        val playing by c.playerController.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(r.name)
                            Text(m.name, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onDownloadAll(r, m, suwar) }) {
                            Icon(Icons.Outlined.DownloadForOffline, stringResource(R.string.download_all))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(tracks, key = { _, t -> t.surah }) { i, t ->
                    val n = localDigits(t.surah, locale)
                    val selected = t == playing.item
                    ListItem(
                        modifier = Modifier.clickable { c.mainScope.launch { c.playerController.playItems(tracks, i) } },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                        leadingContent = { Roundel(numeral = n, semanticLabel = n, size = 44.dp) },
                        headlineContent = {
                            Text(t.surahName, style = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold))
                        },
                        trailingContent = { trailing(t) },
                    )
                }
            }
        }
    }
}
