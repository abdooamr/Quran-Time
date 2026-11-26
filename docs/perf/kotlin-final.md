# Kotlin app — final measurements (Task 35)

Galaxy A50 (SM-A505FN, Android 12), release build (4.06 MB APK) with the
regenerated Baseline Profile compiled (`cmd package compile -f -m
speed-profile`, as a Play install would be). All 604 pages on disk. Same
scripts as Task 17 (`tools/perf.sh`, `tools/swipe.sh`; Macrobenchmark hangs
on this Samsung).

| metric | Kotlin | Flutter | target | result |
|---|---|---|---|---|
| cold start to fully drawn, median of 10 | **421 ms** (311–506) | 586 ms | ≤ 600 ms and below Flutter | ✓ |
| swipe jank: missed frame deadlines, 40 swipes per run, median of 5 | **0.89 %** (1.42, 0.81, 0.09, 0.89, 0.99) | — | ≤ 1 % | ✓ (narrow) |
| memory (PSS), "Play page" on page 3, 30 s in (playing, ayah highlighted) | **113 MB** | 120 MB at start | ≤ 120 MB | ✓ |
| all 604 page masks on disk | **78.9 MB** | ~180 MB | ≤ 100 MB | ✓ |
| APK | **4.06 MB** | 23.6 MB | ≤ 8 MB | ✓ |

## The jank gate: what failed and what fixed it

The first final run used the profile generated at Task 17. Four swipe runs
gave 0.73, 1.60, 2.77 and 0.36 % (median ≈ 1.2 %, over the gate). An
`atrace` of 24 swipes (the release build is now `profileable`) showed ART
JIT-compiling **540 methods** while swiping: the code added after Task 17
(follow-along overlay, mini player, player state) wasn't in the profile, so
it ran interpreted on the main thread at first.

The profile journey now also plays a page, swipes while it plays, pauses,
and opens every tab (profile 24,011 → 32,378 lines). Re-traced, the swipes
JIT-compile **29 methods**, and the five runs above give a 0.89 % median.

## Other notes

- Cold start: Task 17 measured 384 ms, before the mini player and player
  restore existed. A run right after the 604-page pack (phone busy) gave
  551 ms; settled runs give 412–421 ms.
- Memory during swipes peaks at 137–153 MB and returns to ~104 MB after
  20 s idle (no leak; decoded pages are released as they leave the cache).
- Page pack: the size is the app's own "saved on this phone" figure
  (`run-as` doesn't work on a release build). 604/604 pages, 0 failed, in
  about 4 minutes over Wi-Fi.
