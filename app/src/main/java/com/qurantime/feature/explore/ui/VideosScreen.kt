package com.qurantime.feature.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.explore.Video
import com.qurantime.feature.explore.VideoGroup
import com.qurantime.feature.explore.VideoType
import com.qurantime.feature.live.ui.openVideo

private class VideosData(val types: List<VideoType>, val groups: List<VideoGroup>)

/** Recitation videos, filterable by type, in a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen() {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.videos)) }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AsyncView(
                load = { VideosData(c.explore.videoTypes(lang), c.explore.videos(lang)) },
                lang,
                isEmpty = { it.groups.isEmpty() },
                emptyText = stringResource(R.string.no_stations),
            ) { d -> VideoGrid(d) }
        }
    }
}

@Composable
private fun VideoGrid(d: VideosData) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    val items = remember(d, type) { d.groups.flatMap { g -> g.videos.filter { type == null || it.typeId == type }.map { it to g.reciterName } } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("videos-grid"),
    ) {
        item(key = "types", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = type == null, onClick = { type = null }, label = { Text(stringResource(R.string.all)) }) }
                items(d.types, key = { it.id }) { t ->
                    FilterChip(selected = type == t.id, onClick = { type = t.id }, label = { Text(t.name) })
                }
            }
        }
        items(items, key = { it.first.id }) { (v, reciter) ->
            VideoCard(v, reciter) { openVideo(c, nav, v.url, reciter) }
        }
    }
}

@Composable
private fun VideoCard(video: Video, reciter: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("video-${video.id}").clickable(onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Thumbnail(video.thumbUrl, Icons.Outlined.SmartDisplay, Modifier.fillMaxSize())
                Icon(Icons.Rounded.PlayCircle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
            }
            Text(reciter, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(10.dp))
        }
    }
}
