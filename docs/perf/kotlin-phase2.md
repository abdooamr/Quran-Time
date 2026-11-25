# Kotlin app after the Mushaf phase (Task 17)

Galaxy A50 (SM-A505FN, Android 12), release build with the Baseline Profile compiled (`cmd package compile -m speed-profile`, as a Play install would be). Pages were already on disk.

| metric | Kotlin | Flutter baseline | target |
|---|---|---|---|
| cold start to fully drawn (median of 10) | **384 ms** (347–415) | 586 ms | ≤ 600 ms and below Flutter ✓ |
| cold start without the profile compiled | 639 ms | — | (first launch after a sideload) |
| page swipes: missed frame deadlines (20 swipes, 559 frames) | **0.18 %**, p50 12.5 ms, p99 25.3 ms | (Task 3: Flutter hook) | ≤ 1 % ✓ |
| memory (PSS) after 40 swipes, no audio yet | 100 MB settled, 125 MB just after | 120 MB at start | ≤ 120 MB with audio — checked in Task 35 |
| APK (arm64, all ABIs in one file) | **2.30 MB** | 23.6 MB | ≤ 8 MB ✓ |

## How it was measured

- Start: `tools/perf.sh` (10 cold starts, "Fully drawn" from logcat; the app reports it once the opening page is drawn).
- Swipes: `tools/swipe.sh` drives 10 forward + 10 back swipes with `adb shell input swipe` after a warm-up pass, samples `gfxinfo framestats` every 3 swipes and merges them (`tools/framestats.py`). A frame is janky when it completes after its deadline. Frames that never complete (adb-injected touches create many) are excluded; `gfxinfo`'s own "Janky frames" counts them as 4950 ms frames.
- Macrobenchmark (`:benchmark`) hung on the A50 before launching the app (Samsung), so these scripts are the measurement.

## What moved the numbers

| change | effect |
|---|---|
| Baseline Profile (start-up + 20 swipes + jump sheet) compiled | start 639 → 384 ms |
| opening page decoded while the first frame is built; neighbours wait for it | fully drawn lands right after the first frame |
| `Bitmap.prepareToDraw()` after decode (GPU upload off the first frame) | missed deadlines 0.72 % → 0.18 % |
| two pages composed ahead instead of one | 0.54 %, but +15 MB of GPU textures → reverted |
