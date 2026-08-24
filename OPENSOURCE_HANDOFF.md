# OpenBible Production Handoff

## 1. Project Identity

**App:** OpenBible
**Package:** com.openbible
**Platform:** Android (Kotlin, Jetpack Compose)
**Min SDK:** 29 (Android 10)
**Target/Compile SDK:** 34
**Build system:** Gradle 8.4 + Kotlin DSL + KSP
**Database:** Room 2.6.1 (prepopulated SQLite shipped in assets)
**State:** Storage: DataStore; UI state: Compose `StateFlow`

## 2. Build & Dependency Structure

```
openbible/
├── build.gradle.kts          (version catalog: plugins only)
├── settings.gradle.kts       (FAIL_ON_PROJECT_REPOS; google + mavenCentral)
├── gradle.properties         (parallel=true, caching=true, JVM 17)
├── gradle/libs.versions.toml (catalog)
└── app/
    ├── build.gradle.kts      (application plugin, compose, room, ksp)
    └── proguard-rules.pro
```

### Key Versions

| Component | Version |
|-----------|---------|
| AGP | 8.4.0 |
| Kotlin | 2.0.0 |
| KSP | 2.0.0-1.0.22 |
| Room | 2.6.1 |
| Compose BOM | 2024.06.00 |
| Navigation | 2.7.7 |
| DataStore | 1.1.1 |
| Coroutines | 1.8.1 |
| Glance (widget) | 1.1.0 |

### Release Config

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- splits abi disabled, `isUniversalApk = true` (F-Droid requirement)
- debug `applicationIdSuffix = ".debug"`

### DataStore

`openbible_prefs` preferences file.
Keys: default_translation, theme_mode, font_size, font_size_verses, line_spacing,
retro_theme_enabled, page_flip_sound, page_flip_animation, daily_verse_enabled,
daily_verse_time_hour, daily_verse_time_minute, last_read_translation,
last_read_book, last_read_ chapter.

## 3. Application Bootstrap

```
OpenBibleApp : Application()
  ├── lazy database: OpenBibleDatabase
  ├── lazy userPreferences: UserPreferences
  └── onCreate(): DailyVerseReceiver.createNotificationChannel(this)

MainActivity : ComponentActivity()
  ├── enableEdgeToEdge()
  ├── reads notification extras (translationId, bookId, chapter)
  ├── collects themeMode from DataStore
  ├── OpenBibleTheme(themeMode)
  └── OpenBibleNavGraph(isTablet, notification extras...)
```

## 4. Navigation

Bottom nav: Home, Bible, Search, Bookmarks, Settings.
Plus composable `reading_plans` (NOT in bottom nav).

Routes:
- `home`
- `bible`
- `bible/{translationId}/{bookId}/{chapter}`
- `search`
- `bookmarks`
- `settings`
- `notes` (route only; no bottom-nav entry)

Tablet handled via `isTablet` flag from RetroPixel config.

## 5. Data Model (Room Database v2, 14 entities)

### Core Bible (prepopulated; read-only)

| Entity | Notes |
|--------|-------|
| TranslationEntity | id, name, abbreviation, language, isBundled |
| BookEntity | translationId FK; id, name, abbreviation, number, testament |
| VerseEntity | PK id (NOT auto); translationId + bookId + chapter + verse |
| CrossReferenceEntity | fromVerseId FK verses.id; toBookId, toChapter, toVerseStart, toVerseEnd, relevance |

PK detail: `VerseEntity.id` is assigned by import script, not auto-gen. Verse queries use
`translationId + bookId + chapter + verse` as the natural key; `id` is the foreign key
target for highlights/bookmarks/history.

### User-Generated Content (runtime writes)

