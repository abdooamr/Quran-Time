# Quran Time (Kotlin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android (Kotlin, Jetpack Compose) reimplementation of the finished Flutter Quran Time app. It has exactly the same features and behaviour, and it meets the measured speed, size and memory targets on the user's Galaxy A50.

**Architecture:**
- **App structure:** one `:app` module with feature packages that mirror the Flutter `lib/features/*`. A hand-written `AppContainer` replaces `main.dart`'s wiring.
- **Data:** repositories call one OkHttp `ApiClient` through a Room-backed `CachedFetcher`. Settings live in DataStore.
- **UI state:** `ViewModel`s and plain controller classes expose `StateFlow`.
- **Audio:** one Media3 `MediaSessionService` hosts ExoPlayer, behind a `PlayerBackend` interface so the queue logic can be tested on the JVM.
- **Mushaf:** pages are turned once into 8-bit mask images on the phone and drawn tinted, with no SVG parsing on swipes.
- **Measurement:** `:benchmark` and `:baselineprofile` modules measure and speed up startup and swipes.

**Tech Stack:**
- Kotlin 2.x, AGP, Compose BOM and Material 3, Navigation Compose with type-safe routes, Lifecycle ViewModel
- kotlinx.serialization and kotlinx.coroutines
- OkHttp, Room (KSP), Preferences DataStore
- Media3 (exoplayer, exoplayer-hls, session, ui), WorkManager, AndroidSVG, Coil 3
- androidx core-splashscreen, profileinstaller
- Tests: JUnit4, Robolectric, Compose UI test, Turbine, MockWebServer, Macrobenchmark

**Spec:** `docs/superpowers/specs/2026-11-25-quran-time-kotlin-design.md` (this repo). The **feature authority** it names is the Flutter app in `D:\AAAAAAProjects\GIT-HUB\Quran-Time` (`lib/`, `test/`), its spec `docs/superpowers/specs/2026-11-16-quran-time-design.md`, and its ledger `docs/superpowers/ledger-2026-11-16-quran-time.md`. Below, `FLUTTER/` means `D:\AAAAAAProjects\GIT-HUB\Quran-Time\`.

## Global Constraints

**Project**
- **Location and identity:** the project lives at `D:\AAAAAAProjects\GIT-HUB\Quran-Time-Kotlin` as its own git repo, working on branch `feat/kotlin-app`. Package and applicationId are `com.qurantime.kotlin`. The display name is "Quran Time" / "وقت القرآن".
- **SDK and build:** minSdk 23, compileSdk and targetSdk 36, JDK 17 (installed). Use the latest stable Kotlin, AGP, Compose BOM, Media3, Room and WorkManager when Task 1 runs, pinned in `gradle/libs.versions.toml`. The Gradle wrapper is copied from `FLUTTER/android/` and then bumped to the version that AGP requires.
- **Allowed dependencies:** only those in Tech Stack. No Hilt/Dagger/Koin, Retrofit, RxJava, bundled SQLite, or analytics, ads, crash-reporting or account SDKs. The APK contains **no native `.so` files**.
- **Network:** only `https://www.mp3quran.net/api/v3/` and the media hosts its responses link to. The `language` query value is only `ar` or `eng`.

**URLs**
- Every URL taken from an API response goes through `upgradeToHttps`.
- MP3 URL is `server/` + 3-digit surah + `.mp3`, built only when the surah is in that moshaf's `surah_list`.
- Mushaf page URL is `https://www.mp3quran.net/api/quran_pages_svg/NNN.svg` for pages 1..604.
- The viewBox is 235×235 for pages 1–2 and 345×550 for every other page.
- Juz start pages: `1,22,42,62,82,102,121,142,162,182,201,222,242,262,282,302,322,342,362,382,402,422,442,462,482,502,522,542,562,582`.

**Look and settings**
- **Palette** (from `FLUTTER/lib/core/theme/app_theme.dart`). Highlight values are ARGB.

  | Theme | Background | Ink | Highlight | Field | On field | Gilt | Chrome |
  |---|---|---|---|---|---|---|---|
  | Light | `#F4EEDD` | `#231F20` | `#552E7D5B` | `#2F6B4F` | `#F7F1E3` | `#A97F33` | `#EBE3CF` |
  | Sepia | `#EBDDC2` | `#4A3B28` | `#55B07D2B` | `#2F6B4F` | `#F4EEDD` | `#9E7630` | `#E0CFAF` |
  | Dark | `#121212` | `#E6E1D6` | `#664FB98A` | `#1D4434` | `#EDE6D3` | `#C9A45A` | `#1A1A18` |

  The dark theme's primary colour is `#8FCBA9`. Amiri Regular and Bold come from `FLUTTER/assets/fonts/`.
- **Setting defaults:** `locale = ar`, `themeMode = SYSTEM`, `mushafReadId = 5`, `autoFollow = true`, `lastPage = 1`.
- **Cache TTLs:**

  | Data | TTL |
  |---|---|
  | suwar, riwayat, moshaf | 30 days |
  | reciters, radios | 7 days |
  | live-tv, videos, video_types, recent_reads | 1 day |
  | tafasir, tafsir, tadabor | 30 days |
  | ayat_timing (reads, soar, timing) | never expire |

- **ApiClient:** retry delays of 500 ms then 1500 ms on timeouts and 5xx. Connect timeout 20 s, read timeout 30 s, follows redirects.
- **Strings:** every user-facing string comes from `values/strings.xml` (en) and `values-ar/strings.xml`, converted from `FLUTTER/lib/core/l10n/app_en.arb` / `app_ar.arb` with the same keys in snake_case. The default locale is ar, set with `AppCompatDelegate`-free per-app locales (`LocaleManager` on API 33+, otherwise `Configuration` in `attachBaseContext`). Arabic layout is RTL.

**Performance targets, measured on the A50 (SM-A505FN) — hard gates (spec K5)**

| Metric | Target |
|---|---|
| arm64 APK size | ≤ 8 MB |
| Cold start to fully drawn | ≤ 600 ms, and below the Flutter baseline |
| Page swipe jank | ≤ 1% janky frames |
| Memory (PSS), Mushaf open and audio playing | ≤ 120 MB |
| All 604 page masks | ≤ 100 MB |

When any measured number misses its target, **stop and ask the user**, giving the number. This is the only planned stop. Everything else runs unattended (K6). Collect manual device checks in `docs/manual-checks.md` and hand them over at the end.

**Porting and tests**
- **Port rule:** when a task says *port `FLUTTER/test/x_test.dart`*, every `test(`/`testWidgets(` in that file becomes a Kotlin `@Test` named with the Dart description in backticks. It asserts the same behaviour, with the same fixture values. Widget tests become Robolectric Compose tests: a Flutter `tooltip` maps to `contentDescription`, and `find.text` maps to `onNodeWithText`. A Dart test that cannot apply to the native stack is replaced by an equivalent, with a `Ruling:` line in the ledger.
- **Fixtures:** copy `FLUTTER/test/fixtures/*` to `app/src/test/resources/fixtures/`. `FixtureApi` (Task 5) answers paths exactly like `FLUTTER/test/support/fakes.dart` `fixtureHandler`.
- **Unit-test command:** `./gradlew :app:testDebugUnitTest --tests '<class>'` (Git Bash, from the repo root).
- **Signing:** release signing reads `key.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), falling back to the debug key. **Never create the keystore or write passwords.** The user does that.

## Review Focus

1. **A mask file cut short by process death or a full disk** must be detected (bad header or size) and re-made from the SVG. It must never crash or draw a garbage page. Test in Task 13.
2. **Changing screen width** (rotation, fold, split screen) when masks exist for another width bucket shows the existing mask scaled, and re-makes it later. There's no crash, and no second full set of masks is kept: the old bucket file is deleted when a page is re-made. Test in Task 13.
3. **A disk-full error during the page pack** counts that page as failed, deletes `.part` files, and leaves no partial mask. The pack continues with the other pages. Test in Task 30.
4. **Losing audio focus or unplugging headphones** pauses playback and keeps the position. ExoPlayer is built with `handleAudioFocus = true` and `setHandleAudioBecomingNoisy(true)`. Test in Task 20.
5. **Memory pressure** (`onTrimMemory` ≥ `TRIM_MEMORY_UI_HIDDEN`) clears the page bitmap LRU and the zoom bitmap, and redraws from disk on return. Test in Task 14.

---

## Phase 0 — Project and baseline

### Task 1: Repo, Gradle project and an app that starts

**Files:**
- Create: `Quran-Time-Kotlin/` with these files:
  - `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
  - `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
  - `app/build.gradle.kts`, `app/proguard-rules.pro`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/qurantime/kotlin/{QuranTimeApp.kt,MainActivity.kt,AppContainer.kt}`
  - `.gitignore`, `key.properties.example`
