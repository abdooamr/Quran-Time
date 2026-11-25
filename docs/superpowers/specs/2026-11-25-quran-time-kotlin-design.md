# Quran Time (Kotlin) — Design

Date: 2026-11-25
Status: Draft, awaiting approval
Feature authority: the Flutter app in this repo (`lib/`), its spec `docs/superpowers/specs/2026-11-16-quran-time-design.md`, and the rulings and final-review fixes in `docs/superpowers/ledger-2026-11-16-quran-time.md`.

---

## 1. Intent

A second, native Android version of Quran Time, written in Kotlin, with **exactly the same features and behaviour** as the finished Flutter app. Nothing new, nothing removed. The point is the maximum speed, smoothness and lightness Android can give: a small download, a fast cold start, page swipes with no dropped frames, low memory and battery use.

The Flutter app stays as it is and remains the iOS path. The Kotlin app is Android only.

## 2. Goals

1. **Feature parity** with the Flutter app: every screen, flow, setting, error state and fix listed in the feature authority (above). Section 8 gives the inventory.
2. **Measured performance targets** on the user's phone (Samsung Galaxy A50, SM-A505FN, Android 12, 3.5 GB RAM), each compared with the Flutter app measured on the same phone first:

| Metric | Target |
|---|---|
| Download size (arm64 APK from the AAB) | ≤ 8 MB |
| Cold start to the last Mushaf page drawn (page already on disk) | ≤ 600 ms, and faster than Flutter |
| Page swipe | ≤ 1% janky frames (Macrobenchmark `FrameTimingMetric`), no blank page when the next page is on disk |
| Memory with the Mushaf open and audio playing | ≤ 120 MB PSS |
| Storage for all 604 pages | ≤ 100 MB (Flutter's SVGs take about 180 MB) |

3. The same look: the "Madinah Mushaf Edition" design in `DESIGN.md` (ivory `#F4EEDD`, sepia `#EBDDC2`, dark `#121212`, gilt only on rules, Amiri font), with the same light/sepia/dark themes and full Arabic RTL.
4. Store-ready: release signing from `key.properties`, privacy policy, store listing (reuse the Flutter docs).

## 3. Non-goals

- iOS, Kotlin Multiplatform, Compose Multiplatform.
- Any feature the Flutter app doesn't have: no Khatmah, widgets, Android Auto, Wear OS, text mode, Tanzil, search, translations. These can follow once parity ships.
- Fixing the 15 deferred minors from the Flutter ledger is **not required**, but where the native stack makes one disappear for free (notification icon, notification Stop, double bookmark), do it.
- Moving user data from the Flutter app into the Kotlin app.

## 4. Anti-goals: failure even if it "works"

- Any behaviour that differs from the Flutter app without a recorded reason.
- A heavier stack than needed: no Hilt/Dagger, no RxJava, no bundled SQLite, no native `.so` libraries unless a measurement justifies one.
- Shipping the Mushaf images inside the APK, or depending on any server other than mp3quran.net.
- Converting pages on the main thread, or holding full-colour (ARGB) page bitmaps when an 8-bit mask will do.
- Performance claims without a Macrobenchmark or `dumpsys` number from the A50 behind them.
- Ads, analytics, crash reporting, accounts.
- Generating or storing the user's signing passwords. The user creates the keystore.

## 5. Decisions made with the user

| # | Decision |
|---|----------|
| K1 | Reimplement the whole app in Kotlin, same features, as a separate project. |
| K2 | **Android only**, Jetpack Compose (picked over Kotlin Multiplatform). |
| K3 | Mushaf pages become **lightweight images**, made on the phone from the SVG once, sized to the screen. |
| K4 | Plan only for now; implementation starts when the user says so. |
| K5 | **The section 2 targets are hard gates.** When a measured number misses a target, the build stops and asks the user. This is the one stop in an otherwise unattended run. Feature parity is never traded away to meet a target without the user's say. |
| K6 | **The build runs unattended**, like the Flutter one: no stops at phase ends. Benchmarks run whenever the phone allows it. Manual device checks and anything needing the user's hands are collected and listed at the end. |

Everything decided in the Flutter spec (§5 there: D1–D11) still holds, except D12/D13 (Provider and the Flutter libraries), which section 7 replaces.

## 6. Approaches considered

**Platform** (user chose A)
- A. **Android only, Jetpack Compose.** Smallest and fastest; uses Android's own audio, database and download tools.
- B. Kotlin Multiplatform with Compose Multiplatform: shares iOS, but it's about 30–40% more work, needs a Mac, and is slightly heavier on Android.
- C. KMP shared logic with native UIs on each platform: the most work of the three.

**Mushaf pages** (A chosen; the user suggested images)
- A. **SVG to 8-bit mask image once per page, on the phone, at the screen's pixel width.** Measured on page 300: the 567 KB SVG becomes a 1080×1721, 16-level grey image of 134 KB (WebP lossless). It draws without parsing, tints by paint colour without an extra pass, and takes 1.9 MB in memory as `ALPHA_8` (7.4 MB as ARGB).
- B. Render the SVG every time (AndroidSVG to a `Picture`): parsing a 500 KB file costs tens of ms per page, so swipes would jank. Rejected.
- C. Images made in advance and hosted or bundled: needs a server or adds about 80 MB to the APK, and re-hosts the Mushaf files. Rejected.

**Dependency injection:** A. **a hand-written `AppContainer`** (the Flutter app's `main.dart` wiring, in Kotlin), chosen: no annotation processing, faster builds and startup. B. Hilt: standard, but heavier and adds build time. C. Koin: runtime lookup, nothing gained here.

**Settings storage:** A. **Preferences DataStore**, chosen: the Android standard for key–value settings. B. A Room table, like the Flutter app: works, but blocks on DB open at startup.

## 7. Design

### 7.1 Project

- Location: `D:\AAAAAAProjects\GIT-HUB\Quran-Time-Kotlin`, its own git repo (initialised with this spec and the plan). Branch work as in the Flutter build.
- applicationId **`com.qurantime.kotlin`**, so it installs next to the Flutter app (`com.qurantime.app`) for side-by-side comparison. Switching it to `com.qurantime.app` to replace the Flutter app on Play is one line, and the user's call later.
- Gradle Kotlin DSL with a version catalog. Kotlin 2.x, AGP and Compose BOM at their latest stable versions. minSdk 23 (as the Flutter app), target/compile SDK 36. JDK 17 is installed.
- Modules: `:app`, `:baselineprofile` (generates the Baseline Profile on the A50), `:benchmark` (Macrobenchmark: startup and page swipes). No other modules.
- Packages mirror the Flutter features: `core/{api,cache,db,settings,theme,l10n,ui,net}` and `feature/{mushaf,audio,followalong,timing,catalog,reciters,downloads,live,explore,video,bookmarks,settings}`.

### 7.2 Libraries

| Need | Library | Replaces (Flutter) |
|---|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe routes) | widgets, go_router |
| State | `ViewModel` + `StateFlow`, coroutines | provider / ChangeNotifier |
| HTTP | OkHttp + kotlinx.serialization (a small `ApiClient`, no Retrofit) | dio |
| Database | Room (KSP), using Android's own SQLite | drift + sqlite3_flutter_libs |
| Settings | Preferences DataStore | settings table |
| Audio | Media3 ExoPlayer + `MediaSessionService` | just_audio + audio_service |
| Video and live TV | Media3 ExoPlayer + `PlayerView` (HLS module) | video_player |
| Downloads | WorkManager (foreground worker) with OkHttp, `.part` then rename | background_downloader |
| SVG to image | AndroidSVG (pure Java, no native code) | flutter_svg |
| Images (reciter/video thumbnails, if used) | Coil 3 | — |
| Connectivity | `ConnectivityManager.NetworkCallback` | connectivity_plus |
| Startup | `androidx.core:core-splashscreen`, `profileinstaller` | native splash |
| Tests | JUnit4, kotlinx-coroutines-test, Turbine, Robolectric, Compose UI test, OkHttp MockWebServer, Room in-memory | flutter_test |

### 7.3 Startup

`Application.onCreate` builds the `AppContainer` lazily: nothing touches disk or network there. The splash screen stays up only until settings are read from DataStore, which gives the last page and theme. The Mushaf screen then draws the cached page image. Everything else starts after the first frame, off the main thread:

- the download reconcile (C1 fix: downloads that finished while the app was closed);
- the player restore (paused, at the saved position);
- the catalog prefetch.

The MediaSessionService is created only when something plays or is restored. A Baseline Profile covers start-up and the Mushaf swipe path. R8 runs in full mode with resource shrinking.

### 7.4 Mushaf pages (images)

Pipeline per page (one class, `PageImageStore`, replacing `MushafPageStore`):

1. **Fetch:** download `quran_pages_svg/NNN.svg` with OkHttp (gzip on the wire), written to `.part` then renamed. In-flight requests are de-duplicated, as in Flutter.
2. **Convert:** on `Dispatchers.Default`, at most 2 at once. AndroidSVG renders the page into a bitmap whose width is the page area's width in pixels (a "render width" bucket, rounded up to 120 px). The alpha channel is then quantised to 16 levels.
3. **Store:** save the 8-bit mask to `pages/w<width>/NNN.<ext>`. The encoding is whichever is smaller *and* decodes in ≤ 8 ms on the A50: WebP lossless (via an ARGB copy), or the raw alpha bytes deflated. The plan's first Mushaf task measures both. The mask is the only permanent copy of the page (604 masks ≈ 80 MB). The SVG is kept gzipped (`svg/NNN.svg.gz`, about 150 KB) only in a small LRU of the 30 most recently opened pages (≈ 5 MB), for zoom. *(Claude's call: keeping every SVG would cost about 90 MB more and break the storage target.)*
4. **Show:** decode as an `ALPHA_8` bitmap and draw it with `Paint(color = palette.ink)`, so theme tinting is free. An in-memory LRU holds the current page ±2, about 10 MB. When the user swipes, the pages either side are prefetched and converted, as `prefetchAround` does in Flutter.
5. **Zoom** (pinch, up to 4×, as in Flutter): when zoom settles above 1.25×, render that page from its SVG once at 2.5× width into a temporary bitmap. It's dropped when zoom returns to 1×. If the SVG isn't in the LRU, fetch it when online. Offline, keep showing the scaled mask, which is slightly soft above 2×.
   **Screen-size change** (rotation, fold, a different render-width bucket): pages show their existing mask scaled until they're re-made. A pending re-make of a page happens the next time it's online and opened. The page pack offers "Re-make for this screen", which re-downloads.
