# Test parity: Flutter → Kotlin

Every Dart test has a Kotlin `@Test` with the same name in backticks (the
port rule). `tools/parity.py` checks this by name: a few names differ only
where the JVM forbids characters (`0.5..2.0` → `0_5 to 2_0`), where a Dart
string template became one test per value (`next page is to the left (ar)` /
`(en)`), or where a table-driven Dart test became `<name> cases` /
`<name> fixture`.

**Result: 251 Dart tests, 251 ported, 0 missing.**
The Kotlin suite has 299 tests: the ports plus 48 added ones
(Review Focus cases, the mask codec, downloads ordering, the Range-resume
worker, the page pack, release-manifest checks and others).

No Dart test was dropped. Where a test had to work differently, the ruling is
in the execution ledger; the ones that change *how* a test checks behaviour:

- **Mushaf page rendering (Task 14):** Robolectric draws with its default
  graphics (native graphics crash the test JVM on Windows), so tests decode
  masks to ARGB bitmaps over the real file format (`LegacyFriendlyCodec`);
  the ALPHA_8 path is checked on the A50.
- **Zoom (Task 16):** the gesture logic is a `ZoomState` tested with virtual
  time instead of Flutter's `InteractiveViewer` gestures.
- **API cache in UI tests (Task 25):** an in-memory `ApiCacheDao`, because a
  large Room write stalled the Robolectric main looper; the Room path is
  covered by `CachedFetcherTest` and was checked on the A50.
- **Media3 completion (Task 29):** runs on Media3's fake-clock test player.

| Dart test file | Dart tests | Ported | Kotlin test classes |
|---|---:|---:|---|
| `app_shell_test.dart` | 5 | 5 | ShellTest |
| `core/api/api_client_test.dart` | 7 | 7 | ApiClientTest |
| `core/api/json_utils_test.dart` | 4 | 4 | JsonUtilsTest |
| `core/api/urls_test.dart` | 5 | 5 | UrlsTest |
| `core/cache/cached_fetcher_test.dart` | 8 | 8 | CachedFetcherTest |
| `features/audio/ayah_repeat_test.dart` | 8 | 8 | AyahRepeatTest, PlayerControllerTest |
| `features/audio/playable_item_test.dart` | 6 | 6 | MediaItemsTest, PlayableItemTest |
| `features/audio/player_controller_test.dart` | 14 | 14 | PlayerControllerTest, SleepTimerTest |
| `features/audio/player_ui_test.dart` | 9 | 9 | PlayerUiTest |
| `features/audio/quran_audio_handler_test.dart` | 14 | 14 | QueueHandlerTest |
| `features/bookmarks/bookmarks_flow_test.dart` | 3 | 3 | BookmarksFlowTest, JumpSheetTest |
| `features/bookmarks/bookmarks_repository_test.dart` | 4 | 4 | BookmarksRepositoryTest |
| `features/catalog/catalog_repository_test.dart` | 3 | 3 | CatalogRepositoryTest |
| `features/catalog/models_test.dart` | 5 | 5 | CatalogModelsTest |
| `features/downloads/downloads_repository_test.dart` | 11 | 11 | DownloadsRepositoryTest |
| `features/downloads/downloads_ui_test.dart` | 5 | 5 | DownloadsUiTest, OnlineStatusTest |
| `features/explore/explore_test.dart` | 7 | 7 | ExploreTest |
| `features/explore/models_test.dart` | 8 | 8 | ExploreModelsTest |
| `features/follow_along/follow_along_controller_test.dart` | 7 | 7 | Setup |
| `features/live/live_tv_test.dart` | 3 | 3 | LiveTvTest |
| `features/live/models_test.dart` | 3 | 3 | LiveModelsTest |
| `features/live/radio_list_test.dart` | 3 | 3 | RadioListTest |
| `features/mushaf/ayah_actions_test.dart` | 6 | 6 | AyahActionsTest |
| `features/mushaf/ayah_geometry_test.dart` | 5 | 5 | AyahGeometryTest |
| `features/mushaf/follow_along_ui_test.dart` | 4 | 4 | FollowAlongUiTest |
| `features/mushaf/jump_sheet_test.dart` | 5 | 5 | JumpSheetTest |
| `features/mushaf/mushaf_controller_test.dart` | 3 | 3 | MushafControllerTest |
| `features/mushaf/mushaf_index_test.dart` | 4 | 4 | MushafIndexTest |
| `features/mushaf/mushaf_page_store_test.dart` | 7 | 7 | PageImageStoreTest |
| `features/mushaf/mushaf_reciter_and_play_test.dart` | 4 | 4 | MushafReciterAndPlayTest |
| `features/mushaf/mushaf_screen_test.dart` | 6 | 6 | MushafScreenTest |
| `features/mushaf/offline_recovery_test.dart` | 4 | 4 | MushafReciterAndPlayTest, MushafScreenTest |
| `features/mushaf/review_fixes_test.dart` | 6 | 6 | ReviewFixesTest |
| `features/reciters/models_test.dart` | 4 | 4 | ReciterModelsTest |
| `features/reciters/reciter_detail_test.dart` | 4 | 4 | ReciterDetailTest |
| `features/reciters/reciter_filter_test.dart` | 9 | 9 | ReciterFilterTest |
| `features/reciters/reciters_repository_test.dart` | 5 | 5 | RecitersRepositoryTest |
| `features/reciters/reciters_screen_test.dart` | 5 | 5 | RecitersScreenTest |
| `features/repositories_test.dart` | 8 | 8 | RepositoriesTest |
| `features/settings/settings_screen_test.dart` | 4 | 4 | SettingsScreenTest |
| `features/settings/settings_store_test.dart` | 4 | 4 | SettingsStoreTest |
| `features/timing/lookup_and_matcher_test.dart` | 7 | 7 | LookupAndMatcherTest |
| `features/timing/models_test.dart` | 5 | 5 | TimingModelsTest |

Re-run: `python tools/parity.py out.json` (paths at the top of the script).