- Create: `docs/superpowers/specs/` and `docs/superpowers/plans/`, holding copies of this plan and its spec.
- Test: `app/src/test/java/com/qurantime/kotlin/AppStartTest.kt`

**Interfaces:**
- Produces:
  - `class QuranTimeApp : Application { val container: AppContainer }`
  - `class AppContainer(context: Context)`: every dependency is `by lazy`, and the constructor does no I/O.
  - `MainActivity : ComponentActivity`, using edge-to-edge and `installSplashScreen()`.

- [ ] **Step 1:** `git init` the repo, then copy the spec and plan into `docs/superpowers/`. Copy `gradlew*` and `gradle/wrapper/` from `FLUTTER/android/`. Write the version catalog with the latest stable versions; check each one on Google Maven or Maven Central. Commit on `main`, then `git switch -c feat/kotlin-app`.
- [ ] **Step 2:** Configure `app/build.gradle.kts`:
  - applicationId and SDK levels from Global Constraints
  - Compose enabled; `kotlinOptions` jvmTarget 17
  - release: `isMinifyEnabled = true`, `isShrinkResources = true`
  - `android.enableR8.fullMode=true` in `gradle.properties`
  - signing from `key.properties` with the debug fallback, as in `FLUTTER/android/app/build.gradle.kts`
  - `testOptions.unitTests.isIncludeAndroidResources = true`
  - no `abiFilters`, because there's no native code