6. **Overlays:** ayah highlight polygons and tap hit-testing use the timing data's viewBox coordinates, scaled by `bitmapWidth / viewBox.width`. This uses the same viewBox rules as Flutter: 235×235 for pages 1–2, 345×550 for the rest.
7. **Page pack** ("download all pages", in Downloads): a WorkManager foreground job fetches and converts every missing page with progress. It can be cancelled and resumed.

Flutter's `MushafPageStore` tests (in-flight de-duplication, `.part` cleanup, prefetch, pack progress and failures, clear and size) port over to this class.

### 7.5 Audio

- A `PlaybackService : MediaSessionService` holds one ExoPlayer, which plays local files when downloaded (`localPathFor`) and streams otherwise.
- A `PlayerController` mirrors the Flutter class with the same behaviour and fixes:
  - queue of surahs from one recording;
  - speed, repeat surah, and sleep timer (minutes or end of surah);
  - ayah/range repeat N times, including a range that ends at the end of the file (I4 fix);
  - error streak capped at 2, keeping the resume point (I2 fix);
  - "item completed" only on natural completion, not skips (I3 fix);
  - restore after the app is killed, paused (I2/restore);
  - the notification permission is requested on first play.
- Media3 provides the lock-screen, Bluetooth and notification controls. Custom session commands are used only where Flutter had a custom action.

