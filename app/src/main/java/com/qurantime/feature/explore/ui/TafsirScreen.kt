package com.qurantime.feature.explore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.TafsirRoute
import com.qurantime.core.theme.BookFont
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.audio.AudioClip
import com.qurantime.feature.explore.TafsirClip
import kotlinx.coroutines.launch

/** The tafsir collections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TafsirCollectionsScreen() {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tafsir)) }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AsyncView(load = { c.explore.tafasir(lang) }, lang, isEmpty = { it.isEmpty() }, emptyText = stringResource(R.string.no_stations)) { list ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { t ->
                        ListItem(
                            modifier = Modifier.clickable { nav.navigate(TafsirRoute(t.id)) },
                            leadingContent = { Icon(Icons.Outlined.MenuBook, null) },
                            headlineContent = { Text(t.name) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                }
            }
        }
    }
}

private class TafsirData(val collection: String, val surahName: String, val clips: List<TafsirClip>)

/** One surah's tafsir clips; tapping one plays it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TafsirClipsScreen(tafsirId: Int, surah: Int) {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    AsyncView(
        load = {
            TafsirData(
                collection = c.explore.tafasir(lang).firstOrNull { it.id == tafsirId }?.name.orEmpty(),
                surahName = c.catalog.suwar(lang).firstOrNull { it.id == surah }?.name ?: "$surah",
                clips = c.explore.tafsir(tafsirId, surah, lang),
            )
        },
        lang, tafsirId, surah,
    ) { d ->
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text(d.surahName, fontFamily = BookFont, fontWeight = FontWeight.Bold)
                        Text(d.collection, style = MaterialTheme.typography.bodySmall)
                    }
                })
            },
        ) { padding ->
            if (d.clips.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_tafsir)) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(d.clips, key = { it.id }) { clip ->
                        ListItem(
                            modifier = Modifier.clickable {
                                c.mainScope.launch {
                                    c.playerController.playItems(listOf(AudioClip("tafsir-${clip.id}", clip.name, d.collection, clip.url)))
                                }
                            },
                            leadingContent = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                            headlineContent = { Text(clip.name) },
                        )
                    }
                }
            }
        }
    }
}