- [ ] **Step 3: Write the failing test** `AppStartTest.`app container builds without touching disk``. Use Robolectric `ApplicationProvider`, build `AppContainer`, and assert that `context.filesDir.listFiles()` is empty afterwards.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest --tests 'com.qurantime.kotlin.AppStartTest'`. Expected: FAIL to compile, because `AppContainer` doesn't exist yet.
- [ ] **Step 5:** Implement `QuranTimeApp`, an empty `AppContainer`, and `MainActivity` showing an empty `Surface`. Register the application and activity in the manifest. Add the `INTERNET` permission.
- [ ] **Step 6:** Run the test (expect PASS), then `./gradlew :app:assembleRelease`. Expected: BUILD SUCCESSFUL, and `unzip -l app/build/outputs/apk/release/*.apk | grep '\.so'` prints nothing.
- [ ] **Step 7:** Commit: `chore: Kotlin project skeleton`.

### Task 2: Benchmark modules and an A50 instrumentation check

**Files:**
- Create: `benchmark/build.gradle.kts`, `benchmark/src/main/java/com/qurantime/kotlin/benchmark/StartupBenchmark.kt`
- Create: `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/java/com/qurantime/kotlin/baselineprofile/BaselineProfileGenerator.kt`
- Create: `tools/perf.sh`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts` (add `benchmark` build type: signingConfig debug, `isDebuggable = false`, matchingFallbacks release; apply `androidx.baselineprofile`)

**Interfaces:**
- Produces:
  - `tools/perf.sh <package> <out.md>` prints and appends four numbers, using the same methods for both apps:
    - `fully_drawn_ms`: the median of 10 cold starts, from logcat `Fully drawn <pkg>`
    - `jank_pct`: from `dumpsys gfxinfo <pkg>` "Janky frames" after the swipe test, or from the Flutter hook (Task 3)
    - `pss_mb`: `dumpsys meminfo <pkg>` TOTAL PSS
    - `apk_mb`: the arm64 APK size
  - `StartupBenchmark.startup()` measures cold startup with `StartupTimingMetric`.

- [ ] **Step 1:** Write `StartupBenchmark` (Macrobenchmark, `CompilationMode.Partial()`, 10 iterations, `startActivityAndWait()`). Write `BaselineProfileGenerator`, which starts the app and waits for idle; later tasks add journeys.
- [ ] **Step 2:** With the A50 connected (`adb devices` lists `R58M389X2XD`), run `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.benchmark.StartupBenchmark`. Expected: a JSON result under `benchmark/build/outputs/connected_android_test_additional_output/`.
  - If it fails because the screen is off or the device is locked, or because UiAutomator input is blocked (Samsung blocked `adb shell input` in the Flutter build), record `Ruling: A50 instrumentation unavailable — benchmarks batched for the user` in the ledger. Add each later benchmark step to `docs/manual-checks.md` as a command for the user, and continue.
- [ ] **Step 3:** Start an API 34+ emulator from the installed `system-images/*/google_apis_playstore` image, and run `./gradlew :app:generateBaselineProfile`. Expected: `app/src/release/generated/baselineProfiles/baseline-prof.txt` exists.
- [ ] **Step 4:** Write `tools/perf.sh`, and check it against the empty app: it prints four numbers.
- [ ] **Step 5:** Commit: `chore: benchmark and baseline-profile modules`.

### Task 3: Flutter baseline numbers

**Files:**
- Modify (in `FLUTTER/`, on a new branch `measure/baseline`, **never merged**):
  - `android/app/src/main/kotlin/.../MainActivity.kt`: a `MethodChannel("qt/perf")` whose `fullyDrawn` calls `reportFullyDrawn()`
  - `lib/features/mushaf/ui/mushaf_page.dart`: invoke `fullyDrawn` once after the first page's SVG paints
  - `integration_test/swipe_perf_test.dart`: swipes 20 pages with `tester.fling`; `SchedulerBinding.addTimingsCallback` counts frames whose `totalSpan` is over 16.6 ms and prints `jank_pct=<n>`
- Create (Kotlin repo): `docs/perf/baseline-flutter.md`

- [ ] **Step 1:** In `FLUTTER/`, run `git switch -c measure/baseline` and add the three hooks. Then run `flutter build apk --release --split-per-abi` and `adb install` the arm64 APK. Before measuring, open pages 1–25 once so they're on disk.
- [ ] **Step 2:** Run `tools/perf.sh com.qurantime.app docs/perf/baseline-flutter.md`.
  - For jank, run `flutter drive --profile --driver=test_driver/integration_test.dart --target=integration_test/swipe_perf_test.dart` and record the printed `jank_pct`.
  - For memory, start "Play page" on page 3, then take `pss_mb`.
  - Expected: four numbers recorded, with the date and device.
- [ ] **Step 3:** Commit on `measure/baseline` in FLUTTER (so it's never lost), then `git switch feat/quran-time-app`. Commit `docs/perf/baseline-flutter.md` in the Kotlin repo: `docs: Flutter baseline on the A50`.

### Task 4: Theme, strings, digits and the five-tab shell

**Files:**
- Create: `core/theme/{Palette.kt,Theme.kt,Type.kt}`, `core/l10n/{Digits.kt,Locales.kt}`
- Create: `core/nav/{Routes.kt,AppNavHost.kt,Shell.kt}`
- Create: placeholder screens for each tab
- Create: `res/values/strings.xml`, `res/values-ar/strings.xml`, `res/font/amiri_regular.ttf`, `res/font/amiri_bold.ttf`
- Test: port `FLUTTER/test/app_shell_test.dart` → `ShellTest.kt`, plus `DigitsTest.kt` for `FLUTTER/lib/core/l10n/digits.dart`. That file has no Dart test, so the Kotlin tests are 3 cases: Western digits in en, Arabic-Indic `٣` in ar, and parsing `"٣٠٢"` → 302.

**Interfaces:**
- Produces:
  - `enum class AppThemeMode { SYSTEM, LIGHT, SEPIA, DARK }`
  - `data class MushafPalette(background, ink, highlight, field, onField, gilt, chrome: Color)` with `LocalMushafPalette` and `fun paletteFor(mode: AppThemeMode, systemDark: Boolean): MushafPalette`
  - `@Composable fun QuranTimeTheme(mode: AppThemeMode, content: @Composable () -> Unit)`
  - `fun localDigits(n: Int, locale: Locale): String`
  - `fun parseLocalDigits(s: String): Int?`
  - `@Serializable` route objects:
    - tabs: `MushafRoute`, `ListenRoute`, `LiveRoute`, `ExploreRoute`, `MoreRoute`
    - Listen: `RecentRoute`, `ReciterRoute(id: Int)`, `MoshafRoute(reciterId: Int, moshafId: Int)`
    - Explore: `VideosRoute`, `TafsirListRoute`, `TafsirRoute(tafsirId: Int)`, `TafsirSurahRoute(tafsirId: Int, surah: Int)`, `TadaborListRoute`, `TadaborRoute(surah: Int)`
    - More: `BookmarksRoute`, `DownloadsRoute`, `MushafReciterRoute`, `SettingsRoute`, `AboutRoute`
    - full screen: `PlayerRoute`, `VideoRoute(url: String, title: String, live: Boolean)`
  - `@Composable fun Shell(bottomExtras: @Composable ColumnScope.() -> Unit, content: …)`: a bottom bar with the extras slot above the `NavigationBar`. Each tab keeps its own back stack (`saveState`/`restoreState`).

- [ ] **Step 1:** Convert both ARB files to `strings.xml`, including placeholders and plurals. Copy the fonts.
- [ ] **Step 2:** Write the ported shell tests and `DigitsTest`. Run them; expected FAIL.
- [ ] **Step 3:** Implement the theme (Material 3 `ColorScheme` built from the palette as `FLUTTER/lib/core/theme/app_theme.dart` builds it), the digits helpers, the routes, the NavHost and the shell. Tab order, icons and labels follow `FLUTTER/lib/core/router.dart` and `FLUTTER/lib/app.dart`.
- [ ] **Step 4:** Run the tests; expected PASS. Commit: `feat: theme, strings and five-tab shell`.

---

## Phase 1 — Data layer

### Task 5: URLs, JSON helpers and ApiClient

**Files:**
- Create: `core/api/{Urls.kt,JsonUtils.kt,ApiClient.kt}`
- Test: port `FLUTTER/test/core/api/urls_test.dart`, `json_utils_test.dart` and `api_client_test.dart` (MockWebServer)
- Create test support:
  - `support/Fixtures.kt` (`loadFixture(name): String`)
  - `support/FixtureApi.kt`: implements `Api` like `fixtureHandler`, and records `calls: List<Pair<String, Map<String, String>>>`

**Interfaces:**
- Produces:
  - `enum class ApiLanguage(val code: String) { AR("ar"), ENG("eng"); companion object { fun fromLocale(l: Locale): ApiLanguage } }`
  - Constants and helpers:
    - `const val API_BASE = "https://www.mp3quran.net/api/v3/"`, `const val MUSHAF_PAGES = 604`
    - `upgradeToHttps(url: String): String`, `normalizeServer(url: String): String`
    - `surahAudioUrl(server: String, surah: Int): String`, `mushafPageUrl(page: Int): String`
  - `JsonElement` helpers: `asIntLoose()`, `asIntListLoose()` (the CSV `surah_list`), mirroring `json_utils.dart`
  - `class ApiException(val statusCode: Int?, message: String) : Exception(message)`
  - `interface Api { suspend fun getJson(path: String, query: Map<String, Any?> = emptyMap()): JsonElement }`, implemented by `class ApiClient(client: OkHttpClient, baseUrl: HttpUrl = API_BASE.toHttpUrl(), retryDelays: List<Long> = listOf(500, 1500)) : Api`, which runs on `Dispatchers.IO` and drops null query values

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. `ApiClient` retries only `SocketTimeoutException` and HTTP ≥ 500. Other errors throw `ApiException` at once.
- [ ] **Step 3:** Run the tests (expected PASS). Commit: `feat: URLs and ApiClient`.

### Task 6: Room database, DataStore settings and CachedFetcher

**Files:**
- Create: `core/db/{AppDatabase.kt,Entities.kt,Converters.kt}` and `core/db/dao/{ApiCacheDao,BookmarksDao,FavoritesDao,DownloadsDao,AyahGeometryDao}.kt`
- Create: `core/settings/SettingsStore.kt`, `core/cache/CachedFetcher.kt`
- Test: port `FLUTTER/test/features/settings/settings_store_test.dart` and `FLUTTER/test/core/cache/cached_fetcher_test.dart`
- Test: `DbMigrationTest.kt` (Room `MigrationTestHelper` with exported schema v1)
- Create test support: `support/TestClock.kt`

**Interfaces:**
- Produces:
  - **Entities**, with the columns of `FLUTTER/lib/core/db/app_database.dart` except Settings:
    - `ApiCacheEntity(key PK, json, fetchedAtMs)`
    - `BookmarkEntity(id auto, page, surah?, ayah?, createdAtMs)`
    - `FavoriteEntity(kind, refId, createdAtMs; PK kind+refId)`
    - `DownloadEntity(id auto, kind, moshafId?, surah?, url UNIQUE, path, status: DownloadStatus, totalBytes?, receivedBytes=0, taskId?)`
    - `AyahGeometryEntity(surah, ayah, page, polygon, markerX?, markerY?; PK surah+ayah)`
  - `enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, MISSING }`, stored **by name** with a `TypeConverter`
  - `AppDatabase` version 1 with `exportSchema = true` and `fallbackToDestructiveMigration(false)`. DAOs expose `suspend` reads and writes and `Flow` watches, matching the Dart DAO methods (`FLUTTER/lib/core/db/daos/*.dart`).
  - `class SettingsStore(dataStore: DataStore<Preferences>)` with:
    - `val state: StateFlow<Settings>`
    - `suspend fun load()`
    - setters `setLocale(tag: String)`, `setThemeMode(AppThemeMode)`, `setMushafReadId(Int)`, `setAutoFollow(Boolean)`, `setLastPage(Int)`, `setLastPlaybackJson(String?)`
    - `data class Settings(locale: String = "ar", themeMode: AppThemeMode = SYSTEM, mushafReadId: Int = 5, autoFollow: Boolean = true, lastPage: Int = 1, lastPlaybackJson: String? = null)`
  - `object Ttl { CATALOG = 30.days; WEEKLY = 7.days; DAILY = 1.days; val FOREVER: Duration? = null }`
  - `class CachedFetcher(dao: ApiCacheDao, now: () -> Long = System::currentTimeMillis)` with:
    - `suspend fun <T> fetch(key: String, ttl: Duration?, remote: suspend () -> JsonElement, parse: (JsonElement) -> T, forceRefresh: Boolean = false): T`
    - `peek(key)`, `isStale(key, ttl)`, `fetchedAt(key)`, `store(key, json)`
    - semantics as `FLUTTER/lib/core/cache/cached_fetcher.dart`

- [ ] **Step 1:** Write the ported tests and `DbMigrationTest.`schema v1 opens and enum is stored by name``, which inserts a `DOWNLOADS` row with status `DONE` and reads the raw column back as `"DONE"`. Run them; expected FAIL.
- [ ] **Step 2:** Implement. Set the Room schema location to `app/schemas`.
- [ ] **Step 3:** Run the tests (expected PASS). Commit: `feat: Room, DataStore settings and CachedFetcher`.

### Task 7: Models

**Files:**
- Create: `feature/catalog/Models.kt`, `feature/reciters/Models.kt`, `feature/timing/Models.kt`, `feature/live/Models.kt`, `feature/explore/Models.kt`, `feature/audio/PlayableItem.kt`
- Test: port these `FLUTTER/test/features/` files: `catalog/models_test.dart`, `reciters/models_test.dart`, `timing/models_test.dart`, `live/models_test.dart`, `explore/models_test.dart`, `audio/playable_item_test.dart`

**Interfaces:**
- Produces (fields as in the Dart models; parse functions take `JsonElement`):
  - **Catalog:** `Surah(id, name, startPage, endPage, isMakki)`, `Riwaya(id, name)`, `MoshafType(id, riwayaId, styleId, name)`
  - **Reciters:**
    - `Moshaf(id, name, server, surahTotal, moshafType, surahs: List<Int>)` with `has(surah)`, `audioUrl(surah): String?` and `toJson()`
    - `Reciter(id, name, letter, date: Instant?, recentDate: Instant?, moshaf: List<Moshaf>)` with `toJson()`
  - **Timing:**
    - `TimedRead(id, name, riwaya, folderUrl, surahCount)`
    - `AyahTiming(ayah, startMs, endMs, polygon: List<Offset>?, marker: Offset?, page: Int?)`, using Compose `Offset` from `ui-geometry`
  - **Live:** `RadioStation(id, name, url)`, `LiveChannel(id, name, url)`
  - **Explore:**
    - `VideoType(id, name)`, `Video(id, typeId, url, thumbUrl)`, `VideoGroup(id, reciterName, videos)`
    - `Tafsir(id, name)`, `TafsirClip(id, tafsirId, name, url, surah)`
    - `TadaborClip(id, surah, audioUrl?, videoUrl?, imageUrl, title, text, surahName, riwayaName?, reciterName?)` with `isVideo`
  - **Playable items:** `sealed interface PlayableItem { val url: String; fun toJson(): JsonObject }` with:
    - `SurahTrack(reciterId, reciterName, moshafId, moshafName, server, surah, surahName)`
    - `RadioStream(id, name, url)`
    - `AudioClip(id: String, title, subtitle, url, artUrl?)`
    - `PlayableItem.fromJson(JsonObject)`, with the same `kind` keys and equality as the Dart file

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement with hand-written `JsonElement` parsing, so the loose types (int-as-string, CSV `surah_list`, `tafasir` key) match Dart exactly.
- [ ] **Step 3:** Run the tests (expected PASS). Commit: `feat: models`.

### Task 8: Repositories, timing lookup and the read matcher

**Files:**
- Create:
  - `feature/catalog/CatalogRepository.kt`, `feature/reciters/RecitersRepository.kt`
  - `feature/timing/{TimingRepository.kt,AyahLookup.kt,TimedReadMatcher.kt}`
  - `feature/live/LiveRepository.kt`, `feature/explore/ExploreRepository.kt`
- Test: port these `FLUTTER/test/features/` files: `repositories_test.dart`, `catalog/catalog_repository_test.dart`, `reciters/reciters_repository_test.dart` (delta merge with `last_updated_date = fetchedAt − 1 day`), `timing/lookup_and_matcher_test.dart`

**Interfaces:**
- Consumes: `Api`, `CachedFetcher`, `Ttl`, models (Tasks 5–7).
- Produces (signatures as in the Dart files, `Future<T>` → `suspend fun … : T`; cache keys and TTLs identical, with the language in the key):
  - `CatalogRepository.suwar(lang)`, `riwayat(lang)`, `moshafTypes(lang)`
  - `RecitersRepository.all(lang, forceRefresh = false)`, `byId(id, lang)`, `recent(lang)`
  - `TimingRepository.reads()`, `timedSurahs(readId)`, `timing(readId, surah)`
  - `LiveRepository.radios(lang)`, `liveTv(lang)`
  - `ExploreRepository.videoTypes(lang)`, `videos(lang)`, `tafasir(lang)`, `tafsir(tafsirId, surah, lang)`, `tadabor(surah, lang)`
  - Timing lookup: `fun ayahIndexAt(timings: List<AyahTiming>, positionMs: Long): Int?`, as `ayah_lookup.dart`
  - `class TimedReadMatcher(reads: List<TimedRead>, reciters: List<Reciter>)` with `readForServer(server): TimedRead?` and `isTimed(m: Moshaf): Boolean`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run the tests (expected PASS). Commit: `feat: repositories and timing lookup`.

### Task 9: OnlineStatus and container wiring

**Files:**
- Create: `core/net/OnlineStatus.kt`, `core/ui/{OfflineBanner.kt,AsyncView.kt}`
- Modify: `AppContainer.kt`, `Shell.kt` (OfflineBanner in `bottomExtras`)
- Test: `OnlineStatusTest.kt`, and `OfflineBannerTest.kt` ported from the banner cases in `FLUTTER/test/app_shell_test.dart` (if they're already ported in Task 4, add none)

**Interfaces:**
- Produces:
  - `interface OnlineStatus { val online: StateFlow<Boolean> }`, implemented by `ConnectivityOnlineStatus(cm: ConnectivityManager)` (NetworkCallback with `NET_CAPABILITY_VALIDATED`) and, in tests, `FakeOnlineStatus` with `set(Boolean)`
  - `@Composable fun <T> AsyncView(state: Async<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit)`, with `sealed interface Async<out T> { Loading; Data(value); Failed(error) }`, behaving like `FLUTTER/lib/core/widgets/async_view.dart`
  - `AppContainer` exposes all of the following `by lazy`: `db`, `settings`, `api`, `fetcher`, the repositories, `online`

- [ ] **Step 1:** Write `OnlineStatusTest.`reports offline when the validated network is lost`` (Robolectric `ShadowConnectivityManager`) and the banner test. Run them; expected FAIL.
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run the tests (expected PASS). Commit: `feat: online status, async view, container wiring`.

---

## Phase 2 — Mushaf

### Task 10: Page mask codec (measure, then choose)

**Files:**
- Create: `feature/mushaf/image/{PageMask.kt,MaskCodec.kt,WebpMaskCodec.kt,DeflateMaskCodec.kt,SvgRasterizer.kt}`
- Create: `app/src/androidTest/java/com/qurantime/kotlin/MaskCodecDeviceTest.kt`
- Create: `app/src/androidTest/assets/page-003.svg` and `page-300.svg`, copied from `FLUTTER/samples/mushaf-svg/`
- Test: `MaskCodecTest.kt` (Robolectric, `@GraphicsMode(NATIVE)`)

**Interfaces:**
- Produces:
  - `class PageMask(val width: Int, val height: Int, val alpha: ByteArray)`: one byte per pixel, values quantised to 16 levels (`v and 0xF0 or (v ushr 4)`)
  - `interface MaskCodec { val ext: String; fun encode(m: PageMask, out: OutputStream); fun decode(input: InputStream): Bitmap /* ALPHA_8 */ }`. The file starts with an 8-byte header: magic `QTM1`, width u16, height u16. Decode throws `CorruptMaskException` on a bad magic or a short read.
  - `object SvgRasterizer { fun render(svg: InputStream, viewBox: Size, widthPx: Int): PageMask }`: AndroidSVG `renderToCanvas` on an ARGB bitmap, keeping only the alpha. It never runs on the main thread (`check(Looper.myLooper() != Looper.getMainLooper())`).
  - `fun renderWidthBucket(px: Int): Int = ((px + 119) / 120) * 120`

- [ ] **Step 1: Write the failing tests:**
  - `MaskCodecTest.`round trip keeps every pixel`` (for both codecs, a 50×80 mask in which `decode(encode(m))` has identical alpha bytes)
  - `MaskCodecTest.`quantises to 16 levels``
  - `MaskCodecTest.`truncated file throws CorruptMaskException``
  - `MaskCodecTest.`bucket rounds up to 120``, asserting `renderWidthBucket(1080) == 1080` and `renderWidthBucket(1081) == 1200`
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement both codecs and the rasterizer.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5: Measure on the A50.** `MaskCodecDeviceTest` renders page 3 and page 300 at the A50's page-area width, encodes them with both codecs, decodes each 20 times, and logs `codec=<ext> bytes=<n> decodeMedianMs=<n> renderMs=<n>`. Run `./gradlew :app:connectedDebugAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.MaskCodecDeviceTest`, then `adb logcat -d | grep codec=`. Expected: 4 lines.
- [ ] **Step 6:** Pick the smaller codec whose decode median is ≤ 8 ms. Record `Task 10: Ruling: codec=<ext> — <numbers>` in the ledger and make it `AppContainer.maskCodec`. If neither decodes in ≤ 8 ms, pick the faster one and note it; the swipe benchmark in Task 17 is the real gate.
  - **If the projected size of all 604 pages (the average page size × 604) is over 100 MB, stop and ask the user (K5).**
- [ ] **Step 7:** Commit: `feat: page mask codec and rasterizer`.

### Task 11: Mushaf index

**Files:**
- Create: `feature/mushaf/MushafIndex.kt`
- Test: port `FLUTTER/test/features/mushaf/mushaf_index_test.dart`

**Interfaces:**
- Produces: `JUZ_START_PAGES`, `juzForPage(page)`, `surahsOnPage(page, suwar)`, `viewBoxForPage(page): Size`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: Mushaf index`.

