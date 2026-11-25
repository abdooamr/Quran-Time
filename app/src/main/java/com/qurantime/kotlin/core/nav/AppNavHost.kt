package com.qurantime.kotlin.core.nav

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.navigation.compose.navigation
import com.qurantime.kotlin.core.net.OnlineStatus
import com.qurantime.kotlin.core.ui.LocalSnackbar
import com.qurantime.kotlin.core.ui.OfflineBanner

/** The navigation controller, for screens that move between tabs or open sub-screens. */
val LocalNavController = staticCompositionLocalOf<NavHostController> { error("no NavController") }

/** Which tab a destination belongs to (each tab is a nested graph with its own back stack). */
fun tabOf(dest: NavDestination): AppTab? = dest.hierarchy.firstNotNullOfOrNull { d ->
    when {
        d.hasRoute<MushafTab>() -> AppTab.MUSHAF
        d.hasRoute<ListenTab>() -> AppTab.LISTEN
        d.hasRoute<LiveTab>() -> AppTab.LIVE
        d.hasRoute<ExploreTab>() -> AppTab.EXPLORE
        d.hasRoute<MoreTab>() -> AppTab.MORE
        else -> null
    }
}

/** Switches to [tab], keeping each tab's back stack as the user left it. */
fun NavHostController.openTab(tab: AppTab) {
    val current = currentBackStackEntry?.destination?.let(::tabOf)
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = tab != current
    }
}

/**
 * The app's navigation: five tabs in a [Shell]. [destinations] adds each
 * tab's graph; until a feature exists its tab shows a placeholder.
 */
@Composable
fun AppNavHost(
    nav: NavHostController,
    online: OnlineStatus? = null,
    bottomExtras: @Composable ColumnScope.() -> Unit = { online?.let { OfflineBanner(it) } },
    destinations: NavGraphBuilder.() -> Unit = { placeholderTabs() },
) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.let(::tabOf)
    CompositionLocalProvider(LocalNavController provides nav) {
        // The full player and the video player cover the whole screen.
        val fullScreen = entry?.destination?.let { it.hasRoute<PlayerRoute>() || it.hasRoute<VideoRoute>() } == true
        Shell(current = current, onSelect = nav::openTab, bottomExtras = bottomExtras, showBottomBar = !fullScreen, snackbar = LocalSnackbar.current) { padding ->
            NavHost(nav, startDestination = MushafTab, modifier = Modifier.padding(padding), builder = destinations)
        }
    }
}

/** A tab graph holding only its start screen. */
inline fun <reified G : Any, reified S : Any> NavGraphBuilder.tab(
    start: S,
    crossinline content: @Composable () -> Unit,
    noinline more: NavGraphBuilder.() -> Unit = {},
) {
    navigation<G>(startDestination = start) {
        composable<S> { content() }
        more()
    }
}

fun NavGraphBuilder.placeholderTabs() {
    tab<MushafTab, MushafRoute>(MushafRoute, { TabPlaceholder(AppTab.MUSHAF) })
    tab<ListenTab, ListenRoute>(ListenRoute, { TabPlaceholder(AppTab.LISTEN) })
    tab<LiveTab, LiveRoute>(LiveRoute, { TabPlaceholder(AppTab.LIVE) })
    tab<ExploreTab, ExploreRoute>(ExploreRoute, { TabPlaceholder(AppTab.EXPLORE) })
    tab<MoreTab, MoreRoute>(MoreRoute, { TabPlaceholder(AppTab.MORE) })
}

/** Temporary content for screens not built yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabPlaceholder(tab: AppTab) = TitledPlaceholder(stringResource(tab.label))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitledPlaceholder(title: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) {}
}
