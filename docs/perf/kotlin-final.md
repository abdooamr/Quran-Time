# Kotlin app — final measurements (Task 35)

Galaxy A50 (SM-A505FN, Android 12), release build (4.04 MB APK) with the
Baseline Profile compiled (`cmd package compile -f -m speed-profile`). The
profile is the one generated at Task 17 (start-up, swipes, jump sheet):
regenerating it on the emulator was stopped when the PC ran low on memory.
All 604 pages were on disk. Same scripts as Task 17 (`tools/perf.sh`,
`tools/swipe.sh`; Macrobenchmark hangs on this Samsung).

| metric | Kotlin | Flutter | target | result |
|---|---|---|---|---|
| cold start to fully drawn, median of 10 | **412 ms** (399–444) | 586 ms | ≤ 600 ms and below Flutter | ✓ |
| swipe jank: missed frame deadlines, 40 swipes per run | **0.73 %, 1.60 %, 2.77 %, 0.36 %** (4 runs; median ≈ 1.2 %) | — | ≤ 1 % | **✗ (varies run to run)** |
| memory (PSS), "Play page" on page 3, 30 s in | **116 MB** | 120 MB at start | ≤ 120 MB | ✓ (tight) |
| all 604 page masks on disk | **78.9 MB** | ~180 MB | ≤ 100 MB | ✓ |
| APK | **4.04 MB** | 23.6 MB | ≤ 8 MB | ✓ |

## Notes

- Cold start: a first run right after the profile compile and the 604-page
  pack gave 551 ms (the phone was still busy); after it settled, 412 ms.
  Task 17 measured 384 ms, before the mini player and player restore existed.
- Swipes: Task 17 measured 0.18 % over 20 swipes. Now the same script gives
  0.36–2.77 % across four runs in a row. `gfxinfo` blames the UI thread
  (12 slow UI-thread frames, 10 slow draw-command frames, 0 slow bitmap
  uploads) rather than the GPU.
- Memory during swipes peaks at 137–153 MB and returns to 104 MB after
  20 s idle: no leak, but swiping allocates a lot (the Java heap reaches
  ~30 MB, native ~41 MB).