### Task 12: Page fetcher

**Files:**
- Create: `feature/mushaf/image/PageFetcher.kt`
- Test: `PageFetcherTest.kt` (MockWebServer)

**Interfaces:**
- Produces: `fun interface PageFetcher { suspend fun fetch(url: String, target: File) }` and `class OkHttpPageFetcher(client: OkHttpClient) : PageFetcher`. It writes the gzip-decoded SVG to `target` and throws `IOException` on non-2xx. Read timeout 30 s; this fixes the Flutter minor "no receiveTimeout on page fetches".

- [ ] **Step 1:** Write the tests:
  - `PageFetcherTest.`writes the body to the target``
  - `PageFetcherTest.`404 throws and leaves no file``
  - `PageFetcherTest.`stalled body times out``, using MockWebServer `throttleBody` and a 1 s client timeout
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Commit: `feat: page fetcher`.

### Task 13: PageImageStore

**Files:**
- Create: `feature/mushaf/image/{PageImageStore.kt,SvgLru.kt}`
- Test: port `FLUTTER/test/features/mushaf/mushaf_page_store_test.dart` → `PageImageStoreTest.kt`, plus the new tests below
- Create test support: `support/FakePageFetcher.kt` (serves `page-003.svg` for any page; it can fail, delay or count calls)

