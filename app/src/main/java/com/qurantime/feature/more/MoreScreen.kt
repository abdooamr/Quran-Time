package com.qurantime.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qurantime.R
import com.qurantime.core.nav.AboutRoute
import com.qurantime.core.nav.BookmarksRoute
import com.qurantime.core.nav.DownloadsRoute
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.MushafReciterRoute
import com.qurantime.core.nav.SettingsRoute

private class Entry(val icon: ImageVector, val label: Int, val route: Any)

private val entries = listOf(
    Entry(Icons.Outlined.BookmarkBorder, R.string.bookmarks, BookmarksRoute),
    Entry(Icons.Outlined.DownloadForOffline, R.string.downloads, DownloadsRoute),
    Entry(Icons.Outlined.RecordVoiceOver, R.string.mushaf_reciter, MushafReciterRoute),
    Entry(Icons.Outlined.Settings, R.string.settings, SettingsRoute),
    Entry(Icons.Outlined.Info, R.string.about, AboutRoute),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val nav = LocalNavController.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_more)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            for (e in entries) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { nav.navigate(e.route) },
                        leadingContent = { Icon(e.icon, contentDescription = null) },
                        headlineContent = { Text(stringResource(e.label)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    stringResource(R.string.source_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}
