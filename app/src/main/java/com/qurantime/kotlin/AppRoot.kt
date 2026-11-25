package com.qurantime.kotlin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
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
import com.qurantime.kotlin.core.nav.SettingsRoute
import com.qurantime.kotlin.core.nav.TabPlaceholder
import com.qurantime.kotlin.core.nav.TitledPlaceholder
import com.qurantime.kotlin.core.nav.tab
import com.qurantime.kotlin.core.settings.SettingsDrivenApp
import com.qurantime.kotlin.feature.bookmarks.ui.BookmarkButton
import com.qurantime.kotlin.feature.bookmarks.ui.BookmarksScreen
import com.qurantime.kotlin.feature.more.MoreScreen
import com.qurantime.kotlin.feature.mushaf.ui.MushafScreen

/** The app's dependencies, for screens to read. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer provided") }

/** The whole app: settings-driven language and theme, the tab shell and every screen. */
@Composable
fun QuranTimeRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        SettingsDrivenApp(container.settings) {
            AppNavHost(rememberNavController(), online = container.online) {
                tab<MushafTab, MushafRoute>(MushafRoute, {
                    MushafScreen(actions = { page, _ -> BookmarkButton(page) })
                })
                tab<ListenTab, ListenRoute>(ListenRoute, { TabPlaceholder(AppTab.LISTEN) })
                tab<LiveTab, LiveRoute>(LiveRoute, { TabPlaceholder(AppTab.LIVE) })
                tab<ExploreTab, ExploreRoute>(ExploreRoute, { TabPlaceholder(AppTab.EXPLORE) })
                tab<MoreTab, MoreRoute>(MoreRoute, { MoreScreen() }) {
                    composable<BookmarksRoute> { BookmarksScreen() }
                    composable<DownloadsRoute> { TitledPlaceholder(stringResource(R.string.downloads)) }
                    composable<MushafReciterRoute> { TitledPlaceholder(stringResource(R.string.mushaf_reciter)) }
                    composable<SettingsRoute> { TitledPlaceholder(stringResource(R.string.settings)) }
                    composable<AboutRoute> { TitledPlaceholder(stringResource(R.string.about)) }
                }
            }
        }
    }
}