**Interfaces:**
- Consumes: `PageFetcher`, `MaskCodec`, `SvgRasterizer`, `renderWidthBucket`, `viewBoxForPage`, `mushafPageUrl`.
- Produces:
  - `class PageImageStore(root: File, fetcher: PageFetcher, codec: MaskCodec, rasterize: (InputStream, Size, Int) -> PageMask = SvgRasterizer::render, io: CoroutineDispatcher = Dispatchers.IO, cpu: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2), svgLruSize: Int = 30)`
  - Methods:
    - `fun isCached(page: Int): Boolean`: a mask exists in any bucket
    - `suspend fun mask(page: Int, widthPx: Int): MaskResult`, which de-duplicates in-flight work per page and throws `PageUnavailableException(page, cause)`
    - `data class MaskResult(val file: File, val bucket: Int, val exact: Boolean)`, where `exact = false` means another bucket's mask was returned and a re-make is pending
    - `fun prefetchAround(page: Int, widthPx: Int, radius: Int = 2): Job`
    - `fun downloadAll(widthPx: Int, concurrency: Int = 3): Flow<PackProgress>`, with `data class PackProgress(done, total, failed)`
    - `suspend fun svgFor(page: Int): File?`: from the LRU, fetched when online, otherwise null
    - `suspend fun cachedBytes(): Long`
    - `suspend fun clear()`
  - **Layout:** `root/mushaf/w<bucket>/NNN.<ext>` and `root/mushaf/svg/NNN.svg.gz`. The `.part` files are next to their targets.

- [ ] **Step 1: Write the failing tests.** These are the ported tests (same names, same expectations), plus:
  - `fetches, converts and keeps only the mask and a gz svg`
  - `svg cache keeps the 30 most recent`: after 31 pages, the first page's `svg.gz` is gone
  - `corrupt mask is re-made` (Review Focus 1): write the first 5 bytes of a valid mask, and `mask()` returns a decodable file after one more fetch
  - `other bucket returns exact=false and re-make deletes the old bucket file` (Review Focus 2)
  - `conversion never runs on the caller dispatcher`: inject a `cpu` test dispatcher and assert that the rasterize lambda ran on it
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement. Validate a mask by its header and its expected length before returning it.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Commit: `feat: page image store`.

### Task 14: Mushaf pager, page view, header and controller

**Files:**
- Create: `feature/mushaf/MushafController.kt`
- Create: `feature/mushaf/ui/{MushafScreen.kt,MushafPager.kt,MushafPageView.kt,PageBitmapCache.kt,Header.kt,Ornaments.kt}`
- Modify: `QuranTimeApp.kt` (`onTrimMemory` → `container.pageBitmaps.clear()`), `AppContainer.kt`
- Test: port these `FLUTTER/test/features/mushaf/` files:
  - `mushaf_controller_test.dart`
  - `mushaf_screen_test.dart` (RTL and LTR page direction: `reverse: ltr` in Flutter means the next page is to the left in both locales)
  - `offline_recovery_test.dart`, header cases 1–2 only; cases 3–4 come in Task 26
- Test: `PageBitmapCacheTest.kt`
- Create test support: `support/TestApp.kt` (the Kotlin `TestDeps`: an `AppContainer` built from FixtureApi, in-memory Room, a test DataStore, a fake page store, a FakeOnlineStatus and, later, a FakePlayerBackend; it starts the app with `createAndroidComposeRule`)

**Interfaces:**
- Consumes: `PageImageStore`, `SettingsStore`, `CatalogRepository`, `OnlineStatus`, `MushafIndex`.
- Produces:
  - `class MushafController(settings: SettingsStore, pages: PageImageStore, scope: CoroutineScope)` with:
    - `val currentPage: StateFlow<Int>`, `val followPaused: StateFlow<Boolean>`, `val jumpRequests: SharedFlow<Int>`
    - `pauseFollow()`, `resumeFollow()`, `onPageShown(page)` (saves lastPage and prefetches), `suspend goTo(page: Int, animate: Boolean = true)`
  - `class PageBitmapCache(maxEntries: Int = 5)` with `get(page, bucket)`, `put(…)` and `clear()`; it holds `ALPHA_8` bitmaps
  - `@Composable fun MushafPageView(page: Int, overlays: @Composable BoxScope.(scale: Float) -> Unit = {}, onTapViewBox: ((Offset) -> Unit)? = null)`. It draws the mask with `drawImage(bitmap, colorFilter = ColorFilter.tint(palette.ink))` inside a gilt frame, with the folio roundel under the page, laid out as `FLUTTER/lib/features/mushaf/ui/mushaf_page.dart` `_FittedPage`. The tap position is converted to viewBox units. A spinner appears only after 300 ms, and an offline error shows Retry.
  - `Header(suwarState, onRetry)`: the surah cartouche (tap opens the jump sheet in Task 16), the juz roundel, and the bookmark and play slots, which are filled in Tasks 15 and 26

- [ ] **Step 1: Write the failing tests:** the ported tests, plus
  - `PageBitmapCacheTest.`evicts beyond five``
  - `PageBitmapCacheTest.`trim memory clears`` (Review Focus 5)
  - `MushafScreenTest.`opens at the saved last page``
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement:
  - a `HorizontalPager` with `beyondViewportPageCount = 1` and `reverseLayout` per the Flutter rule; `key = page`
  - decode on `Dispatchers.Default`, with a `produceState` per page
  - the header auto-retries when `online` turns true after a failure (I5)
  - startup (spec §7.3): `MainActivity` keeps the splash on screen (`setKeepOnScreenCondition`) only until `settings.load()` completes. After the first frame (`Choreographer.postFrameCallback`), it launches the `suwar` prefetch on `Dispatchers.IO`. Nothing else runs in `onCreate`.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Commit: `feat: Mushaf reader`.

### Task 15: Bookmarks

**Files:**
- Create: `feature/bookmarks/{BookmarksRepository.kt,ui/BookmarksScreen.kt}`
- Modify: `Header.kt` (bookmark button), `AppNavHost.kt`
- Test: port `FLUTTER/test/features/bookmarks/bookmarks_repository_test.dart` and `bookmarks_flow_test.dart`

