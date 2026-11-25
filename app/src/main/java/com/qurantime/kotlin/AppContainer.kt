package com.qurantime.kotlin

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.qurantime.kotlin.core.api.Api
import com.qurantime.kotlin.core.api.ApiClient
import com.qurantime.kotlin.core.api.defaultHttpClient
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.db.AppDatabase
import com.qurantime.kotlin.core.net.ConnectivityOnlineStatus
import com.qurantime.kotlin.core.net.OnlineStatus
import com.qurantime.kotlin.core.settings.SettingsStore
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.qurantime.kotlin.feature.audio.Media3Backend
import com.qurantime.kotlin.feature.audio.PlaybackService
import com.qurantime.kotlin.feature.audio.PlayerController
import com.qurantime.kotlin.feature.audio.QueueHandler
import com.qurantime.kotlin.feature.audio.buildPlayer
import com.qurantime.kotlin.feature.bookmarks.BookmarksRepository
import com.qurantime.kotlin.feature.catalog.CatalogRepository
import com.qurantime.kotlin.feature.explore.ExploreRepository
import com.qurantime.kotlin.feature.live.LiveRepository
import com.qurantime.kotlin.feature.mushaf.MushafController
import com.qurantime.kotlin.feature.mushaf.image.MaskCodec
import com.qurantime.kotlin.feature.mushaf.image.OkHttpPageFetcher
import com.qurantime.kotlin.feature.mushaf.image.PageImageStore
import com.qurantime.kotlin.feature.mushaf.image.RawDeflateMaskCodec
import com.qurantime.kotlin.feature.mushaf.ui.PageBitmapCache
import com.qurantime.kotlin.feature.reciters.RecitersRepository
import com.qurantime.kotlin.feature.timing.TimingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's dependency graph, written by hand (no DI framework).
 *
 * The constructor must not touch disk or network; every member is `by lazy`
 * so start-up only pays for what the first screen uses.
 */
open class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Lives as long as the process: background work that outlives a screen. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    open val db: AppDatabase by lazy { AppDatabase.build(appContext) }

    open val settings: SettingsStore by lazy {
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                appContext.preferencesDataStoreFile("settings")
            },
        )
    }

    val http by lazy { defaultHttpClient() }
    open val api: Api by lazy { ApiClient(http) }
    val fetcher by lazy { CachedFetcher(db.apiCacheDao()) }

    val catalog by lazy { CatalogRepository(api, fetcher) }
    val reciters by lazy { RecitersRepository(api, fetcher) }
    val timing by lazy { TimingRepository(api, fetcher) }
    val live by lazy { LiveRepository(api, fetcher) }
    val explore by lazy { ExploreRepository(api, fetcher) }

    open val maskCodec: MaskCodec = RawDeflateMaskCodec
    open val pages: PageImageStore by lazy { PageImageStore(appContext.filesDir, OkHttpPageFetcher(http), maskCodec) }
    val pageBitmaps by lazy { PageBitmapCache() }

    /** Built after settings are loaded (it starts at the saved last page). */
    val mushaf by lazy {
        MushafController(
            initialPage = settings.state.value.lastPage,
            saveLastPage = settings::setLastPage,
            prefetch = { page, width -> pages.prefetchAround(page, width) },
            scope = appScope,
        )
    }

    /** Main-thread scope: the player and everything driving it live on the main thread. */
    val mainScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** The app's one ExoPlayer, created on first use (not at start-up). */
    val player by lazy { buildPlayer(appContext) }

    open val queue: QueueHandler by lazy { QueueHandler(Media3Backend(player, mainScope), localPathFor = { null }, mainScope) }

    /** Set by the activity: asks for the notification permission (Android 13+), once. */
    var notificationPermission: (suspend () -> Unit)? = null

    val playerController by lazy {
        PlayerController(
            queue = queue,
            settings = settings,
            requestNotificationPermission = { notificationPermission?.invoke() },
            scope = mainScope,
            ensureService = ::connectSession,
        )
    }

    private var session: ListenableFuture<MediaController>? = null

    /**
     * Binds the media session service (lock screen, notification, Bluetooth).
     * Done on the first play or restore, never at start-up.
     */
    open fun connectSession() {
        if (session != null) return
        session = MediaController.Builder(appContext, SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))).buildAsync()
    }

    /** Stop from the notification or the app: stops playback, the sleep timer and any repeat. */
    open fun stopPlayback() = playerController.stop()

    /** Loads the saved bookmarks in the background as soon as anything asks for them. */
    val bookmarks by lazy {
        BookmarksRepository(db.bookmarksDao()).also { repo -> appScope.launch { repo.load() } }
    }

    open val online: OnlineStatus by lazy {
        ConnectivityOnlineStatus(appContext.getSystemService(ConnectivityManager::class.java))
    }
}
