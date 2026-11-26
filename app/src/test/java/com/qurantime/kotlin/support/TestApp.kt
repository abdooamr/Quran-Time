package com.qurantime.kotlin.support

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.qurantime.kotlin.AppContainer
import com.qurantime.kotlin.QuranTimeRoot
import com.qurantime.kotlin.core.cache.CachedFetcher
import com.qurantime.kotlin.core.db.AppDatabase
import com.qurantime.kotlin.core.settings.SettingsStore
import com.qurantime.kotlin.core.theme.AppThemeMode
import com.qurantime.kotlin.feature.audio.QueueHandler
import com.qurantime.kotlin.feature.reciters.parseReciters
import com.qurantime.kotlin.feature.timing.TimedReadMatcher
import com.qurantime.kotlin.feature.timing.parseTimedReads
import com.qurantime.kotlin.feature.mushaf.image.MaskCodec
import com.qurantime.kotlin.feature.mushaf.image.PageImageStore
import com.qurantime.kotlin.feature.mushaf.image.PageMask
import com.qurantime.kotlin.feature.mushaf.image.RawDeflateMaskCodec
import java.io.File
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** A solid-ink page, so tests can read the ink colour from the screen. */
val solidRasterize: (InputStream, Size, Int) -> PageMask = { input, viewBox, width ->
    input.readBytes()
    val height = (width * viewBox.height / viewBox.width).toInt().coerceAtLeast(1)
    PageMask(width, height, ByteArray(width * height) { 0xFF.toByte() })
}

/**
 * The real mask file format, decoded into an ARGB bitmap: Robolectric's
 * default graphics can't fill `ALPHA_8` bitmaps, and its native graphics
 * crash Compose on Windows. The real decoder is covered by MaskCodecTest.
 */
object LegacyFriendlyCodec : MaskCodec by RawDeflateMaskCodec {
    override fun decode(input: InputStream): Bitmap {
        val m = RawDeflateMaskCodec.decodeMask(input)
        return Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}

/**
 * The app's container backed by fakes and in-memory stores (the Kotlin
 * `TestDeps`): fixture API, in-memory Room, a temp DataStore, a fake network
 * status and a page store whose downloads come from [pageFetcher].
 */
class TestContainer(
    context: Context = ApplicationProvider.getApplicationContext(),
    val root: File = createTempDirectory("qt-test").toFile(),
) : AppContainer(context) {
    val fixtureApi = FixtureApi()
    override val api get() = fixtureApi
    override val db: AppDatabase = memoryDb()

    // API cache in a map: Room work started on the main thread only resumes when
    // the main looper is drained (see [await]); a map keeps waitUntil-based tests simple.
    override val fetcher = CachedFetcher(MemoryApiCacheDao())
    override val settings = SettingsStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(root, "settings.preferences_pb")
        },
    )
    val fakeOnline = FakeOnlineStatus()
    override val online get() = fakeOnline
    val pageFetcher = FakePageFetcher()
    override val maskCodec: MaskCodec = LegacyFriendlyCodec
    override val pages = PageImageStore(root, pageFetcher, maskCodec, rasterize = solidRasterize)
    override val pagePack by lazy { com.qurantime.kotlin.feature.downloads.InProcessPagePack(pages, geometry, mainScope) }
    val fakeDownloader = FakeDownloader()
    override val downloader get() = fakeDownloader
    val backend = FakePlayerBackend()
    override val queue by lazy { QueueHandler(backend, { null }, mainScope) }

    override fun connectSession() {}

    /** Built from the fixtures, as the Flutter TestDeps did: available offline in tests. */
    override suspend fun matcher() = fixtureMatcher

    companion object {
        val fixtureMatcher by lazy {
            TimedReadMatcher(parseTimedReads(loadFixtureJson("ayat_timing_reads.json")), parseReciters(loadFixtureJson("reciters_eng.json")))
        }
    }
}

/**
 * Starts the whole app on [c]: settings first (language, theme, last page),
 * as the real activity does behind its splash screen.
 */
fun ComposeContentTestRule.launchApp(
    c: TestContainer = TestContainer(),
    locale: String = "en",
    theme: AppThemeMode = AppThemeMode.LIGHT,
    lastPage: Int? = null,
): TestContainer {
    runBlocking {
        c.settings.setLocale(locale)
        c.settings.setThemeMode(theme)
        lastPage?.let { c.settings.setLastPage(it) }
        c.settings.load()
    }
    setContent { QuranTimeRoot(c) }
    waitForIdle()
    return c
}

/**
 * Waits (up to 5 s) for [condition], draining the main looper on each check:
 * work that background threads (Room, DataStore) post back to the main
 * thread runs then, as it does on a phone.
 */
fun ComposeContentTestRule.await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    waitUntil(timeoutMs) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        condition()
    }
}

/** Waits (up to 5 s) for a node with [tag]; IO work runs on real threads. */
fun ComposeContentTestRule.awaitTag(tag: String, timeoutMs: Long = 5_000) {
    waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