**Interfaces:**
- Produces: `class BookmarksRepository(dao: BookmarksDao, now: () -> Long, scope)` with:
  - `val items: StateFlow<List<Bookmark>>`
  - `hasPage(page)`
  - `suspend togglePage(page)`, `addAyah(surah, ayah, page)`, `restore(b)`, `remove(id)`
  - `data class Bookmark(id, page, surah?, ayah?, createdAtMs)` with `isAyah`
  - `addAyah` is a no-op when the same surah and ayah is already bookmarked; this fixes the Flutter double-bookmark minor

- [ ] **Step 1:** Write the ported tests plus `BookmarksRepositoryTest.`adding the same ayah twice keeps one``. Run them; expected FAIL.
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: bookmarks`.

### Task 16: Jump sheet and zoom

**Files:**
- Create: `feature/mushaf/ui/{JumpSheet.kt,ZoomLayer.kt}`
- Test: port `FLUTTER/test/features/mushaf/jump_sheet_test.dart`
- Test: `ZoomLayerTest.kt`

**Interfaces:**
- Consumes: `PageImageStore.svgFor`, `SvgRasterizer.render`.
- Produces:
  - `JumpSheet(onJump: (page: Int) -> Unit)`, with the same tabs and fields as `jump_sheet.dart`, accepting Arabic-Indic digits
  - `ZoomLayer`: pinch zoom up to 4×. When zoom settles above 1.25× for 150 ms, it renders `svgFor(page)` at 2.5× the bucket width on `Dispatchers.Default` and swaps it in. It frees that bitmap when zoom drops to ≤ 1.0×, and keeps the scaled mask when `svgFor` returns null.

- [ ] **Step 1:** Write the ported tests plus:
  - `ZoomLayerTest.`hi-res render appears after zoom settles and is dropped at 1x``
  - `ZoomLayerTest.`offline without svg keeps scaled mask``
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Commit: `feat: jump sheet and zoom`.

### Task 17: Mushaf benchmark gate

**Files:**
- Create: `benchmark/src/main/java/com/qurantime/kotlin/benchmark/SwipeBenchmark.kt`
- Modify: `StartupBenchmark.kt` (wait for `reportFullyDrawn`), `BaselineProfileGenerator.kt` (journey: start, swipe 10 pages, open the jump sheet)
- Modify: `MushafScreen.kt`: call `ReportDrawnWhen { firstPageDrawn }` (`activity-compose`)
- Create: `docs/perf/kotlin-phase2.md`

- [ ] **Step 1:** Write `SwipeBenchmark.swipe20()`: `FrameTimingMetric`, with pages 1–25 already on disk (setup opens them once), and 20 swipes with `UiDevice.swipe`.
- [ ] **Step 2:** Regenerate the baseline profile on the emulator, as in Task 2 Step 3.
- [ ] **Step 3:** Run both benchmarks on the A50, then `tools/perf.sh com.qurantime.kotlin docs/perf/kotlin-phase2.md`. Expected: startup median, `frameOverrunMs` and janky-frame percentage, `pss_mb` (no audio yet) and `apk_mb`, all recorded.
- [ ] **Step 4:** Compare with the targets and the Flutter baseline. **If startup, jank or APK size misses, stop and ask the user (K5).**
- [ ] **Step 5:** Commit: `perf: Mushaf benchmarks and baseline profile`.

---

## Phase 3 — Audio

### Task 18: Repeat session and sleep timer

**Files:**
- Create: `feature/audio/{AyahRepeat.kt,SleepTimer.kt}`
- Test: port `FLUTTER/test/features/audio/ayah_repeat_test.dart`. Also port the sleep-timer cases from `player_controller_test.dart` into `SleepTimerTest.kt`; the rest of that file is ported in Task 21.

**Interfaces:**
- Produces:
  - `sealed interface RepeatDecision { data object None; data class Seek(val ms: Long); data object Finished }`
  - `class AyahRepeatSession(startMs: Long, endMs: Long, times: Int)` with `remaining`, `onPosition(ms): RepeatDecision` and `onEnd(): RepeatDecision`
  - `class SleepTimer(scope: CoroutineScope, clock: () -> Long, onFire: () -> Unit)` with `state: StateFlow<SleepState>`, `start(d: Duration)`, `startEndOfTrack()`, `onTrackCompleted()` and `cancel()`
  - `data class SleepState(val endsAtMs: Long?, val endOfTrack: Boolean)` with `active`

- [ ] **Step 1:** Write the ported tests (time controlled by `runTest` virtual time); run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: ayah repeat and sleep timer`.

### Task 19: Player backend and queue handler

**Files:**
- Create: `feature/audio/{PlayerBackend.kt,QueueHandler.kt}`
- Test: port `FLUTTER/test/features/audio/quran_audio_handler_test.dart` → `QueueHandlerTest.kt`
- Create test support: `support/FakePlayerBackend.kt`, a port of `FLUTTER/test/support/fake_player_backend.dart` including `failSetSources` and `calls`

**Interfaces:**
- Produces:
  - `interface PlayerBackend` with:
    - `suspend setSources(urls: List<String>, startIndex: Int, startPositionMs: Long)`
    - `play()`, `pause()`, `stop()`, `seek(positionMs: Long, index: Int? = null)`, `setSpeed(Float)`, `setRepeat(RepeatMode)`, `release()`
    - flows: `positionMs: Flow<Long>`, `currentIndex: StateFlow<Int?>`, `state: StateFlow<BackendState>`, `durationMs: StateFlow<Long?>`, `errors: SharedFlow<Throwable>`
  - `enum class RepeatMode { OFF, ONE, ALL }`
  - `data class BackendState(val playing: Boolean, val processing: Processing)`, with `enum Processing { IDLE, LOADING, BUFFERING, READY, COMPLETED }`
  - `class QueueHandler(backend: PlayerBackend, localPathFor: suspend (String) -> String?, scope: CoroutineScope)`, which ports `QuranAudioHandler` exactly:
    - `items`, `currentItem`, `state: StateFlow<PlaybackSnapshot>`
    - `suspend playItems(items, startIndex, startPositionMs = 0)`, `suspend prepareItems(…): Boolean`
    - `play()`, `pause()`, `stop()`, `seek(ms)`, `seekToItem(ms, index)`, `skipToQueueItem(i)`, `skipToNext()`, `skipToPrevious()` (restarts when past 3 s), `setSpeed()`, `setRepeatMode()`
    - `val itemCompleted: SharedFlow<Int>`, for natural completions only (I3)
    - error streak capped at 2, keeping the resume point (I2)
  - `data class PlaybackSnapshot(playing, processing, queueIndex: Int?, speed: Float, repeat: RepeatMode, errorMessage: String?)`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement `QueueHandler`, with no Android types in it.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: queue handler`.

### Task 20: Media3 backend and playback service

**Files:**
- Create: `feature/audio/{Media3Backend.kt,PlaybackService.kt,MediaItems.kt}`
- Modify: `AndroidManifest.xml`:
  - permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`
  - `<service android:name=".feature.audio.PlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">` with the `androidx.media3.session.MediaSessionService` intent filter
