package com.qurantime.kotlin.feature.reciters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.api.normalizeServer
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.nav.LocalNavController
import com.qurantime.kotlin.core.nav.RecentRoute
import com.qurantime.kotlin.core.nav.ReciterRoute
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.core.ui.AsyncView
import com.qurantime.kotlin.feature.catalog.MoshafType
import com.qurantime.kotlin.feature.catalog.Riwaya
import com.qurantime.kotlin.feature.reciters.Moshaf
import com.qurantime.kotlin.feature.reciters.Reciter
import com.qurantime.kotlin.feature.reciters.ReciterFilter
import com.qurantime.kotlin.feature.reciters.applyReciterFilter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Everything the list and its filters need, loaded once. */
class ReciterListData(
    val all: List<Reciter>,
    val riwayat: List<Riwaya>,
    val types: Map<Int, MoshafType>,
    private val timedServers: Set<String>,
) {
    fun isTimed(m: Moshaf) = normalizeServer(m.server) in timedServers
}

const val FAVORITE_RECITER = "reciter"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitersScreen() {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_listen)) }) }) { padding ->
        AsyncView(
            load = {
                val reciters = c.reciters.all(lang)
                val riwayat = c.catalog.riwayat(lang)
                val types = c.catalog.moshafTypes(lang).associateBy { it.id }
                // Timing data is optional: without it the "follows the Mushaf" marks are simply absent.
                val timed = try {
                    c.timing.reads().map { normalizeServer(it.folderUrl) }.toSet()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptySet()
                }
                ReciterListData(reciters, riwayat, types, timed)
            },
            lang,
        ) { data -> RecitersBody(data, padding) }
    }
}

private enum class Picker { RIWAYA, STYLE }

@Composable
private fun RecitersBody(data: ReciterListData, padding: PaddingValues) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val locale = LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ReciterFilter()) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    val favorites by remember { c.db.favoritesDao().watch(FAVORITE_RECITER) }.collectAsStateWithLifecycle(emptyList())
    val favoriteSet = remember(favorites) { favorites.toSet() }
    val f = filter.copy(query = query)
    val results = remember(data, f, favoriteSet) { applyReciterFilter(data.all, f, data.types, data::isTimed, favoriteSet) }

    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_reciters)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item(key = "filters") { Filters(data, filter, onChange = { filter = it }, onPick = { picker = it }) }
        item(key = "recent") {
            ListItem(
                modifier = Modifier.clickable { nav.navigate(RecentRoute) },
                leadingContent = { Icon(Icons.Outlined.NewReleases, null) },
                headlineContent = { Text(stringResource(R.string.recently_added)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
        if (results.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_reciters), textAlign = TextAlign.Center)
                }
            }
        } else {
            var letter: String? = null
            for (r in results) {
                if (r.letter != letter) {
                    val l = r.letter
                    letter = l
                    item(key = "h-$l", contentType = "header") {
                        Text(
                            l,
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                }
                item(key = r.id, contentType = "reciter") {
                    ReciterRow(r, timed = r.moshaf.any(data::isTimed), favourite = r.id in favoriteSet, locale = locale) {
                        nav.navigate(ReciterRoute(r.id))
                    }
                }
            }
        }
    }

    picker?.let { p ->
        val options = when (p) {
            Picker.RIWAYA -> data.riwayat.associate { it.id to it.name }
            Picker.STYLE -> data.types.values.filter { filter.riwayaId == null || it.riwayaId == filter.riwayaId }.associate { it.id to it.name }
        }
        PickSheet(
            title = stringResource(if (p == Picker.RIWAYA) R.string.riwaya_filter else R.string.style_filter),
            allLabel = stringResource(if (p == Picker.RIWAYA) R.string.all_riwayat else R.string.all_styles),
            options = options,
            current = if (p == Picker.RIWAYA) filter.riwayaId else filter.moshafTypeId,
            onDismiss = { picker = null },
        ) { picked ->
            filter = if (p == Picker.RIWAYA) filter.copy(riwayaId = picked, moshafTypeId = null) else filter.copy(moshafTypeId = picked)
            picker = null
        }
    }
}

@Composable
private fun Filters(data: ReciterListData, f: ReciterFilter, onChange: (ReciterFilter) -> Unit, onPick: (Picker) -> Unit) {
    val riwaya = data.riwayat.firstOrNull { it.id == f.riwayaId }
    val style = f.moshafTypeId?.let { data.types[it] }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = riwaya != null, onClick = { onPick(Picker.RIWAYA) }, label = { Text(riwaya?.name ?: stringResource(R.string.riwaya_filter)) })
        FilterChip(selected = style != null, onClick = { onPick(Picker.STYLE) }, label = { Text(style?.name ?: stringResource(R.string.style_filter)) })
        FilterChip(selected = f.timedOnly, onClick = { onChange(f.copy(timedOnly = !f.timedOnly)) }, label = { Text(stringResource(R.string.follows_mushaf)) })
        FilterChip(selected = f.favoritesOnly, onClick = { onChange(f.copy(favoritesOnly = !f.favoritesOnly)) }, label = { Text(stringResource(R.string.favourites)) })
    }
}

@Composable
private fun ReciterRow(r: Reciter, timed: Boolean, favourite: Boolean, locale: java.util.Locale, onOpen: () -> Unit) {
    val c = LocalAppContainer.current
    ListItem(
        modifier = Modifier.testTag("reciter-${r.id}").clickable(onClick = onOpen),
        headlineContent = { Text(r.name) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recordings_count, localDigits(r.moshaf.size, locale)))
                if (timed) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Outlined.AutoStories, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.follows_mushaf), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = {
            IconButton(onClick = {
                c.mainScope.launch { c.db.favoritesDao().toggle(FAVORITE_RECITER, r.id, System.currentTimeMillis()) }
            }) {
                if (favourite) {
                    Icon(Icons.Rounded.Star, stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Rounded.StarBorder, stringResource(R.string.favorite_add))
                }
            }
        },
    )
}

/** A single-choice sheet with an "all" row first; [onPick] gets null for "all". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickSheet(title: String, allLabel: String, options: Map<Int, String>, current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxHeight(0.7f)) {
            item { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) }
            item {
                ListItem(
                    modifier = Modifier.clickable { onPick(null) },
                    headlineContent = { Text(allLabel) },
                    trailingContent = if (current == null) ({ Icon(Icons.Rounded.Check, null) }) else null,
                )
            }
            items(options.entries.toList(), key = { it.key }) { e ->
                ListItem(
                    modifier = Modifier.clickable { onPick(e.key) },
                    headlineContent = { Text(e.value) },
                    trailingContent = if (e.key == current) ({ Icon(Icons.Rounded.Check, null) }) else null,
                )
            }
        }
    }
}
