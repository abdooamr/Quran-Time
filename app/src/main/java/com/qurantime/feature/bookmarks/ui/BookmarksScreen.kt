package com.qurantime.feature.bookmarks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.l10n.localDigits
import com.qurantime.core.nav.AppTab
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.nav.openTab
import com.qurantime.core.theme.BookFont
import com.qurantime.feature.bookmarks.Bookmark
import com.qurantime.feature.catalog.Surah
import com.qurantime.feature.mushaf.surahsOnPage
import com.qurantime.feature.mushaf.ui.Roundel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen() {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val items by c.bookmarks.items.collectAsStateWithLifecycle()
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    val suwar by produceState(emptyList<Surah>(), lang) { value = runCatching { c.catalog.suwar(lang) }.getOrDefault(emptyList()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.bookmark_removed)
    val undo = stringResource(R.string.undo)
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.bookmarks)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bookmarks_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.id }) { b ->
                    BookmarkRow(
                        b,
                        suwar,
                        onOpen = {
                            c.mushaf.goTo(b.page)
                            nav.openTab(AppTab.MUSHAF)
                        },
                        onDelete = {
                            scope.launch {
                                c.bookmarks.remove(b.id)
                                snackbar.currentSnackbarData?.dismiss()
                                if (snackbar.showSnackbar(removed, undo) == SnackbarResult.ActionPerformed) c.bookmarks.restore(b)
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(b: Bookmark, suwar: List<Surah>, onOpen: () -> Unit, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val page = localDigits(b.page, locale)
    val pageLabel = stringResource(R.string.page_number, page)
    val onPage = surahsOnPage(b.page, suwar).joinToString(" · ") { it.name }
    val surahName = suwar.firstOrNull { it.id == b.surah }?.name.orEmpty()
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Roundel(numeral = page, semanticLabel = pageLabel, size = 44.dp) },
        headlineContent = {
            Text(
                if (b.isAyah) stringResource(R.string.bookmark_ayah, surahName, localDigits(b.ayah!!, locale)) else pageLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold),
            )
        },
        supportingContent = if (onPage.isEmpty()) null else ({ Text(if (b.isAyah) pageLabel else onPage) }),
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
