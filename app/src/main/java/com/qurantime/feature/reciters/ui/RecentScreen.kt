package com.qurantime.feature.reciters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.ReciterRoute
import com.qurantime.core.ui.AsyncView
import java.text.DateFormat

/** Reciters with the newest recordings, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen() {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.recently_added)) }) }) { padding ->
        AsyncView(
            load = { c.reciters.recent(lang).sortedByDescending { it.recentDate ?: 0L } },
            lang,
            isEmpty = { it.isEmpty() },
            emptyText = stringResource(R.string.no_reciters),
        ) { reads ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(reads, key = { it.id }) { r ->
                    ListItem(
                        modifier = Modifier.clickable { nav.navigate(ReciterRoute(r.id)) },
                        headlineContent = { Text(r.name) },
                        supportingContent = {
                            Text(listOfNotNull(r.moshaf.firstOrNull()?.name, r.recentDate?.let { date.format(it) }).joinToString(" · "))
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
