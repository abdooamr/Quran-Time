package com.qurantime.feature.live.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.qurantime.AppContainer
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.VideoRoute
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.audio.RadioStream
import com.qurantime.feature.live.RadioStation
import com.qurantime.feature.reciters.foldForSearch
import kotlinx.coroutines.launch

const val FAVORITE_RADIO = "radio"

/** Stops the recitation and opens [url] in the full-screen video player. */
fun openVideo(c: AppContainer, nav: NavController, url: String, title: String, live: Boolean = false) {
    c.stopPlayback()
    nav.navigate(VideoRoute(url, title, live)) { launchSingleTop = true }
}

/** The Live tab: radio stations and the two live TV channels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen() {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.tab_live)) })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    val labels = listOf(R.string.radio to Icons.Rounded.Radio, R.string.live_tv to Icons.Rounded.LiveTv)
                    labels.forEachIndexed { i, (label, icon) ->
                        SegmentedButton(
                            selected = segment == i,
                            onClick = { segment = i },
                            shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        },
    ) { padding ->
        when (segment) {
            0 -> RadioList(padding)
            else -> LiveTvList(padding)
        }
    }
}

@Composable
private fun RadioList(padding: PaddingValues) {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    AsyncView(load = { c.live.radios(lang) }, lang, isEmpty = { it.isEmpty() }, emptyText = stringResource(R.string.no_stations)) { all ->
        val favourites by remember { c.db.favoritesDao().watch(FAVORITE_RADIO) }.collectAsStateWithLifecycle(emptyList())
        val favSet = remember(favourites) { favourites.toSet() }
        var query by rememberSaveable { mutableStateOf("") }
        val shown = remember(all, query, favSet) {
            val q = foldForSearch(query)
            val matching = if (q.isEmpty()) all else all.filter { foldForSearch(it.name).contains(q) }
            // Favourites first, otherwise the API's order.
            matching.filter { it.id in favSet } + matching.filter { it.id !in favSet }
        }
        val now by c.playerController.state.collectAsStateWithLifecycle()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_stations)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(shown, key = { it.id }) { r ->
                val item = RadioStream(r.id, r.name, r.url)
                RadioRow(
                    r,
                    playing = now.playing && now.item == item,
                    favourite = r.id in favSet,
                    onPlay = { c.mainScope.launch { c.playerController.playItems(listOf(item)) } },
                    onFavourite = { c.mainScope.launch { c.db.favoritesDao().toggle(FAVORITE_RADIO, r.id, System.currentTimeMillis()) } },
                )
            }
        }
    }
}

@Composable
private fun RadioRow(station: RadioStation, playing: Boolean, favourite: Boolean, onPlay: () -> Unit, onFavourite: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.testTag("radio-${station.id}").clickable(onClick = onPlay),
        colors = if (playing) ListItemDefaults.colors(headlineColor = primary) else ListItemDefaults.colors(),
        leadingContent = {
            if (playing) Icon(Icons.Rounded.GraphicEq, stringResource(R.string.now_playing), tint = primary)
            else Icon(Icons.Rounded.Radio, null)
        },
        headlineContent = { Text(station.name) },
        trailingContent = {
            IconButton(onClick = onFavourite) {
                if (favourite) Icon(Icons.Rounded.Star, stringResource(R.string.favorite_remove), tint = primary)
                else Icon(Icons.Rounded.StarBorder, stringResource(R.string.favorite_add))
            }
        },
    )
}

@Composable
private fun LiveTvList(padding: PaddingValues) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    AsyncView(load = { c.live.liveTv(lang) }, lang, isEmpty = { it.isEmpty() }, emptyText = stringResource(R.string.no_stations)) { channels ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                Card(Modifier.fillMaxWidth().clickable { openVideo(c, nav, ch.url, ch.name, live = true) }) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LiveTv, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(ch.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = { openVideo(c, nav, ch.url, ch.name, live = true) },
                            label = { Text(stringResource(R.string.live_badge)) },
                            leadingIcon = { Icon(Icons.Rounded.Circle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(10.dp)) },
                        )
                    }
                }
            }
        }
    }
}
