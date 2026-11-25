package com.qurantime.kotlin.core.nav

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

/** Which tab a destination belongs to (each tab keeps its own back stack). */
fun tabOf(dest: NavDestination): AppTab? = dest.hierarchy.firstNotNullOfOrNull { d ->
    when {
        d.hasRoute<MushafRoute>() -> AppTab.MUSHAF
        d.hasRoute<ListenRoute>() -> AppTab.LISTEN
        d.hasRoute<LiveRoute>() -> AppTab.LIVE
        d.hasRoute<ExploreRoute>() -> AppTab.EXPLORE
        d.hasRoute<MoreRoute>() -> AppTab.MORE
        else -> null
    }
}

/**
 * The app's navigation: five tabs in a [Shell]. [destinations] adds the
 * screens; until a feature exists its tab shows a placeholder.
 */
@Composable
fun AppNavHost(
    nav: NavHostController,
    bottomExtras: @Composable ColumnScope.() -> Unit = {},
    destinations: NavGraphBuilder.() -> Unit = { placeholderTabs() },
) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.let(::tabOf)
    Shell(
        current = current,
        onSelect = { tab ->
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = tab != current
            }
        },
        bottomExtras = bottomExtras,
    ) { padding ->
        NavHost(nav, startDestination = MushafRoute, modifier = Modifier.padding(padding), builder = destinations)
    }
}

fun NavGraphBuilder.placeholderTabs() {
    composable<MushafRoute> { TabPlaceholder(AppTab.MUSHAF) }
    composable<ListenRoute> { TabPlaceholder(AppTab.LISTEN) }
    composable<LiveRoute> { TabPlaceholder(AppTab.LIVE) }
    composable<ExploreRoute> { TabPlaceholder(AppTab.EXPLORE) }
    composable<MoreRoute> { TabPlaceholder(AppTab.MORE) }
}

/** Temporary content for tabs not built yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabPlaceholder(tab: AppTab) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(tab.label)) }) }) {}
}
