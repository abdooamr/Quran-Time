# Quran Time (وقت القرآن)

Read the Madinah Mushaf and listen along: while one of the 115 timed
recordings plays, the ayah being recited is highlighted on the real page and
the pages turn by themselves. A native Android app (Kotlin, Jetpack Compose),
free, with no ads, accounts or tracking. Pages, recitations and data come
from [MP3Quran.net](https://mp3quran.net).

## Features

- The 604 pages of the Madinah Mushaf, swiped like a printed book (next page
  to the left), with zoom, a jump sheet (surah, juz, page) and bookmarks.
- **Reading along:** the recited ayah fills with a soft wash, right to left,
  line by line, in time with the recitation; pages turn by themselves.
- Tap an ayah to play from it, repeat it (or a range) for memorisation,
  bookmark it, or open its tafsir.
- 240+ reciters and their recordings in many riwayat; background playback
  with lock-screen controls, sleep timer and speed.
- Downloads: surahs, whole recordings, and every Mushaf page for offline use.
- 170+ Quran radio stations, live TV from Makkah and Madinah, recitation
  videos, tafsir audio and reflections.
- Arabic and English (right-to-left first); light, sepia and dark themes.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 37). Then:

```sh
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit and UI tests (Robolectric)
./gradlew :app:bundleRelease          # Play bundle
```

**Release signing:** copy `key.properties.example` to `key.properties`
(git-ignored) and point it at your own upload keystore. Without it, release
builds are signed with the debug key and cannot be uploaded to Play.

## Performance (Galaxy A50)

| | Kotlin | Flutter version |
|---|---|---|
| Cold start to the page drawn | 421 ms | 586 ms |
| Late frames while swiping pages | 0.89 % | n/a |
| Memory while reciting | 113 MB | ~120 MB |
| All 604 pages on the phone | 78.9 MB | ~180 MB |
| APK | 4.3 MB | 23.6 MB |

How these were measured: [docs/perf/kotlin-final.md](docs/perf/kotlin-final.md).

## Docs

- [Design](DESIGN.md) and [product](PRODUCT.md) records; the reading-along
  and player design: [docs/design-revision.md](docs/design-revision.md)
- [Decisions made during the build](docs/decisions.md)
- [Test parity with the Flutter app](docs/parity.md) (every Flutter test ported)
- [Manual checks on a device](docs/manual-checks.md)
- [Privacy policy](docs/privacy-policy.md) and [store listing](docs/store-listing.md)

## Credits

Mushaf pages, recitations and data: MP3Quran.net. Book face: Amiri (SIL Open
Font License).
