# Checks that need the phone or a person

Items marked [x] were run on the Galaxy A50 over adb on 2026-11-26. The rest need hands on the phone (lock screen, Bluetooth, TalkBack, browsing the Explore screens).

Collected during the unattended build (spec K6). Each line says what to do and what to expect.

## Pending device runs (the Galaxy A50 was not connected)

- [ ] Task 2: `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.benchmark.StartupBenchmark` with the A50 unlocked. Expected: a JSON result under `benchmark/build/outputs/connected_android_test_additional_output/`. If it fails on input injection, benchmarks are run by hand from this list.
- [x] Task 10 (done 2026-11-26: 7.3 ms median): page-image decode time on the A50: `ANDROID_SERIAL=R58M389X2XD ./gradlew :app:connectedDebugAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.MaskCodecDeviceTest`, then `adb logcat -d | grep codec=`. Expected: `codec=qtr` lines with `decodeMedianMs` ≤ 8 (it measured 5.2–8.0 ms on the emulator in a debug build).

## Playback (Task 20)

- [ ] Lock-screen controls: play a surah, lock the phone. Expected: play/pause, previous and next work from the lock screen; the title is the surah and the reciter.
- [ ] Bluetooth: play through a headset or car. Expected: its play/pause and skip buttons work; unplugging or disconnecting pauses.
- [ ] Notification Stop: Expected: playback stops and the notification goes away; the sleep timer and any repeat are cancelled.
- [x] Kill and restore: play, swipe the app away while paused, reopen. Expected: the same surah and position are loaded, paused. — **A50, 2026-11-26:** restored paused (session state 2).

## Downloads (Task 30)

- [ ] Download a surah, then turn on airplane mode. Expected: it plays from the phone.
- [x] More → Downloads → "Download all pages", then airplane mode. Expected: every page opens, and tapping an ayah still shows its actions (outlines were saved too). — **A50, 2026-11-26:** pages open offline and an ayah's actions show (Wi-Fi off).

## Live (Task 31)

- [ ] Play a radio station, lock the phone. Expected: it keeps playing, with controls in the notification.
- [x] Open "Quran channel" and "Sunna channel". Expected: each plays; any recitation stops first; turning the phone sideways fills the screen and hides the system bars; Back returns to the list. — **A50, 2026-11-26:** Quran channel plays live; the recitation stopped first.
- [ ] Start a channel, press Home, come back. Expected: the video paused while hidden and goes on when back.

## Explore (Task 32)

- [ ] Explore → Videos: thumbnails load; a type chip narrows the grid; a video plays full screen.
- [ ] Explore → Tafsir → a collection → a surah → a clip. Expected: it plays in the mini player.
- [ ] Mushaf → long-press an ayah → "Tafsir of this surah". Expected: that surah's tafsir clips open.
- [ ] Explore → Reflections → a surah → Play. Expected: video clips open the video player, audio clips play.

## Settings and About (Task 33)

- [x] Settings → English. Expected: the whole app (including sheets, dialogs and the notification) switches to English, left-to-right, and stays English after closing and reopening. — **A50, 2026-11-26:** app and ayah sheet switch to English, LTR; back to Arabic restores RTL.
- [ ] Settings → each theme. Expected: System / Light / Sepia / Dark apply at once, including the Mushaf page ink.
- [ ] About → "mp3quran.net". Expected: the browser opens the site.

## Release (Task 34)

- [ ] Home screen: the icon is the green star-in-ring; on Android 13+ with themed icons on, it shows as a monochrome star.
- [ ] Cold start: the splash shows the star on a green disc over ivory (dark: over #121212), then the Mushaf.
- [ ] Create the upload keystore and `key.properties` yourself (never committed), then `./gradlew :app:bundleRelease` for Play.

## After the final review (fix pass)

- [x] Theme = System, app open: switch the phone's dark mode. Expected: the app follows at once. — **A50, 2026-11-26:** follows live, dark → light → dark (after the AppLocale fix).
- [x] Play a surah → Live → a TV channel → Back → Play in the mini player. Expected: the recitation plays (not silent). — **A50, 2026-11-26:** one tap plays (after the idle-is-not-playing fix).
- [x] Notification Stop, reopen the app, Play. Expected: it plays. — **A50, 2026-11-26:** media Stop key → idle; reopen + one tap plays.
- [x] Read in landscape, swipe a few pages. Expected: pages sharp, memory stays near portrait levels (`dumpsys meminfo`), "saved on this phone" barely grows. — **A50, 2026-11-26:** works; 118 MB; the page is small in landscape (header, mini player and tabs take the height).
- [ ] Start "Download all pages", then Cancel. Expected: it stops; pages already saved stay.

## Reading along and the players (design revision)

- [x] Play a page with a timed reciter. Expected: the ayah's wash fills right to left, line by line, in time; the next ayah takes over with a soft fade. — **A50, 2026-11-26:** sweep fills right to left in time.
- [x] Settings → Accessibility → Remove animations, then play. Expected: the whole ayah is lit at once; no sweep. — **A50, 2026-11-26:** whole ayah lit at once, no sweep.
- [x] Tap an ayah. Expected: a gilt hairline outlines it while its actions are open. — **A50, 2026-11-26:** gilt hairline + faint wash while the sheet is open.
- [x] Open the full player during a timed recitation. Expected: the recited page with the sweep; tapping it opens that page in the Mushaf. — **A50, 2026-11-26:** recited page with the sweep; tapping it opens page 3 in the Mushaf.
- [ ] TalkBack on the seek bar and the play seal. Expected: they read and adjust as before.
