package com.qurantime.kotlin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qurantime.kotlin.core.nav.AppNavHost
import com.qurantime.kotlin.core.nav.AppTab
import com.qurantime.kotlin.core.nav.ExploreRoute
import com.qurantime.kotlin.core.nav.ListenRoute
import com.qurantime.kotlin.core.nav.LiveRoute
import com.qurantime.kotlin.core.nav.MoreRoute
import com.qurantime.kotlin.core.nav.MushafRoute
import com.qurantime.kotlin.core.nav.TabPlaceholder
import com.qurantime.kotlin.core.settings.SettingsDrivenApp
import com.qurantime.kotlin.feature.mushaf.ui.MushafScreen

/** The app's dependencies, for screens to read. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer provided") }

/** The whole app: settings-driven language and theme, the tab shell and every screen. */
@Composable
fun QuranTimeRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        SettingsDrivenApp(container.settings) {
            AppNavHost(rememberNavController(), online = container.online) {
                composable<MushafRoute> { MushafScreen() }
                composable<ListenRoute> { TabPlaceholder(AppTab.LISTEN) }
                composable<LiveRoute> { TabPlaceholder(AppTab.LIVE) }
                composable<ExploreRoute> { TabPlaceholder(AppTab.EXPLORE) }
                composable<MoreRoute> { TabPlaceholder(AppTab.MORE) }
            }
        }
    }
}