| Entity | Notes |
|--------|-------|
| BookmarkEntity | verseId FK, label?, tags?, createdAt |
| HighlightEntity | verseId FK, color (int), createdAt |
| NoteEntity | notebookId FK, title, contentText, penStrokeData?, createdAt, updatedAt |
| NoteImageEntity | noteId FK, path, position |
| NoteVerseLinkEntity | noteId + verseId PK; links notes to verses |
| NotebookEntity | name, createdAt |
| ReadingPlanEntity | name, description, durationDays, isPrebuilt |
| ReadingPlanDayEntity | planId + dayNumber PK; readings JSON, title |
| ReadingProgressEntity | planId + dayNumber PK; completed boolean, completedAt? |
| ReadingHistoryEntity | verseId PK; lastReadAt, readCount |

### DAO Summary

- `BibleDao` — read-only verse/book/translation/search/random verse + cross-ref joins
- `BookmarkDao` — CRUD + join `getBookmarksWithVerse()`
- `CrossReferenceDao` — per-verse cross-refs; Flow + suspend
- `HighlightDao` — per-verse + per-color delete
- `NoteDao` — notebooks, notes, images, verse links; note text search via LIKE
- `ReadingHistoryDao` — recent history, last-read resolution, upsert
- `ReadingPlanDao` — plan/day/progress CRUD + seeded "Bible in a Year"

### Schema Migrations

- v1: initial 14 tables
- v2: added `cross_references`
- Current: `fallbackToDestructiveMigration()` enabled (acceptable while prepopulated DB
  is regenerated alongside schema changes).

## 6. Data Import Pipeline (`data/import_bible.py`)

Location: `<project-root>/data/import_bible.py`

Flow:
1. Fetches Bible text from scrollmapper sources (KJV, WEB, ASV)
2. Builds SQLite with all 14 tables
3. Output: `data/openbible.db`
4. Copied to `app/src/main/assets/databases/openbible.db` for Room `createFromAsset()`

Translations bundled: KJV, WEB, ASV (all public domain).
The script is the source of truth for `VerseEntity.id` values — IDs are stable and
must not be regenerated lightly or cross-reference data will break.

## 7. Feature Modules

### 7.1 Bible Reading (`ui/bible/BibleScreen.kt`, `BibleViewModel.kt`, `BookChapterSelector.kt`, `TtsControls.kt`)

- Translation + book + chapter selector
- Verse display with highlighting + bookmark
- Cross-reference panel
- TTS playback panel
- Page-flip animation + sound

### 7.2 TTS (`tts/TtsController.kt`)

- Wraps `android.speech.tts.TextToSpeech`
- Utterance IDs: `verse_<index>`
- State exposed via `StateFlow<TtsState>`: `isPlaying`, `isInitialized`, `currentVerseIndex`, `speed`, `isAvailable`
- Supports play/pause/next/prev/stop/speed

### 7.3 Daily Verse Notification (`notification/DailyVerseReceiver.kt`, `DailyVerseScheduler.kt`, `DailyVerseWidgetProvider.kt`)

- `AlarmManager.setWindow()` within configurable hour:minute
- On alarm: query random verse, build notification, tap opens verse
- Also pushes home-screen widget via `DailyVerseWidgetProvider.storeVerse/refreshAll`
- BOOT_COMPLETED auto-reschedules
- Widget data bridge: `SharedPreferences` (`widget_verse`) — avoids widget-process DB access

### 7.4 Reading Plan (`data/ReadingPlanSeeder.kt`, `ui/readingplan/ReadingPlan*.kt`)

- "Bible in a Year" seeded automatically (365 days, ~3–4 chapters/day)
- Distribution: `ceil(1189 / 365) = 4` chapters/day, last days shorter
- Plan marked `isPrebuilt=true`
- Seeder checks `getAllPlansOnce()`; safe to re-run

### 7.5 Search (`ui/search/SearchScreen.kt`, `SearchViewModel.kt`)

- LIKE-based full-text search across translations
- Scoped per-translation or per-book
- Result DTO: `SearchResult(verseId, translationId, bookId, bookAbbreviation, chapter, verse, text)`

### 7.6 Bookmarks (`ui/bookmarks/BookmarksScreen.kt`, `BookmarksViewModel.kt`)

- Joined list with verse/book citation
- Toggle add/remove per verse

### 7.7 Settings (`ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`)

