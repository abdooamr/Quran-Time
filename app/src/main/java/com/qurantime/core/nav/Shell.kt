package com.qurantime.core.nav

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.qurantime.R

enum class AppTab(val graph: Any, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    MUSHAF(MushafTab, R.string.tab_mushaf, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
    LISTEN(ListenTab, R.string.tab_listen, Icons.Outlined.Headphones, Icons.Filled.Headphones),
    LIVE(LiveTab, R.string.tab_live, Icons.Outlined.Radio, Icons.Filled.Radio),
    EXPLORE(ExploreTab, R.string.tab_explore, Icons.Outlined.Explore, Icons.Filled.Explore),
    MORE(MoreTab, R.string.tab_more, Icons.Filled.MoreHoriz, Icons.Filled.MoreHoriz),
}

/**
 * The scaffold around every tab: content plus a bottom bar holding
 * [bottomExtras] (offline banner, mini player) above the navigation bar.
 */
@Composable
fun Shell(
    current: AppTab?,
    onSelect: (AppTab) -> Unit,
    bottomExtras: @Composable ColumnScope.() -> Unit = {},
    showBottomBar: Boolean = true,
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        // Each screen handles the status bar itself (a top app bar, or the
        // Mushaf header); the shell only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) Column {
                bottomExtras()
                NavigationBar {
                    for (tab in AppTab.entries) {
                        val selected = tab == current
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
                        )
                    }
                }
            }
        },
        content = content,
    )
}