### 7.6 Everything else (parity inventory)

Port each with the same screens, routes, strings (`app_ar.arb` / `app_en.arb` into `values-ar/strings.xml` / `values/strings.xml`), empty and error states and tests:

- **Shell:** five tabs (the same tabs and order as `lib/core/router.dart`). The bottom bar holds the offline banner, the mini player and the navigation bar. Full-screen player and video routes.
- **Mushaf:**
  - header: the surah cartouche, which opens the jump sheet; the juz roundel; bookmark; play/pause; Retry and auto-retry after an offline first launch (I5);
  - jump sheet, ayah actions sheet, bookmarks;
  - follow-along: highlight, auto page turn, pausing on a manual swipe, "Back to recitation", tap an ayah to play from it;
  - offline taps show the generic error (I6);
  - the Mushaf reciter setting (default read 5, Al-Ajmi), with the "reciter lacks this surah" SnackBar and its Change action.
- **Listen:** recent; reciters list with riwaya/style filters, favourites first, and the "follows the Mushaf" marks; reciter, then recording, then surahs, with download buttons.
- **Live:** radio (favourites first, playing in the background with the station shown in the notification) and the two HLS TV channels.
- **Explore:** videos, tafsir (Al-Tabari) and tadabor. Tadabor video clips open in the video player, audio clips in the audio player.
- **More:**
  - bookmarks;
  - downloads, with the storage screen, the page pack, and per-surah and whole-recording downloads;
  - Mushaf reciter;
  - settings: language (ar/en) and theme (system/light/sepia/dark), both as segmented buttons;
  - about, with the version and a link to mp3quran.net.