- Theme mode (light/dark/sepia)
- Font size (verse numbers vs verse text separately)
- Line spacing
- Retro pixel toggle
- Page-flip sound/animation
- Daily verse toggle + time

### 7.8 Theme (`ui/theme/`)

- `Theme.kt` — Material 3 theme, dark/light/sepia
- `RetroPixel.kt` — table detection helper
- `Color.kt`, `Type.kt` — custom palette + pixel font

## 8. Resource Layout

### drawable

`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_notification.xml`

### font

`pixelify_sans.ttf` (bundled retro font)

### layout

`widget_daily_verse.xml` (Glance-less RemoteViews)

### mipmap

Standard launcher icon set (hdpi through xxxhdpi, round + square)

### values

`colors.xml`, `dimens.xml`, `strings.xml`, `themes.xml`

### xml

`widget_daily_verse_info.xml` — AppWidgetProviderInfo

## 9. Data Layer File Index

### entities (14)

```
BookEntity, BookmarkEntity, CrossReferenceEntity, HighlightEntity,
NotebookEntity, NoteEntity, NoteImageEntity, NoteVerseLinkEntity,
ReadingHistoryEntity, ReadingPlanDayEntity, ReadingPlanEntity,
ReadingProgressEntity, TranslationEntity, VerseEntity
```

### daos (7 interfaces + 2 join DTOs)

```
BibleDao, BookmarkDao, CrossReferenceDao, HighlightDao, NoteDao,
ReadingHistoryDao, ReadingPlanDao
```
```
BookmarkWithVerse, CrossReferenceDisplay, ReadingHistoryWithVerse, SearchResult
```

### converters

```
Converters.kt — Testament ↔ String, PenMode ↔ String
```

### models

```
Enums.kt — Testament, ThemeMode, PenMode, HighlightColor
```

### preferences

```
UserPreferences.kt — DataStore wrapper (12 keys)
```

### seeder

```
ReadingPlanSeeder.kt — generates 365-day plan from KJV chapter counts
```

## 10. Documentation & Metadata

- `PROJECT_REGISTRY.md` — canonical source of truth for project identity; overrides
  README-driven assumptions. **Any structural change must update this file first.**
- `data/README.md` — import pipeline usage, translations, schema notes
- `.gitignore` — notable: excludes `assets/databases/openbible.db`, `data/*.txt|json`, `venv`,
  builds, keystores

## 11. Structural Findings (Production-Grade Reviews)

### 11.1 Stable Issue — Prepopulated DB lifecycle

**Finding:** `OpenBibleDatabase.buildDatabase()` attempts `createFromAsset()` via a
`try/catch` assets open check. If the asset is missing, Room creates an empty schema and
`fallbackToDestructiveMigration()` will wipe user data on any schema bump.

**Risk:** A user upgrading before the prepopulated DB ships (corrupted install,
incomplete build) gets an empty DB. Subsequent schema updates destroy any bookmarks
the user made in that empty DB.

**Recommendation:**
- Replace the `try/catch` with an explicit `assets.list("databases/")` check.
- Before calling `createFromAsset`, verify asset exists *and* schema version matches.
- If asset missing: show an unrecoverable error screen prompting reinstall
  (do NOT silently fall back to empty schema).
- Alternatively, version-gate: ship an explicit `db_schema_version` int in assets
  and compare to `OpenBibleDatabase.version` before `createFromAsset`.

### 11.2 Stable Issue — VerseEntity.id contract with import script

**Finding:** `VerseEntity.id` is a stable integer assigned by `import_bible.py`, not
auto-generated by Room. `CrossReferenceDao` LEFT JOINs on `v.id = cr.fromVerseId` —
this assumes `VerseEntity.id` is stable across the entire prepopulated corpus.

**Risk:** If the import script changes its ID assignment logic (e.g., different source
data ordering, processing order change), all cross-references point to wrong verses
with no compile-time or migration-time detection.

**Recommendation:**
- Document the ID generation contract in `data/README.md`: "Verse IDs are derived
  from `(translationId, bookId, chapter, verse)` via the formula X."
