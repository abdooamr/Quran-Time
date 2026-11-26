# Checks that need the phone or a person

Collected during the unattended build (spec K6). Each line says what to do and what to expect.

## Pending device runs (the Galaxy A50 was not connected)

- [ ] Task 2: `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.benchmark.StartupBenchmark` with the A50 unlocked. Expected: a JSON result under `benchmark/build/outputs/connected_android_test_additional_output/`. If it fails on input injection, benchmarks are run by hand from this list.
- [x] Task 10 (done 2026-11-26: 7.3 ms median): page-image decode time on the A50: `ANDROID_SERIAL=R58M389X2XD ./gradlew :app:connectedDebugAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.MaskCodecDeviceTest`, then `adb logcat -d | grep codec=`. Expected: `codec=qtr` lines with `decodeMedianMs` ≤ 8 (it measured 5.2–8.0 ms on the emulator in a debug build).

## Playback (Task 20)

- [ ] Lock-screen controls: play a surah, lock the phone. Expected: play/pause, previous and next work from the lock screen; the title is the surah and the reciter.
- [ ] Bluetooth: play through a headset or car. Expected: its play/pause and skip buttons work; unplugging or disconnecting pauses.
- [ ] Notification Stop: Expected: playback stops and the notification goes away; the sleep timer and any repeat are cancelled.
- [ ] Kill and restore: play, swipe the app away while paused, reopen. Expected: the same surah and position are loaded, paused.

## Downloads (Task 30)

- [ ] Download a surah, then turn on airplane mode. Expected: it plays from the phone.
- [ ] More → Downloads → "Download all pages", then airplane mode. Expected: every page opens, and tapping an ayah still shows its actions (outlines were saved too).

## Live (Task 31)

- [ ] Play a radio station, lock the phone. Expected: it keeps playing, with controls in the notification.
- [ ] Open "Quran channel" and "Sunna channel". Expected: each plays; any recitation stops first; turning the phone sideways fills the screen and hides the system bars; Back returns to the list.
- [ ] Start a channel, press Home, come back. Expected: the video paused while hidden and goes on when back.

## Explore (Task 32)

- [ ] Explore → Videos: thumbnails load; a type chip narrows the grid; a video plays full screen.
- [ ] Explore → Tafsir → a collection → a surah → a clip. Expected: it plays in the mini player.
- [ ] Mushaf → long-press an ayah → "Tafsir of this surah". Expected: that surah's tafsir clips open.
- [ ] Explore → Reflections → a surah → Play. Expected: video clips open the video player, audio clips play.

## Settings and About (Task 33)

- [ ] Settings → English. Expected: the whole app (including sheets, dialogs and the notification) switches to English, left-to-right, and stays English after closing and reopening.
- [ ] Settings → each theme. Expected: System / Light / Sepia / Dark apply at once, including the Mushaf page ink.
- [ ] About → "mp3quran.net". Expected: the browser opens the site.
