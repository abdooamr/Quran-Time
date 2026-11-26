package com.qurantime

import com.qurantime.core.nav.openTab
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.qurantime.core.nav.AboutRoute
import com.qurantime.core.nav.AppNavHost
import com.qurantime.core.nav.AppTab
import com.qurantime.core.nav.BookmarksRoute
import com.qurantime.core.nav.DownloadsRoute
import com.qurantime.core.nav.ExploreRoute
import com.qurantime.core.nav.ExploreTab
import com.qurantime.core.nav.ListenRoute
import com.qurantime.core.nav.ListenTab
import com.qurantime.core.nav.LiveRoute
import com.qurantime.core.nav.LiveTab
import com.qurantime.core.nav.MoreRoute
import com.qurantime.core.nav.MoreTab
import com.qurantime.core.nav.MushafReciterRoute
import com.qurantime.core.nav.MushafRoute
import com.qurantime.core.nav.MushafTab
import com.qurantime.core.nav.MoshafRoute
import com.qurantime.core.nav.RecentRoute
import com.qurantime.core.nav.ReciterRoute
import com.qurantime.core.nav.SettingsRoute
import com.qurantime.core.nav.TafsirSurahRoute
import com.qurantime.core.nav.TadaborListRoute
import com.qurantime.core.nav.TadaborRoute
import com.qurantime.core.nav.TafsirListRoute
import com.qurantime.core.nav.TafsirRoute
import com.qurantime.core.nav.VideosRoute
import com.qurantime.feature.explore.ui.ExploreScreen
import com.qurantime.feature.explore.ui.SurahPickerScreen
import com.qurantime.feature.explore.ui.TadaborScreen
import com.qurantime.feature.explore.ui.TafsirClipsScreen
import com.qurantime.feature.explore.ui.TafsirCollectionsScreen
import com.qurantime.feature.explore.ui.VideosScreen
import com.qurantime.core.nav.tab
import com.qurantime.core.settings.SettingsDrivenApp
import com.qurantime.core.nav.PlayerRoute
import com.qurantime.core.nav.VideoRoute
import com.qurantime.feature.live.ui.LiveScreen
import com.qurantime.feature.video.VideoPlayerScreen
import com.qurantime.core.ui.OfflineBanner
import com.qurantime.feature.audio.ui.FullPlayer
import com.qurantime.feature.audio.ui.MiniPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.feature.bookmarks.ui.BookmarkButton
import com.qurantime.feature.downloads.ui.DownloadButton
import com.qurantime.feature.downloads.ui.DownloadsScreen
import kotlinx.coroutines.launch
import com.qurantime.feature.followalong.ui.RepeatAyahsButton
import com.qurantime.feature.bookmarks.ui.BookmarksScreen
import com.qurantime.feature.more.MoreScreen
import androidx.navigation.toRoute
import com.qurantime.feature.reciters.ui.MoshafSurahsScreen
import com.qurantime.feature.reciters.ui.RecentScreen
import com.qurantime.feature.reciters.ui.ReciterScreen
import com.qurantime.feature.reciters.ui.RecitersScreen
import com.qurantime.feature.mushaf.ui.MushafScreen
import com.qurantime.feature.mushaf.ui.PlayPageButton
import com.qurantime.feature.settings.ui.AboutScreen
import com.qurantime.feature.settings.ui.MushafReciterScreen
import com.qurantime.feature.settings.ui.SettingsScreen

/** The app's dependencies, for screens to read. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer provided") }

/** The whole app: settings-driven language and theme, the tab shell and every screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuranTimeRoot(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    CompositionLocalProvider(LocalAppContainer provides container, com.qurantime.core.ui.LocalSnackbar provides snackbar) {
        // Test tags double as resource ids, so benchmarks can find the pager.
        Box(Modifier.semantics { testTagsAsResourceId = true }) {
          SettingsDrivenApp(container.settings) {
            AppNavHost(
                nav,
                online = container.online,
                bottomExtras = {
                    OfflineBanner(container.online)
                    MiniPlayer(onOpen = { nav.navigate(PlayerRoute) { launchSingleTop = true } })
                },
            ) {
                composable<VideoRoute> {
                    val v = it.toRoute<VideoRoute>()
                    VideoPlayerScreen(v.url, v.title, v.live, onClose = { nav.popBackStack() })
                }
                composable<PlayerRoute> {
                    FullPlayer(
                        onClose = { nav.popBackStack() },
                        // Tapping the page read along: go and read it in the Mushaf.
                        onOpenPage = { page ->
                            nav.popBackStack()
                            nav.openTab(AppTab.MUSHAF)
                            container.mushaf.goTo(page)
                        },
                        extras = { RepeatAyahsButton() },
                    )
                }
                tab<MushafTab, MushafRoute>(MushafRoute, {
                    MushafScreen(headerActions = { page, suwar ->
                        BookmarkButton(page)
                        PlayPageButton(page, suwar)
                    })
                })
                tab<ListenTab, ListenRoute>(ListenRoute, { RecitersScreen() }) {
                    composable<RecentRoute> { RecentScreen() }
                    composable<ReciterRoute> { ReciterScreen(it.toRoute<ReciterRoute>().id) }
                    composable<MoshafRoute> {
                        val r = it.toRoute<MoshafRoute>()
                        val rows by remember { container.downloads.watchAll() }.collectAsStateWithLifecycle(emptyList())
                        val byUrl = remember(rows) { rows.associateBy { row -> row.url } }
                        MoshafSurahsScreen(
                            r.reciterId,
                            r.moshafId,
                            onDownloadAll = { rc, m, suwar -> container.mainScope.launch { container.downloads.enqueueMoshaf(rc, m, suwar) } },
                            trailing = { t -> DownloadButton(t, byUrl[t.url]) },
                        )
                    }
                }
                tab<LiveTab, LiveRoute>(LiveRoute, { LiveScreen() })
                tab<ExploreTab, ExploreRoute>(ExploreRoute, { ExploreScreen() }) {
                    composable<VideosRoute> { VideosScreen() }
                    composable<TafsirListRoute> { TafsirCollectionsScreen() }
                    composable<TafsirRoute> {
                        val id = it.toRoute<TafsirRoute>().tafsirId
                        SurahPickerScreen(stringResource(R.string.tafsir)) { s -> nav.navigate(TafsirSurahRoute(id, s)) }
                    }
                    composable<TafsirSurahRoute> {
                        val r = it.toRoute<TafsirSurahRoute>()
                        TafsirClipsScreen(r.tafsirId, r.surah)
                    }
                    composable<TadaborListRoute> { SurahPickerScreen(stringResource(R.string.reflections)) { s -> nav.navigate(TadaborRoute(s)) } }
                    composable<TadaborRoute> { TadaborScreen(it.toRoute<TadaborRoute>().surah) }
                }
                tab<MoreTab, MoreRoute>(MoreRoute, { MoreScreen() }) {
                    composable<BookmarksRoute> { BookmarksScreen() }
                    composable<DownloadsRoute> { DownloadsScreen() }
                    composable<MushafReciterRoute> { MushafReciterScreen() }
                    composable<SettingsRoute> { SettingsScreen() }
                    composable<AboutRoute> { AboutScreen() }
                }
            }
          }
        }
    }
}