- Add a pre-commit or CI check: run `import_bible.py`, extract `(translationId, bookId,
  chapter, verse) → id` mapping, and compare to a golden snapshot. Fail build on drift.

### 11.3 Stable Issue — `fallbackToDestructiveMigration()`

**Finding:** Currently enabled and masked by prepopulated DB regeneration.

**Risk:** User-vs-schema data asymmetry. If schema changes but asset DB is not
regenerated and redistributed, user data is destroyed on upgrade.

**Recommendation:** Before production release, replace `fallbackToDestructiveMigration()`
with explicit `Migration(1,2)` etc. and gate the prepopulated asset on version equality.
If versions mismatch, treat as data error, not "just wipe it."

### 11.4 Stable Issue — `runBlocking` in BroadcastReceiver

**Finding:** `DailyVerseReceiver.showDailyVerse()` uses `runBlocking` + DataStore + Room
queries inside a `BroadcastReceiver`.

**Risk:** `BroadcastReceiver.onReceive()` has ~10s window. On cold start / slow disk,
`runBlocking` can stall the main thread. Not a current problem for Room (~50ms),
but fragile under I/O contention.

**Recommendation:** Move verse lookup to a foreground `WorkManager` job, or to
`DailyVerseScheduler.schedule()` itself. Receiver posts the work; WorkManager handles
execution with its own timeout/battery constraints.

### 11.5 Stable Issue — Search is LIKE-based, no FTS

**Finding:** `BibleDao.searchVerses` uses `LIKE '%query%'` on `verses.text`.

**Risk:** `LIKE` in SQLite uses B-tree, not full-text index. Multi-word queries are O(n*m)
with no ranking. Current limit=100 masks the perf problem but doesn't scale.

**Recommendation:** Add `FTS5` virtual tables over `verses(text)` per translation,
or migrate to Room's `FtsEntity` support. Query becomes MATCH-based with relevance
ranking; translations with >31k verses (Psalms) will feel snappy.

### 11.6 Stable Issue — ReadingPlanSeeder uses org.json

**Finding:** `ReadingPlanSeeder` stores readings as a JSON string in a single TEXT column.

**Risk:** No query flexibility. You cannot filter, sort, or paginate plan days by
individual chapter references inside the JSON.

**Recommendation:** Normalize to `reading_plan_day_readings(planDayId, bookId, chapter,
verseStart, verseEnd)` table. Queries become trivial joins. JSON is acceptable as an
export/cache layer, not as the primary storage.

### 11.7 Stable Issue — AndroidManifest exports

**Finding:** `DailyVerseReceiver` and `DailyVerseWidgetProvider` are `exported=true`
with implicit intent filters.

**Risk:** Any app on the device can send `com.openbible.action.SHOW_DAILY_VERSE`, which
triggers a database read and notification fire.

**Recommendation:** Add `android:exported="false"` (API 31+) or at minimum
`android:permission="com.openbible.permission.RECEIVE_DAILY_VERSE"` and define a
signature-level permission in the manifest.

### 11.8 Stable Issue — PenMode enum not persisted yet

**Finding:** `Enums.kt` defines `PenMode(TEXT, INK, BOTH)`. Both `NoteEntity` and
`NoteImageEntity` have placeholder-ish fields (`penStrokeData`? not in current entities
review — likely a column) but no `@TypeConverter` is registered for `PenMode`.

**Risk:** If `NoteEntity` adds a `penMode` column later without a converter, Room build
fails. If it already has one, it's not surfaced in this review.

**Recommendation:** Add `penModeToString/stringToPenMode` to `Converters.kt` *before*
adding the column. Or define the column as INTEGER with documented ordinal mapping.

### 11.9 Stable Issue — No CI configured

**Finding:** No `.github/workflows`, no CI references in `PROJECT_REGISTRY.md` or README.

**Risk:** No verification of data pipeline, schema drift, or build health on PR.

**Recommendation:** Add:
1. `./gradlew assembleDebug` build check (Java/Kotlin compile + KSP)
2. `python3 data/import_bible.py` does not error
3. `diff <(sqlite3 data/openbible.db '.schema') <expected schema snapshot>` against
   golden
