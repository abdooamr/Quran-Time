package com.qurantime.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.ui.AsyncView
import kotlinx.coroutines.launch

/** Chooses the recording that plays and follows along in the Mushaf (the 115 timed reads, by riwaya). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafReciterScreen() {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.mushaf_reciter)) }) }) { padding ->
        AsyncView(load = { c.timing.reads() }) { reads ->
            val groups = reads.groupBy { it.riwaya }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        stringResource(R.string.mushaf_reciter_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                for ((riwaya, list) in groups) {
                    item(key = "h-$riwaya") {
                        Text(
                            riwaya,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                        )
                    }
                    items(list, key = { it.id }) { r ->
                        val selected = r.id == settings.mushafReadId
                        ListItem(
                            modifier = Modifier.clickable {
                                c.mainScope.launch {
                                    c.settings.setMushafReadId(r.id)
                                    nav.popBackStack()
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                            headlineContent = { Text(r.name) },
                            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                        )
                    }
                }
            }
        }
    }
}
