## com.qurantime.app on SM-A505FN — 2026-11-26 01:39

| metric | value |
|---|---|
| fully_drawn_ms (median of 10) | 586 |
| starts | 733 539 585 595 596 569 551 588 563 665 |
| jank_pct (since last start) | 100.00 |
| pss_mb | 120 |
| apk_mb | 23.62 |

### Flutter page swipes (measure/baseline branch, `integration_test/swipe_perf_test.dart`, profile build)

`flutter drive --profile` on the A50: 20 page flings after a warm-up pass, frames counted with `SchedulerBinding.addTimingsCallback`.

| metric | value |
|---|---|
| frames | 1261 |
| frames over 16.6 ms (build + raster) | 936 (**74.23 %**) |

Flutter does not draw through Android's renderer, so gfxinfo can't see these frames; the comparable Kotlin number is the share of frames whose `FrameCompleted − IntendedVsync` exceeds 16.6 ms (about 10 % at p90 = 17.4 ms), and Kotlin's missed-deadline rate is 0.18 %.

Note: `flutter drive` reinstalled the Flutter app on the phone after an INSTALL_FAILED_VERSION_DOWNGRADE, which clears that app's saved data.
