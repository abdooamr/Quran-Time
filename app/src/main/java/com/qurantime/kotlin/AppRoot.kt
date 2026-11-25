package com.qurantime.kotlin

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qurantime.kotlin.core.nav.AboutRoute
import com.qurantime.kotlin.core.nav.AppNavHost
import com.qurantime.kotlin.core.nav.AppTab
import com.qurantime.kotlin.core.nav.BookmarksRoute
import com.qurantime.kotlin.core.nav.DownloadsRoute
import com.qurantime.kotlin.core.nav.ExploreRoute
import com.qurantime.kotlin.core.nav.ExploreTab
import com.qurantime.kotlin.core.nav.ListenRoute
import com.qurantime.kotlin.core.nav.ListenTab
import com.qurantime.kotlin.core.nav.LiveRoute
import com.qurantime.kotlin.core.nav.LiveTab
import com.qurantime.kotlin.core.nav.MoreRoute
import com.qurantime.kotlin.core.nav.MoreTab
import com.qurantime.kotlin.core.nav.MushafReciterRoute
import com.qurantime.kotlin.core.nav.MushafRoute
import com.qurantime.kotlin.core.nav.MushafTab
import com.qurantime.kotlin.core.nav.MoshafRoute
import com.qurantime.kotlin.core.nav.RecentRoute
import com.qurantime.kotlin.core.nav.ReciterRoute
import com.qurantime.kotlin.core.nav.SettingsRoute
import com.qurantime.kotlin.core.nav.TafsirSurahRoute
import com.qurantime.kotlin.core.nav.TabPlaceholder
import com.qurantime.kotlin.core.nav.TitledPlaceholder
import com.qurantime.kotlin.core.nav.tab
import com.qurantime.kotlin.core.settings.SettingsDrivenApp
import com.qurantime.kotlin.core.nav.PlayerRoute
import com.qurantime.kotlin.core.ui.OfflineBanner
import com.qurantime.kotlin.feature.audio.ui.FullPlayer
import com.qurantime.kotlin.feature.audio.ui.MiniPlayer
import com.qurantime.kotlin.feature.bookmarks.ui.BookmarkButton
import com.qurantime.kotlin.feature.followalong.ui.RepeatAyahsButton
import com.qurantime.kotlin.feature.bookmarks.ui.BookmarksScreen
import com.qurantime.kotlin.feature.more.MoreScreen
import com.qurantime.kotlin.feature.reciters.ui.RecentScreen
import com.qurantime.kotlin.feature.reciters.ui.RecitersScreen
import com.qurantime.kotlin.feature.mushaf.ui.MushafScreen
import com.qurantime.kotlin.feature.mushaf.ui.PlayPageButton
import com.qurantime.kotlin.feature.settings.ui.MushafReciterScreen

/** The app's dependencies, for screens to read. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer provided") }

/** The whole app: settings-driven language and theme, the tab shell and every screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuranTimeRoot(container: AppContainer) {
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    CompositionLocalProvider(LocalAppContainer provides container, com.qurantime.kotlin.core.ui.LocalSnackbar provides snackbar) {
        // Test tags double as resource ids, so benchmarks can find the pager.
        Box(Modifier.semantics { testTagsAsResourceId = true }) {
          SettingsDrivenApp(container.settings) {
            val nav = rememberNavController()
            AppNavHost(
                nav,
                online = container.online,
                bottomExtras = {
                    OfflineBanner(container.online)
                    MiniPlayer(onOpen = { nav.navigate(PlayerRoute) { launchSingleTop = true } })
                },
            ) {
                composable<PlayerRoute> { FullPlayer(onClose = { nav.popBackStack() }, extras = { RepeatAyahsButton() }) }
                tab<MushafTab, MushafRoute>(MushafRoute, {
                    MushafScreen(headerActions = { page, suwar ->
                        BookmarkButton(page)
                        PlayPageButton(page, suwar)
                    })
                })
                tab<ListenTab, ListenRoute>(ListenRoute, { RecitersScreen() }) {
                    composable<RecentRoute> { RecentScreen() }
                    composable<ReciterRoute> { TitledPlaceholder(stringResource(R.string.tab_listen)) }
                    composable<MoshafRoute> { TitledPlaceholder(stringResource(R.string.tab_listen)) }
                }
                tab<LiveTab, LiveRoute>(LiveRoute, { TabPlaceholder(AppTab.LIVE) })
                tab<ExploreTab, ExploreRoute>(ExploreRoute, { TabPlaceholder(AppTab.EXPLORE) }) {
                    composable<TafsirSurahRoute> { TitledPlaceholder(stringResource(R.string.tab_explore)) }
                }
                tab<MoreTab, MoreRoute>(MoreRoute, { MoreScreen() }) {
                    composable<BookmarksRoute> { BookmarksScreen() }
                    composable<DownloadsRoute> { TitledPlaceholder(stringResource(R.string.downloads)) }
                    composable<MushafReciterRoute> { MushafReciterScreen() }
                    composable<SettingsRoute> { TitledPlaceholder(stringResource(R.string.settings)) }
                    composable<AboutRoute> { TitledPlaceholder(stringResource(R.string.about)) }
                }
            }
          }
        }
    }
}
