package com.qurantime

import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.qurantime.core.api.Api
import com.qurantime.core.api.ApiClient
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.api.defaultHttpClient
import com.qurantime.core.cache.CachedFetcher
import com.qurantime.core.db.AppDatabase
import com.qurantime.core.net.ConnectivityOnlineStatus
import com.qurantime.core.net.OnlineStatus
import com.qurantime.core.settings.SettingsStore
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.qurantime.feature.audio.Media3Backend
import com.qurantime.feature.audio.PlaybackService
import com.qurantime.feature.audio.PlayerController
import com.qurantime.feature.audio.QueueHandler
import com.qurantime.feature.audio.buildPlayer
import com.qurantime.feature.bookmarks.BookmarksRepository
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.qurantime.feature.downloads.Downloader
import okio.Path.Companion.toOkioPath
import com.qurantime.feature.video.DefaultVideoPlayerFactory
import com.qurantime.feature.video.VideoPlayerFactory
import com.qurantime.feature.downloads.PagePack
import com.qurantime.feature.downloads.WorkManagerPagePack
import com.qurantime.feature.downloads.DownloadsRepository
import com.qurantime.feature.downloads.WorkManagerDownloader
import com.qurantime.feature.catalog.CatalogRepository
import com.qurantime.feature.explore.ExploreRepository
import com.qurantime.feature.live.LiveRepository
import com.qurantime.feature.mushaf.AyahGeometryRepository
import com.qurantime.feature.mushaf.MushafController
import com.qurantime.feature.mushaf.image.MaskCodec
import com.qurantime.feature.mushaf.image.OkHttpPageFetcher
import com.qurantime.feature.mushaf.image.PageImageStore
import com.qurantime.feature.mushaf.image.RawDeflateMaskCodec
import com.qurantime.feature.mushaf.ui.PageBitmapCache
import com.qurantime.feature.reciters.RecitersRepository
import com.qurantime.feature.followalong.FollowAlongController
import com.qurantime.feature.timing.TimedReadMatcher
import com.qurantime.feature.timing.TimingRepository
import kotlinx.coroutines.sync.withLock
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
    open val fetcher by lazy { CachedFetcher(db.apiCacheDao()) }

    val catalog by lazy { CatalogRepository(api, fetcher) }
    val reciters by lazy { RecitersRepository(api, fetcher) }
    val timing by lazy { TimingRepository(api, fetcher) }
    val live by lazy { LiveRepository(api, fetcher) }
    val explore by lazy { ExploreRepository(api, fetcher) }

    open val maskCodec: MaskCodec = RawDeflateMaskCodec
    open val pages: PageImageStore by lazy { PageImageStore(appContext.filesDir, OkHttpPageFetcher(http), maskCodec) }
    val pageBitmaps by lazy { PageBitmapCache() }

    /**
     * Counts the times Android asked for memory back while the app was hidden.
     * Pages on screen and the zoom render watch it: they let their bitmaps go
     * and draw again from disk when the app is visible.
     */
    val memoryTrims = MutableStateFlow(0)

    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        pageBitmaps.clear()
        memoryTrims.value++
    }

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

    open val queue: QueueHandler by lazy { QueueHandler(Media3Backend(player, mainScope), localPathFor = downloads::localPathFor, mainScope) }

    open val downloader: Downloader by lazy { WorkManagerDownloader(appContext) }
    open val videoPlayers: VideoPlayerFactory = DefaultVideoPlayerFactory

    /**
     * Thumbnails: a small memory cache (the Mushaf pages need the memory) and
     * a disk cache, fetched with the app's OkHttp client.
     */
    open val images: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(16L * 1024 * 1024).build() }
            .diskCache { DiskCache.Builder().directory(appContext.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(64L * 1024 * 1024).build() }
            .build()
    }
    val downloads by lazy { DownloadsRepository(db.downloadsDao(), downloader, appContext.filesDir, appScope) }
    open val pagePack: PagePack by lazy { WorkManagerPagePack(appContext) }

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

    val geometry by lazy {
        AyahGeometryRepository(timing, db.ayahGeometryDao(), suwar = { catalog.suwar(ApiLanguage.AR) })
    }

    private val matcherLock = kotlinx.coroutines.sync.Mutex()
    private var matcherMemo: TimedReadMatcher? = null

    /** Built once from the timed reads and the reciter list; a failure (offline) is retried next time. */
    open suspend fun matcher(): TimedReadMatcher = matcherLock.withLock {
        matcherMemo ?: TimedReadMatcher(timing.reads(), reciters.all(ApiLanguage.fromTag(settings.state.value.locale))).also { matcherMemo = it }
    }

    val follow by lazy {
        FollowAlongController(
            player = playerController,
            timing = timing,
            matcher = ::matcher,
            suwar = { catalog.suwar(ApiLanguage.fromTag(settings.state.value.locale)) },
            settings = settings,
            scope = mainScope,
        )
    }

    open val online: OnlineStatus by lazy {
        ConnectivityOnlineStatus(appContext.getSystemService(ConnectivityManager::class.java))
    }
}
