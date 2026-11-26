package com.qurantime.kotlin.feature.explore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.nav.LocalNavController
import com.qurantime.kotlin.core.nav.TadaborListRoute
import com.qurantime.kotlin.core.nav.TafsirListRoute
import com.qurantime.kotlin.core.nav.VideosRoute
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.core.ui.AsyncView
import com.qurantime.kotlin.feature.mushaf.ui.Roundel

/** The Explore tab: videos, tafsir and reflections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    val nav = LocalNavController.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_explore)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Entry(Icons.Outlined.SmartDisplay, R.string.videos, R.string.videos_hint) { nav.navigate(VideosRoute) }
            Entry(Icons.Outlined.MenuBook, R.string.tafsir, R.string.tafsir_hint) { nav.navigate(TafsirListRoute) }
            Entry(Icons.Outlined.Lightbulb, R.string.reflections, R.string.reflections_hint) { nav.navigate(TadaborListRoute) }
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Picks a surah, then opens it. Used by tafsir and reflections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahPickerScreen(title: String, onPick: (surah: Int) -> Unit) {
    val c = LocalAppContainer.current
    val locale = LocalConfiguration.current.locales[0]
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AsyncView(load = { c.catalog.suwar(lang) }, lang) { suwar ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(suwar, key = { it.id }) { s ->
                        val n = localDigits(s.id, locale)
                        ListItem(
                            modifier = Modifier.clickable { onPick(s.id) },
                            leadingContent = { Roundel(numeral = n, semanticLabel = n, size = 44.dp) },
                            headlineContent = {
                                Text(s.name, style = MaterialTheme.typography.titleMedium, fontFamily = BookFont, fontWeight = FontWeight.Bold)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A network image filling its box, behind which [fallback] shows while it
 * loads or when it fails. Coil decodes it at the size the layout gives it.
 */
@Composable
fun Thumbnail(url: String, fallback: ImageVector, modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(fallback, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                imageLoader = c.images,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