- **Data layer:**
  - `ApiClient` with retry;
  - `CachedFetcher` with the same TTLs (fresh, stale fallback, forever for timing);
  - the reciters delta update (`last_updated_date = fetchedAt − 1 day`);
  - `OnlineStatus`;
  - Room tables for API cache, bookmarks, favourites, downloads and ayah geometry. Settings and the resume point are in DataStore.
  - Room gets a real `Migration` path from version 1. The enum is stored by name, not by index, which fixes a Flutter minor.
- **API facts** from Flutter spec §7 plus the ledger's live changes: audio on `cdn.mp3quran.net/audio/<slug>/r1/`, the tafsir `tafasir` key, tadabor as video, and `www` for the base URL. The JSON fixtures in the Flutter `test/` folder are copied and reused.

### 7.7 Testing and measuring

- **Test-first** for every unit, as in the Flutter build. The Flutter test files (43 files, 252 tests) are the checklist: each behaviour tested there gets a Kotlin test, either a JVM unit test or a Robolectric/Compose test.
- **Fakes** mirror `test/support/`: a fake player backend, a fake downloader, an in-memory page store, and fixture-backed MockWebServer.
- **Before Kotlin work starts, measure the Flutter app on the A50** and record the numbers; they are the baseline section 2 compares against. `am start -W` stops at Flutter's first frame, and `gfxinfo` doesn't see Flutter's frames, so a **measurement-only Flutter build** on a throwaway branch (`measure/baseline`, never merged) adds two hooks:
  - `reportFullyDrawn` once the Mushaf page is drawn, so both apps report "fully drawn";
  - a `FrameTiming` logger that writes the percentage of frames over 16.6 ms during the same 20-page swipe.

  Memory uses `dumpsys meminfo` and size uses the arm64 APK built from the AAB, the same for both apps. *(Claude's call.)*
- **Macrobenchmarks** on the A50: cold start, and swiping 20 Mushaf pages with the pages on disk. Run them at the end of each phase, and record the results in the ledger. Phase 1 first checks that instrumented swipes work on the A50 (Samsung blocked `adb shell input` during the Flutter build; Macrobenchmark uses UiAutomator, which may still work). If they don't, the benchmarks are batched for a person at the phone.
- **Baseline Profile:** generated on an API 34+ emulator, because generating one on an unrooted Android 12 phone isn't supported. Profiles are portable, so it is still *installed and measured* on the A50. *(Claude's call.)*
- **Manual device checks:** the same list as the Flutter build (lock screen and Bluetooth, kill and restore, follow-along on Al-Baqarah, offline playback, radio in the background, TV, the notification clearing on Stop).

### 7.8 Build phases

1. **Measure Flutter; set up the project:** Gradle, catalog, the baseline-profile and benchmark modules, theme, strings, shell with five empty tabs, CI-free local scripts.
2. **Data layer:** ApiClient, CachedFetcher, Room, DataStore, models and repositories, all with ported tests.
3. **Mushaf:** the page image pipeline (with the encoding measurement), the pager, header, jump sheet, bookmarks, zoom, and the first startup and swipe benchmark.
4. **Audio:** PlaybackService, PlayerController, the mini and full player, sleep timer, speed and repeat.
5. **Follow-along:** timing, geometry, highlight, auto turn, tap to play, range repeat, the Mushaf reciter.
6. **Listen and downloads:** reciters, filters, favourites, WorkManager downloads, reconcile, storage, the page pack.
7. **Live, Explore and video.**
8. **Settings, about, polish:** the final benchmarks against the section 2 targets, release build, signing hook-up, store docs, final review.

## 8. Left to the builder (Claude's calls, open to change)

- The exact page-image encoding (§7.4 step 3), decided by measurement.
- Exact library versions: latest stable at the time Phase 1 starts.
- Compose screen structure and ViewModel boundaries, as long as behaviour matches Flutter.
- Where Compose/Material 3 defaults differ visually from the Flutter widgets, match `DESIGN.md`, not pixel-for-pixel Flutter.
- The applicationId stays `com.qurantime.kotlin` until the user decides whether this version replaces the Flutter app on Play.
- Whether the 3 "free" Flutter minors in §3 are fixed: yes, where it costs nothing extra.