4. `adb install` + instrumentation test baseline

### 11.10 Stable Issue — NO_INTERNET vs data pipeline

**Finding:** App correctly declares no internet permission. `data/import_bible.py`
requires network to fetch scrollmapper data.

**Risk:** Build server, CI, or new contributor using an air-gapped environment
cannot regenerate the prepopulated DB.

**Recommendation:** Record the exact source URLs + expected file format in
`data/README.md`. Provide a scripted download step, or snapshot source CSVs into
`data/source/` and import from there.

## 12. Recommended File Ownership Map

| Area | Owner class | Key files |
|------|-------------|-----------|
| DB schema, migrations | `OpenBibleDatabase.kt`, `Converters.kt` | all `entity/*.kt` |
| Bible text queries | `BibleDao.kt` | `VerseEntity`, `BookEntity`, `TranslationEntity` |
| User notes/data | `NoteDao.kt`, `BookmarkDao.kt`, `HighlightDao.kt` | respective entities |
| Reading plans | `ReadingPlanSeeder.kt`, `ReadingPlanDao.kt` | plan + day + progress entities |
| Notifications | `DailyVerseReceiver.kt`, `DailyVerseScheduler.kt` | widget provider |
| TTS | `TtsController.kt` | |
| Preferences | `UserPreferences.kt` | |
| Navigation | `NavGraph.kt` + six `Screen.kt` files | |
| Data pipeline | `data/import_bible.py` | `PROJECT_REGISTRY.md` must be updated when entity schema changes |

## 13. Recommended Change Workflow

1. Edit `app/src/main/java/com/openbible/data/db/entity/*.kt` (schema)
2. Rebuild: `./gradlew assembleDebug` (KSP regenerates DAOs)
3. Update `app/src/main/java/com/openbible/data/db/OpenBibleDatabase.kt` version++
4. Add migration entry or confirm `fallbackToDestructiveMigration` still acceptable
5. Re-run `python3 data/import_bible.py`
6. Copy new `openbible.db` to `app/src/main/assets/databases/`
7. Update `PROJECT_REGISTRY.md` if any of:
   - entities, DAOs, routes, screens, preferences entries, or data pipeline change
   - build version, SDK, or dependency versions change

## 15. Session Log — 2026-08-12 Debug Review + Layout Assessment

