# Checks that need the phone or a person

Collected during the unattended build (spec K6). Each line says what to do and what to expect.

## Pending device runs (the Galaxy A50 was not connected)

- [ ] Task 2: `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.benchmark.StartupBenchmark` with the A50 unlocked. Expected: a JSON result under `benchmark/build/outputs/connected_android_test_additional_output/`. If it fails on input injection, benchmarks are run by hand from this list.
- [ ] Task 10: page-image decode time on the A50: `ANDROID_SERIAL=R58M389X2XD ./gradlew :app:connectedDebugAndroidTest -P android.testInstrumentationRunnerArguments.class=com.qurantime.kotlin.MaskCodecDeviceTest`, then `adb logcat -d | grep codec=`. Expected: `codec=qtr` lines with `decodeMedianMs` ≤ 8 (it measured 5.2–8.0 ms on the emulator in a debug build).
