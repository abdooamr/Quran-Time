package com.qurantime.feature.bookmarks.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import kotlinx.coroutines.launch

/** The Mushaf header's page bookmark toggle. */
@Composable
fun BookmarkButton(page: Int) {
    val repo = LocalAppContainer.current.bookmarks
    val items by repo.items.collectAsStateWithLifecycle()
    val on = items.any { it.page == page && !it.isAyah }
    val scope = rememberCoroutineScope()
    IconToggleButton(checked = on, onCheckedChange = { scope.launch { repo.togglePage(page) } }) {
        if (on) {
            Icon(Icons.Filled.Bookmark, contentDescription = stringResource(R.string.bookmark_remove), tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = stringResource(R.string.bookmark_add))
        }
    }
}
