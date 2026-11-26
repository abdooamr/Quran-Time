# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Arabic-speaking Muslims who read the Mushaf every day and listen to reciters, at home and on the go. Some read at night or before Fajr. Memorisation (repeating an ayah or a range) is a secondary use. Arabic is the default language; English is also supported.

## Product Purpose

Quran Time lets people read the Madinah Mushaf and listen to the Quran in one free app. It is built entirely on the MP3Quran.net API v3. Success means someone can open the app, pick up at the page they left, and press play, with the recitation following along on the page.

## Positioning

Reading and listening meet on the same page. While one of the 115 timed recordings plays, the current ayah is highlighted on the real Mushaf page and the pages turn by themselves. Tapping an ayah plays from there. The app also includes all of MP3Quran's other content (244 reciters, radio, live TV, videos, tafsir audio, tadabor clips), with no ads, no accounts and no tracking.

## Operating Context

- Read in portrait, one page at a time, swiping like a printed Mushaf (next page to the left, in both languages).
- Listened to with the screen locked, using lock-screen and headset controls.
- Used offline once pages and surahs are downloaded.
- Used in dim rooms and at night.

## Capabilities and Constraints

- The Quran is shown only as the 604 SVG pages from MP3Quran (black shapes, no selectable text). There is no text mode, no search inside the text and no translations.
- Ayah highlighting is available only for the 115 timed recordings. Other recordings play without it.
- Navigation is by surah, juz, page number and bookmarks. There is no hizb navigation in v1.
- Native Android: Kotlin and Jetpack Compose (minSdk 24), a reimplementation of the Flutter app with the same features. Android only.
- Data stays on the device. Content and audio come from mp3quran.net and the media hosts it links to.

## Brand Commitments

- Name: "Quran Time" / "وقت القرآن".
- The logo is an eight-point star in a gilt ring on Mushaf green (the launcher icon). Colours and typography follow DESIGN.md.
- MP3Quran.net is credited as the source of audio, pages and data.

## Evidence on Hand

- Mushaf pages are fetched from MP3Quran and stored on the phone as tinted masks; samples live in the Flutter repo (`samples/mushaf-svg/`).
- Captured API responses: `app/src/test/resources/fixtures/`.
- There are no screenshots, testimonials, ratings or user numbers yet. Don't invent any.

## Product Principles

1. The Mushaf page is sacred content. Nothing covers, crops, distorts or decorates the text.
2. Continue where you left off: last page, last recitation, one tap to resume.
3. Reading and listening are one flow, not two apps sharing a shell.
4. Calm and respectful, with no gamification, badges or streak pressure.
5. Works offline and in poor connections, failing gently with a clear retry.

## Accessibility & Inclusion

- Older readers: large touch targets (at least 48dp), high contrast, readable sizes, and the app follows the phone's font-size setting for interface text.
- Night reading: comfortable dark and sepia modes for the page itself, not only the interface.
- Standard Android accessibility: TalkBack labels on every control, full RTL mirroring for Arabic, and respect for reduce-motion.