### 15.1 What Was Done
- Full code review of all 93 Kotlin files + 21 XML files + 11 MD files
- 6 code fixes applied (all compile, build GREEN):
  1. `BibleDao.kt:20` — added `"nkjv"` to `FTS_TRANSLATIONS` (NKJV FTS table existed but was never queried)
  2. `OpenBibleDatabase.kt:136` — catch block now logs via `Timber.e` + falls back to `Context.createFromFile` when prepopulated DB asset missing (was silently creating empty schema)
  3. `DailyVerseWidgetProvider.kt` — widget `PendingIntent` now carries `translationId`/`bookId`/`chapter` extras; `storeVerse` signature updated to 5 params; `DailyVerseReceiver.kt` caller updated
  4. `NoteEditorScreen.kt` — mic button now requests `RECORD_AUDIO` via `ActivityResultContracts.RequestPermission()`; shows "Mic blocked" hint when permanently denied; Undo/Redo/Arrow icons fixed to non-deprecated variants
  5. `ReadingPlanViewModel.kt` — `loadBookNames()` now called synchronously at start of `initialize()` before first render (was async in `init {}`, causing stale "Book X" display)
  6. `StrongDao.kt` — `COLLATE NOCASE` added to both `searchStrongNumbers` + `searchStrongNumbersByPrefix` LIKE queries (case-insensitive Strong's search)
- Emulator: AVD `ob_verify` (Pixel 5, API 34, x86_64) launched on Xvfb :99, screen unlocked, debug APK installed, app launched
- 26 PNG screenshots captured in `/tmp/openbible_screens/` across 8 distinct screens (Home, Bible, Bible long-press, Bookmark added, Search, Search results, Bookmarks, Notes, New Note, Ink mode, Settings)
- Screens verified rendering via pixel analysis (PIL/numpy): text bands detected, color variation present, no blank/crashed screens
- Logcat clean: no FATAL errors, no exceptions, only standard Compose snapshot warnings

### 15.2 What Was NOT Done (Sessions Restarted Before Completion)
- Per-screen visual layout review via `vision_analyze` — tool cannot access local file:// or localhost HTTP images (returns 404/blocked). Pixel analysis confirmed content but cannot read actual text or provide visual critique.
- Screenshots were captured multiple times; many files are identical (~98KB or ~212KB) because navigation between capture attempts failed — the app was already on the same screen.
- Remaining 8 screens not captured: Reading Plans detail, Strong's detail, Geography list, Map, Location detail, Parallel Traditions — require sub-screen navigation that wasn't completed.
- Tablet verification — emulator is a phone (Pixel 5, 1080×2340). Tablet screenshots exist in `screenshots/tablet/` from a prior July 23rd run but were not re-captured or re-analyzed with the current build.

### 15.3 Tablet Support Gap (CRITICAL — NOT ADDRESSED)
The app declares support for 7"–14.5" tablets in `PROJECT_REGISTRY.md` and `fastlane/metadata/`, but the implementation is incomplete:

- **`isTablet` flag exists** (MainActivity: `screenWidthDp >= 600`) and is propagated to screens via constructor params.
- **Only ONE screen uses real adaptive layout:** `BibleWithNotesScreen.kt` (split-pane with draggable divider, >= 600dp). This screen is NEVER registered in `NavGraph.kt`.
- **`BibleScreen.kt`** uses `isTablet` for: split-pane compare mode, study mode split, centering content with max width. Partial tablet support.
- **Navigation is bottom-bar only** — `NavGraph.kt:305-334` hardcodes `NavigationBar` regardless of screen size. No `NavigationRail`, no `WindowSizeClass` API. On 14.5" tablets, navigation is still a bottom bar.
- **All screens use `fillMaxSize()`/`fillMaxWidth()` with no `maxWidth` constraint** (`PROJECT_REGISTRY.md:853-854`). On a 14.5" tablet in landscape (~1400dp), verse text lines span full width — visually unreadable (~140+ chars/line). Needs `widthIn(max = 800.dp)` or `WindowSizeClass` + centered layout.
- **Tablet screenshots exist** in `screenshots/tablet/` (14 PNGs from July 23rd): home, bible, search, strongs, geography, parallel, reading_plan, bookmarks, notes, study_mode, settings, theme_dark, theme_sepia. These predate the 6 fixes applied in this session.

### 15.4 Remaining Work (Tablet Focus)
1. **Navigation:** Add `NavigationRail` for tablet layouts in `NavGraph.kt`. Bottom nav is fine for phones; tablets need a side rail.
2. **Content width:** Add `Modifier.widthIn(max = 800.dp)` or `WindowSizeClass` to all reading/content screens so text doesn't span full 14.5" width. BibleScreen already has partial centering — extend to all screens.
3. **Screen verification on tablet AVD** — create a 10" or 14.5" tablet AVD, install APK, navigate all screens, screenshot, analyze.
4. **Re-capture tablet screenshots** with the current build (fixes applied) to update `screenshots/tablet/`.
5. **Re-verify all 6 fixes on tablet** — especially Bible screen split-pane, BibleWithNotes split (if navigated to), map markers on large screen.

### 15.5 Developer Notes
- `applicationIdSuffix = ".debug"` → package is `com.openbible.debug`, not `com.openbible`. Launch component: `com.openbible.debug/com.openbible.MainActivity`.
- Emulator AVD name: `ob_verify`. Launch with `emulator -avd ob_verify -no-window -gpu swiftshader_indirect` (headless) or with display.
- KVM available: `/dev/kvm` exists, readable+writable.
- Android SDK: `<android-sdk>`, platforms android-34 + android-35, system image android-34/default/x86_64.
- Xvfb on `:99` for headless screenshots: `Xvfb :99 -screen 0 1920x1080x24 -ac -nolisten tcp -dpi 96 &`