- Create: `res/drawable/ic_stat_quran.xml` (a monochrome vector of the icon's star; this fixes the Flutter white-blob minor)
- Test: `Media3BackendTest.kt` (Robolectric with Media3 `TestExoPlayerBuilder`)

**Interfaces:**
- Consumes: `PlayerBackend` and `QueueHandler` (Task 19).
- Produces:
  - `class Media3Backend(player: ExoPlayer) : PlayerBackend`
  - `fun buildPlayer(context: Context): ExoPlayer`, with `setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH), handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)` and `setWakeMode(C.WAKE_MODE_NETWORK)`
  - `PlaybackService`, which owns the `MediaSession`, gets the player from `AppContainer` and stops itself in `onTaskRemoved` when not playing
  - `fun PlayableItem.toMediaItem(): MediaItem`: title and artist as in Flutter `_mediaItemFor`, with `isLive` for radio

- [ ] **Step 1: Write the failing tests:**
  - `buildPlayer handles audio focus and becoming noisy` (Review Focus 4: assert the player's `audioAttributes` and use `ShadowAudioManager` focus loss → `playWhenReady == false`)
  - `index and completion flows follow the player`
  - `radio media item is live`
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Add the lock-screen and Bluetooth, notification Stop, and kill-and-restore checks to `docs/manual-checks.md`.
- [ ] **Step 6:** Commit: `feat: Media3 playback service`.

### Task 21: PlayerController

**Files:**
- Create: `feature/audio/PlayerController.kt`
- Test: port the remaining cases of `FLUTTER/test/features/audio/player_controller_test.dart` (restore, range repeat, sleep at the end of a surah, skip handling)

**Interfaces:**
- Consumes: `QueueHandler`, `SleepTimer`, `AyahRepeatSession`, `SettingsStore`.
- Produces: `class PlayerController(handler: QueueHandler, settings: SettingsStore, requestNotificationPermission: suspend () -> Unit, scope: CoroutineScope)` with:
  - `state: StateFlow<PlayerUiState>`, where `PlayerUiState(item: PlayableItem?, playing, positionMs, durationMs, speed, repeat, isLive, error, repeating: Boolean, sleep: SleepState)`. Position is sampled at most every 250 ms for UI use only, so the whole app doesn't redraw every few seconds; this fixes the Flutter rebuild minor.
  - `positionMs: Flow<Long>`
  - `suspend playItems(items, startIndex = 0, startPositionMs = 0)`
  - `togglePlay()`, `seek(ms)`, `next()`, `previous()`, `setSpeed(f)`, `cycleRepeat()`, `stop()` (also cancels sleep and repeat; this fixes the Flutter Stop minor)
  - `suspend restore()`
  - `startRangeRepeat(startMs, endMs, times)`, `cancelRepeat()`
  - `val sleep: SleepTimer`

- [ ] **Step 1:** Write the ported tests plus `PlayerControllerTest.`stop cancels sleep and repeat``. Run them; expected FAIL.
- [ ] **Step 2:** Implement. The controller lives in `AppContainer` (app scope), and `MainActivity` calls `restore()` after the first frame (`Choreographer.postFrameCallback`).
  - `requestNotificationPermission` is backed by an `ActivityResultLauncher<String>` that `MainActivity` registers. It's a no-op below API 33 and asks once.
  - The `MediaController` (and so the `PlaybackService`) is connected only on the first `playItems` or `restore` that has something to restore.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: player controller`.

### Task 22: Mini and full player UI

**Files:**
- Create: `feature/audio/ui/{MiniPlayer.kt,FullPlayer.kt,SpeedSheet.kt,SleepSheet.kt}`
- Modify: `Shell.kt` (MiniPlayer between the offline banner and the nav bar), `AppNavHost.kt` (`PlayerRoute`)
- Test: port `FLUTTER/test/features/audio/player_ui_test.dart`, keeping the transport controls LTR in Arabic

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. The slider and the position text read `positionMs` inside their own small composables, so a position tick redraws only them.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: player UI`.

---

## Phase 4 — Follow-along

### Task 23: Ayah geometry and hit testing

**Files:**
- Create: `feature/mushaf/{AyahGeometryRepository.kt,AyahHitTest.kt}`
- Test: port `FLUTTER/test/features/mushaf/ayah_geometry_test.dart`

**Interfaces:**
- Produces:
  - `data class AyahShape(surah, ayah, page, polygon: List<Offset>, marker: Offset?)`
  - `fun hitTest(shapes: List<AyahShape>, p: Offset): AyahShape?`
  - `class AyahGeometryRepository(timing: TimingRepository, dao: AyahGeometryDao, suwar: suspend () -> List<Surah>, referenceReadId: Int = 5)` with `forPage(page)`, `find(surah, ayah)`, `ensureSurah(surah)` and `ensureAll(onProgress)`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: ayah geometry`.

### Task 24: FollowAlongController

**Files:**
- Create: `feature/followalong/FollowAlongController.kt`
- Test: port `FLUTTER/test/features/follow_along/follow_along_controller_test.dart`

**Interfaces:**
- Consumes: `PlayerController`, `TimingRepository`, `TimedReadMatcher`, `CatalogRepository`, `SettingsStore`.
- Produces: `class FollowAlongController(player, timing, matcher: suspend () -> TimedReadMatcher, suwar: suspend () -> List<Surah>, settings, scope)` with:
  - `state: StateFlow<FollowState>`, where `FollowState(active, readId?, surah?, timings, current: AyahTiming?, currentPage: Int?)`
  - `suspend playFromAyah(surah, ayah): Boolean`
  - `suspend ayahsOf(surah): List<Int>`
  - `suspend repeatAyahs(surah, from, to, times): Boolean`
  - The matcher is memoised in `AppContainer`, and a failure clears the memo (as `main.dart`).

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: follow-along controller`.

### Task 25: Highlight, auto page turn, actions and repeat sheet

**Files:**
- Create: `feature/mushaf/ui/{HighlightOverlay.kt,AyahActionsSheet.kt,BackToRecitation.kt}`, `feature/followalong/ui/RepeatSheet.kt`
- Modify: `MushafScreen.kt`, `MushafPager.kt` (track user versus programmatic page changes and call `pauseFollow`)
- Test: port these `FLUTTER/test/features/mushaf/` files: `follow_along_ui_test.dart`, `ayah_actions_test.dart`

**Interfaces:**
- Produces: `HighlightOverlay(shapes, current, scale)`, a Compose `Canvas` drawing `palette.highlight` paths in viewBox units × scale; `AyahActionsSheet(shape)`; `RepeatSheet(surah)`.

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: follow-along UI`.

### Task 26: Mushaf reciter, play page and review fixes

**Files:**
- Create: `feature/settings/ui/MushafReciterScreen.kt`
- Modify: `Header.kt` (play/pause button), `MushafScreen.kt`
- Test: port these `FLUTTER/test/features/mushaf/` files: `mushaf_reciter_and_play_test.dart`, `review_fixes_test.dart`, and `offline_recovery_test.dart` cases 3–4 (offline taps say so: I6)

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. This includes the "reciter lacks this surah" SnackBar with its Change action, which opens `MushafReciterRoute`.
- [ ] **Step 3:** Run them (expected PASS). Add the Al-Ajmi Al-Baqarah follow-along check (highlight, page turns, tap ayah 255) to `docs/manual-checks.md`. Commit: `feat: Mushaf reciter and play page`.

---

## Phase 5 — Listen and downloads

### Task 27: Reciters list, filters, favourites, recent

**Files:**
- Create: `feature/reciters/{ReciterFilter.kt,RecitersListViewModel.kt}` and `feature/reciters/ui/{RecitersScreen.kt,RecentScreen.kt}`
- Test: port these `FLUTTER/test/features/reciters/` files: `reciter_filter_test.dart`, `reciters_screen_test.dart`

**Interfaces:**
- Produces:
  - `data class ReciterFilter(query = "", riwayaId: Int? = null, moshafTypeId: Int? = null, timedOnly = false, favoritesOnly = false)` with `isEmpty` and `keep(r)`
  - `fun applyReciterFilter(all, filter, favoriteIds): List<Reciter>`: favourites first, a stable sort
  - `class RecitersListViewModel(...)` with `state: StateFlow<RecitersListState>`, `load(lang)`, `setFilter(f)`, `toggleFavorite(id)`, `isTimed(m)`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement with a `LazyColumn` (`key = reciter.id`, `contentType`).
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: reciters list`.

### Task 28: Reciter detail and surah list

**Files:**
- Create: `feature/reciters/ui/{ReciterScreen.kt,MoshafSurahsScreen.kt}`
- Test: port `FLUTTER/test/features/reciters/reciter_detail_test.dart`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. The download button slot is filled in Task 30.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: reciter detail`.

### Task 29: Downloader and DownloadsRepository

**Files:**
- Create: `feature/downloads/{Downloader.kt,WorkManagerDownloader.kt,DownloadWorker.kt,DownloadsRepository.kt}`
- Test: port `FLUTTER/test/features/downloads/downloads_repository_test.dart` (including reconcile, C1)
- Test: `DownloadWorkerTest.kt` (WorkManager `TestListenableWorkerBuilder` with MockWebServer)
- Create test support: `support/FakeDownloader.kt` (port of `fake_downloader.dart`, with `statuses`)

**Interfaces:**
- Produces:
  - `interface Downloader` with:
    - `suspend enqueue(url: String, target: File, title: String): String /* taskId */`
    - `pause(taskId)`, `resume(taskId)`, `cancel(taskId)`
    - `suspend statusOf(taskId): DownloadStatus?`
    - `val events: Flow<DownloadEvent>`, where `DownloadEvent(taskId, status, received?, total?)`
  - `WorkManagerDownloader` uses unique work per URL. The worker runs in the foreground with a notification and reports progress. Resuming uses a `Range` header from the `.part` length. On success it renames `.part` to the target.
  - `class DownloadsRepository(dao, downloader, root: File, scope)`. Its method names follow the Dart file: `settled()`, `all()`, `reconcile()`, `watchAll()`, `enqueueSurah(t: SurahTrack)`, `enqueueMoshaf(r, m, suwar)`, `localPathFor(url): String?`, `pause(id)`, `resume(id)`, `cancel(id)`, `deleteMoshaf(moshafId)`, `bytesByMoshaf()`.
  - Event handling is serialised through one `Mutex`, which fixes the Flutter concurrent-events minor.
  - `localPathFor` returns null when the file has gone, which fixes the Flutter queued-file-deleted minor.

- [ ] **Step 1:** Write the ported tests plus:
  - `DownloadWorkerTest.`resumes from the part file with a Range header``
  - `DownloadsRepositoryTest.`events for one row are applied in order``
  - `DownloadsRepositoryTest.`deleted file reports no local path``
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement. `AppContainer` calls `reconcile()` after the first frame.
- [ ] **Step 4:** Run them; expected PASS.
- [ ] **Step 5:** Commit: `feat: downloads`.

### Task 30: Downloads UI and the page pack

**Files:**
- Create: `feature/downloads/ui/{DownloadsScreen.kt,DownloadButton.kt}`, `feature/mushaf/image/PagePackWorker.kt`
- Modify: `MoshafSurahsScreen.kt` (download buttons)
- Test: port `FLUTTER/test/features/downloads/downloads_ui_test.dart`
- Test: `PagePackWorkerTest.kt`

**Interfaces:**
- Consumes: `PageImageStore.downloadAll`, `DownloadsRepository`.
- Produces: `PagePackWorker`, a unique foreground CoroutineWorker named `page-pack` that reports `PackProgress` as `setProgress` data (`done`, `total`, `failed`) and can be cancelled. Downloads shows its progress, `cachedBytes()` and "Re-make for this screen" (spec §7.4).

- [ ] **Step 1:** Write the ported tests plus:
  - `PagePackWorkerTest.`disk full on one page counts it failed and continues`` (Review Focus 3): the fetcher throws `IOException("ENOSPC")` for page 7; afterwards `failed == 1`, there's no `007.*.part`, and every other page has a mask
  - `PagePackWorkerTest.`cancel stops and keeps finished pages``
- [ ] **Step 2:** Run them; expected FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run them; expected PASS. Add the airplane-mode playback and offline reading check to `docs/manual-checks.md`.
- [ ] **Step 5:** Commit: `feat: downloads UI and page pack`.

---

## Phase 6 — Live and Explore

### Task 31: Radio, live TV and the video player

**Files:**
- Create: `feature/live/ui/{LiveScreen.kt,RadioList.kt,LiveTvList.kt}`, `feature/video/{VideoPlayerScreen.kt,VideoPlayerFactory.kt}`
- Test: port these `FLUTTER/test/features/live/` files: `radio_list_test.dart`, `live_tv_test.dart`
- Create test support: `support/FakeVideoPlayerFactory.kt` (port of `fake_video.dart`)

**Interfaces:**
- Produces:
  - `fun interface VideoPlayerFactory { fun create(context: Context, url: String, live: Boolean): Player }`. The default builds a separate ExoPlayer with the HLS module and releases it on dispose.
  - Radio plays through `PlayerController.playItems(listOf(RadioStream(...)))`.

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. The video screen uses `PlayerView` in `AndroidView` and is full screen in landscape.
- [ ] **Step 3:** Run them (expected PASS). Add the radio-in-background and both TV channels checks to `docs/manual-checks.md`. Commit: `feat: live radio and TV`.

### Task 32: Explore: videos, tafsir and tadabor

**Files:**
- Create: `feature/explore/ui/{ExploreScreen.kt,VideosScreen.kt,TafsirScreen.kt,TadaborScreen.kt}`
- Test: port `FLUTTER/test/features/explore/explore_test.dart`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement. Thumbnails use Coil `AsyncImage` with a size set from layout. Tadabor video clips open `VideoRoute`, and audio clips play as `AudioClip`.
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: explore`.

---

## Phase 7 — Settings, release and the final gate

### Task 33: Settings, about and More

**Files:**
- Create: `feature/settings/ui/{SettingsScreen.kt,AboutScreen.kt}`, `feature/more/MoreScreen.kt`
- Test: port `FLUTTER/test/features/settings/settings_screen_test.dart`

- [ ] **Step 1:** Write the ported tests; run them (expected FAIL).
- [ ] **Step 2:** Implement:
  - segmented buttons for language and theme
  - a language change applies per-app locales and recreates the activity
  - About shows `BuildConfig.VERSION_NAME` and opens mp3quran.net with `Intent.ACTION_VIEW`; the manifest gets a `<queries>` entry
- [ ] **Step 3:** Run them (expected PASS). Commit: `feat: settings and about`.

### Task 34: Release build, icon and store docs

**Files:**
- Create:
  - `res/mipmap-anydpi-v26/ic_launcher.xml` with foreground, background and monochrome layers
  - `res/drawable/splash_icon.xml`
  - `res/values/themes.xml` and `res/values-night/themes.xml` (splash ivory `#F4EEDD`, night `#121212`)
  - `app/proguard-rules.pro`: only what kotlinx.serialization and AndroidSVG need
- Create: `docs/privacy-policy.md` and `docs/store-listing.md`, copied from FLUTTER. Update the permissions list to match this manifest. The listing keeps the same feature bullets.
- Test: `ReleaseConfigTest.kt` (reads the merged manifest: `INTERNET` present, no `android.permission.READ_*` or location permissions, the service is declared with `mediaPlayback`)

- [ ] **Step 1:** Write `ReleaseConfigTest.`manifest has only the permissions the app needs``. Run it; expected FAIL.
- [ ] **Step 2:** Create the icon resources from `FLUTTER/assets/icon/*.png`, and the rest of the files.
- [ ] **Step 3:** Run the test (expected PASS), then `./gradlew :app:bundleRelease :app:assembleRelease`. Expected: BUILD SUCCESSFUL. The release APK installs on the A50 with `adb install -r`. A fresh launch (`adb logcat -c`, start, then `adb logcat -d *:E | grep com.qurantime.kotlin`) shows 0 errors.
- [ ] **Step 4:** Commit: `chore: release build, icon and store docs`.

### Task 35: Final benchmarks and parity check

**Files:**
- Create: `docs/perf/kotlin-final.md`, `docs/parity.md`
- Modify: `docs/manual-checks.md`

- [ ] **Step 1:** Regenerate the baseline profile on the emulator, then rebuild the release.
- [ ] **Step 2:** On the A50, run `StartupBenchmark`, `SwipeBenchmark` and `tools/perf.sh com.qurantime.kotlin docs/perf/kotlin-final.md` (memory with "Play page" running on page 3). Then run `du -sh` of `files/mushaf/w*` after the page pack, via `adb shell run-as com.qurantime.kotlin du -sh files/mushaf`. Expected: all five metrics recorded next to the Flutter baseline.
- [ ] **Step 3:** **If any metric misses its target, stop and ask the user (K5)**, giving the table.
- [ ] **Step 4:** Write `docs/parity.md`. List every Dart test file next to its Kotlin test class, with the count of ported tests, and every replaced test with its ruling. Expected: every Dart test is accounted for.
- [ ] **Step 5:** Run the full suite with `./gradlew :app:testDebugUnitTest lint`. Expected: all tests pass and lint reports no errors.
- [ ] **Step 6:** Commit: `docs: final performance and parity report`. The executor then runs the final whole-branch review and the finishing skill. The user creates the keystore and `key.properties` themselves; leave that to them.
