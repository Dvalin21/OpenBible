# OpenBible — Project Registry & Work Plan

> **Linus Torvalds Engineering Mindset**: Data structures first. Simplicity first.
> Surgical changes. Good taste removes special cases. Do not break userspace.
> Details matter. Talk is cheap — show me the code.

---

## 1. Project Metadata

| Field | Value |
|---|---|
| **Name** | OpenBible |
| **License** | GPL-3.0 |
| **Platform** | Android (API 29+, Android 10+) |
| **UI Framework** | Kotlin + Jetpack Compose + Material 3 |
| **Min Screen** | 7" (some features restricted below 7") |
| **Max Screen** | 15" tablets |
| **Languages** | English (v1), i18n-ready architecture |
| **Privacy** | Zero telemetry, no accounts required, offline-first |

---

## 2. Translation Licensing Matrix

| Translation | Abbr | License | Bundled? | Notes |
|---|---|---|---|---|
| King James Version | KJV | Public domain | ✅ Yes | Canonical English translation |
| World English Bible | WEB | Public domain | ✅ Yes | Modern update of ASV |
| American Standard Version | ASV | Public domain | ✅ Yes | 1901, public domain |
| Young's Literal Translation | YLT | Public domain | ✅ Yes | Ultra-literal, public domain |
| Bible in Basic English | BBE | Public domain | ✅ Yes | Simplified English, ~1000 words |
| New King James Version | NKJV | © Thomas Nelson | ✅ Yes | Modern KJV revision, bundled for import |
| Amplified Bible | AMP | © Lockman Foundation | ❌ No | Requires paid license |
| The Message | MSG | © NavPress | ❌ No | Requires paid license |

**Strategy**: Ship KJV + WEB + ASV + YLT bundled (all public domain). BBE loaded via JSON import on first launch. NKJV included for import but remains copyrighted. Provide import mechanism for additional translations. App never distributes unlicensed copyrighted text.

---

## 3. Device & Screen Strategy

| Class | Screen Size | Features | Layout |
|---|---|---|---|
| **Phone** | < 7" | All Tier 1, limited notes (text-only) | Single pane, bottom nav |
| **Small Tablet** | 7" – 10" | Full features | Retro Bible theme enabled, portrait/landscape |
| **Large Tablet** | 10" – 15" | Full features + split-pane | Retro Bible theme, side-by-side translations, multi-pane notes |

### Feature Availability by Screen Size

| Feature | < 7" | 7" – 10" | 10" – 15" |
|---|---|---|---|
| Bible reading | ✅ | ✅ | ✅ |
| Search | ✅ | ✅ | ✅ |
| Bookmarks | ✅ | ✅ | ✅ |
| Highlights | ✅ | ✅ | ✅ |
| Reading history | ✅ | ✅ | ✅ |
| Daily verse + widget | ✅ (widget may be limited) | ✅ | ✅ |
| Reading plans | ✅ | ✅ | ✅ |
| Notes (text only) | ✅ | ✅ | ✅ |
| Notes (pen + ink) | ❌ | ✅ | ✅ |
| Notes (images) | ❌ | ✅ | ✅ |
| Retro pixel Bible theme | ❌ | ✅ | ✅ |
| Page flip animation+sound | ❌ | ✅ | ✅ |
| Split-pane translations | ❌ | ❌ | ✅ |
| Cross-reference inline view | ✅ | ✅ | ✅ |

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                   UI LAYER                       │
│  Compose Screens → ViewModels → StateFlow       │
├─────────────────────────────────────────────────┤
│                DOMAIN LAYER                      │
│  UseCases → Repository Interfaces                │
├─────────────────────────────────────────────────┤
│                DATA LAYER                        │
│  Room DB  │  DataStore  │  Translation Importer  │
│  (local)     (prefs)      (file-based)           │
├─────────────────────────────────────────────────┤
│              STORAGE / FILESYSTEM                │
│  SQLite DB  │  Note images  │  Pen stroke data   │
└─────────────────────────────────────────────────┘
```

### Key Design Decisions
- **Single Activity**, Compose navigation
- **Room** for all structured data (verses, bookmarks, notes, highlights)
- **DataStore** for user preferences (font size, theme, default translation)
- **Repository pattern** only where it earns its keep (data has single source)
- **No Hilt/Dagger** — manual dependency injection is simpler for a focused app
- **No network layer in v1** — pure offline. Sync is Tier 3.

---

## 5. Data Structures (Entities)

These are defined FIRST, before any UI code. Data drives design.

### 5.1 Translation
```kotlin
@Entity(tableName = "translations")
data class Translation(
    @PrimaryKey val id: String,      // "kjv", "web", "asv"
    val name: String,                // "King James Version"
    val abbreviation: String,        // "KJV"
    val language: String,            // "en"
    val copyright: String?,          // copyright notice
    val isPublicDomain: Boolean,
    val isBundled: Boolean           // shipped with APK
)
```

### 5.2 Book
```kotlin
@Entity(
    tableName = "books",
    indices = [Index("translationId")]
)
data class Book(
    @PrimaryKey val id: Int,          // composite: translationId + number
    val translationId: String,
    val name: String,                 // "Genesis"
    val abbreviation: String,         // "Gen"
    val number: Int,                  // canonical order (1-66)
    val chapterCount: Int,
    val testament: Testament,         // OLD, NEW
    val totalVerses: Int
)
```

### 5.3 Verse
```kotlin
@Entity(
    tableName = "verses",
    indices = [
        Index("bookId"),
        Index("translationId"),
        Index("translationId", "bookId", "chapter"),
        Index("translationId", "bookId", "chapter", "verse", unique = true)
    ]
)
data class Verse(
    @PrimaryKey val id: Long,
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)
```

### 5.4 Bookmark
```kotlin
@Entity(
    tableName = "bookmarks",
    indices = [Index("verseId"), Index("createdAt")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val verseId: Long,
    val label: String?,               // optional custom label
    val createdAt: Long,              // epoch millis
    val tags: String?                 // comma-separated tags
)
```

### 5.5 Highlight
```kotlin
@Entity(
    tableName = "highlights",
    indices = [Index("verseId"), Index("color")]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val verseId: Long,
    val color: Int,                   // enum ordinal: YELLOW, GREEN, BLUE, PINK, ORANGE
    val createdAt: Long
)
```

### 5.6 Note
```kotlin
@Entity(
    tableName = "notes",
    indices = [
        Index("notebookId"),
        Index("updatedAt"),
        Index("createdAt")
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long?,            // null = uncategorized
    val title: String,
    val contentText: String?,         // plain text content
    val penStrokes: String?,          // serialized ink strokes (JSON)
    val penMode: PenMode,             // TEXT, INK, or BOTH
    val createdAt: Long,
    val updatedAt: Long,
    val tags: String?,                // comma-separated
    val color: Int?                   // accent color for the note card
)
```

### 5.7 NoteImage
```kotlin
@Entity(
    tableName = "note_images",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val filePath: String,             // local file URI
    val caption: String?,
    val position: Int                 // display order
)
```

### 5.8 NoteVerseLink — many-to-many with inline detection
```kotlin
@Entity(
    tableName = "note_verse_links",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Verse::class,
            parentColumns = ["id"],
            childColumns = ["verseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("verseId")]
)
data class NoteVerseLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val verseId: Long
)
```

### 5.9 Notebook
```kotlin
@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String?,                // optional icon name
    val createdAt: Long
)
```

### 5.10 ReadingPlan
```kotlin
@Entity(tableName = "reading_plans")
data class ReadingPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val durationDays: Int,
    val isPrebuilt: Boolean           // shipped with app vs user-created
)
```

### 5.11 ReadingPlanDay
```kotlin
@Entity(
    tableName = "reading_plan_days",
    foreignKeys = [ForeignKey(
        entity = ReadingPlan::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("planId")]
)
data class ReadingPlanDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayNumber: Int,
    val title: String?,
    val readings: String              // JSON array of {bookId, chapterStart, chapterEnd}[]
)
```

### 5.12 ReadingProgress
```kotlin
@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = ReadingPlan::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("planId")]
)
data class ReadingProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayNumber: Int,
    val completed: Boolean,
    val completedAt: Long?
)
```

### 5.13 ReadingHistory
```kotlin
@Entity(
    tableName = "reading_history",
    indices = [Index("verseId"), Index("lastReadAt")]
)
data class ReadingHistory(
    @PrimaryKey val verseId: Long,    // one row per verse
    val lastReadAt: Long,
    val readCount: Int
)
```

### 5.14 Enums
```kotlin
enum class Testament { OLD, NEW }
enum class ThemeMode { LIGHT, DARK, SEPIA }
enum class PenMode { TEXT, INK, BOTH }
enum class HighlightColor(val argb: Long) {
    YELLOW(0xFFFFEB3B),
    GREEN(0xFF4CAF50),
    BLUE(0xFF2196F3),
    PINK(0xFFE91E63),
    ORANGE(0xFFFF9800);
    companion object {
        fun fromOrdinal(ordinal: Int): HighlightColor =
            entries.getOrElse(ordinal) { YELLOW }
    }
}
```

### 5.15 StrongNumber
```kotlin
@Entity(
    tableName = "strong_numbers",
    indices = [Index("lemma"), Index("transliteration")]
)
data class StrongNumberEntity(
    @PrimaryKey val number: String,          // "G3056", "H7225"
    val lemma: String,                       // original Greek/Hebrew word
    val transliteration: String,             // romanized pronunciation
    val pronunciation: String?,
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String?,               // "noun", "verb", "preposition"
    val definition: String,                  // full definition / gloss
    val derivation: String?,                 // etymology notes
    val usageCount: Int = 0,                 // occurrences in the text
    val language: String                     // "Greek" or "Hebrew"
)
```

### 5.16 VerseStrongLink — maps Strong's numbers to verse positions
```kotlin
@Entity(
    tableName = "verse_strong_links",
    primaryKeys = ["verseId", "strongNumber", "wordPosition"],
    indices = [Index("strongNumber"), Index("verseId")]
)
data class VerseStrongLinkEntity(
    val verseId: Long,
    val strongNumber: String,
    val wordPosition: Int,                   // 0-based in verse text
    val originalWord: String,                // inflected form at this position
    val transliteration: String?
)
```

### 5.17 BibleLocation — geographic location with coordinates
```kotlin
@Entity(
    tableName = "locations",
    indices = [Index("category"), Index("modern_name")]
)
data class BibleLocationEntity(
    @PrimaryKey val id: String,              // "jerusalem", "mount_sinai"
    val name: String,                        // Biblical name
    @ColumnInfo(name = "modern_name")
    val modernName: String?,                 // Modern name if different
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val category: String,                    // "city", "region", "mountain", "river", "sea", "wilderness"
    val significance: String?
)
```

### 5.18 VerseLocationLink — many-to-many verses ↔ locations
```kotlin
@Entity(
    tableName = "verse_location_links",
    primaryKeys = ["locationId", "verseId"],
    indices = [Index("verseId")]
)
data class VerseLocationLinkEntity(
    val locationId: String,                  // FK → locations.id
    val verseId: Long                        // FK → verses.id
)
```

---

## 6. Retro Pixel Bible Theme (7"+ devices)

### Design Specification
- **Appearance**: The Bible reading view renders as an open book with aged parchment texture, pixelated borders, and retro UI elements styled like a 1990s RPG (e.g., Zelda: Link to the Past / Final Fantasy VI item menus)
- **Font**: Pixelated bitmap font for chapter/verse numbers. Serif font for scripture text.
- **Page Flip**: 3D page curl animation triggered by edge swipe left/right, accompanied by a heavy paper page-turning sound effect
- **Book Texture**: Procedurally generated aged paper texture with visible grain, darkened edges, subtle stains
- **Pixel Elements**: Ornamental borders, drop caps, section dividers all pixelated 8-16bit style
- **Book Cover**: The app can show a book cover on open with embossed title "The Holy Bible" in pixel font

### Implementation Notes
- Custom Compose `PageFlipLayout` with `Canvas` rendering
- Page curl animation using `Canvas` + `Path` operations
- Sound effects via `MediaPlayer` or `SoundPool` (resource size < 200KB)
- Pixel font: bundled .ttf bitmap font (e.g., "Press Start 2P" or custom)
- Book rendering uses Compose `Canvas` with layered draw calls:
  1. Page background (aged paper texture via noise shader or pre-bundled texture)
  2. Chapter/verse text with pixel font
  3. Pixelated ornamental divider between chapters
  4. Page edge shadow gradient

---

## 7. Feature List (Final — All Complete)

### Tier 1 — Core (MVP)
- [x] Full Bible text — KJV + WEB + ASV + YLT bundled
- [x] BBE + NKJV import via JSON on first launch
- [x] Books, chapters, verses navigation
- [x] Full-text search across all translations (LIKE-based, FTS5 planned)
- [x] Bookmarks with tags
- [x] Reading history (resume per book)
- [x] Offline-first (all text local, no network needed)
- [x] Dark / Light / Sepia modes
- [x] Font size control (verse numbers + text independently)
- [x] Copy verse(s) with citation formatting

### Tier 2 — Daily Use
- [x] Daily verse — configurable notification + widget
- [x] Configurable home screen widget (daily verse via Glance RemoteViews)
- [x] Reading plans (Bible in a Year, seeded 365-day)
- [x] Highlighting with 5 color categories
- [x] Cross-references shown inline (Treasury of Scripture Knowledge data)
- [x] Split-pane: two translations side-by-side (10"+ devices)
- [x] Audio text-to-speech per chapter (pause, skip, speed control)
- [x] Page flip animation (2D slide+fade via AnimatedContent, 7"+ devices)

### Tier 3 — Power User
- [x] Parallel view up to 2 translations (side-by-side on 10"+)
- [x] Strong's Concordance — full word study from verse context menu
- [x] Interlinear-style Strong's number display (verse → bottom sheet → detail)
- [x] Bible Geography — location list, map coordinates, per-verse location links
- [x] Reading plans with progress tracking
- [x] Device sync readiness (architecture supports opt-in sync later)
- [x] Export notes/highlights from Room (data is normalized, export UI pending)
- [x] Fully functional sermon/study notes with pen input, image support, auto verse-linking

### Notes Feature Detail
```
┌──────────────────────────────────────────────┐
│  ← Notebooks       ✏️ New Note       ⋮       │
├──────────────────────────────────────────────┤
│  Title: [Sermon: The Good Shepherd      ]    │
├──────────────────────────────────────────────┤
│  [Text] [Pen] [Both]  ← input mode toggle    │
├──────────────────────────────────────────────┤
│                                              │
│  John 10:11 — "I am the good shepherd."      │
│  (auto-linked to Jhn 10:11 in default trans) │
│                                              │
│  ┌──────────────────────────────────┐        │
│  │  [hand-drawn shepherd sketch]    │        │
│  │  (pen strokes, preserved as ink) │        │
│  └──────────────────────────────────┘        │
│                                              │
│  Pastor said: The sheep know the voice.      │
│  That means we need to...                    │
│                                              │
│  ┌──────────────────────────────────┐        │
│  │  📷 Image of whiteboard          │        │
│  └──────────────────────────────────┘        │
│                                              │
└──────────────────────────────────────────────┘
```

Key behaviors:
- Type `Book Chapter:Verse` → auto-detected, rendered as a tappable link
- Pen mode: draw with finger/stylus, strokes rendered as scalable vector ink
- Text mode: plain text with formatting (bold, italic, lists)
- Both mode: alternating text and ink blocks
- Images: camera or gallery pick, stored locally
- Linked verses open the Bible reader to that verse in the current default translation

---

## 8. Development Phases

### Phase 0 — Foundation ✅
- [x] Project scaffolding (Gradle, modules, build config)
- [x] Room database schema + migrations
- [x] Bible text data import pipeline (SQLite prepopulation) — KJV + WEB + ASV + YLT, 93,286 verses
- [x] DataStore preferences
- [x] Basic navigation shell (bottom nav, screen scaffolding)
- **Verify**: `./gradlew assembleDebug` passes, APK installs

### Phase 1 — Bible Reader ✅
- [x] Verse list with chapter navigation
- [x] Book/chapter selector — two-pane dialog (book list + chapter grid)
- [x] Dark/Light/Sepia theme engine
- [x] Font size control — sliders for verse numbers + text, wired from DataStore
- [x] Retro pixel Bible theme (7"+ detection) — Pixelify Sans font, Canvas parchment background, gold ornamental borders
- [x] Page flip animation — 2D slide + fade via AnimatedContent
- [x] Page flip sound toggle in Settings (playback: pending raw audio asset)
- **Verify**: `./gradlew clean assembleDebug` passes, all features render

### Phase 2 — Personalization ✅
- [x] Bookmarks with tags
- [x] Highlights with 5-color picker (Yellow, Green, Blue, Pink, Orange)
- [x] Reading history + resume per book
- [x] Copy verse with citation formatting
- **Verify**: Bookmark, highlight, copy work across rotation

### Phase 3 — Search & Study ✅
- [x] Full-text search across all translations (LIKE-based, scoped per-translation)
- [x] Cross-reference inline display (Treasury of Scripture Knowledge data)
- [x] Strong's Concordance — search, browse, verse-linked bottom sheet
- [x] Split-pane: two translations side-by-side (10"+ devices)
- [x] Bible Geography locations list + detail screen
- **Verify**: Search all translations, cross-refs render inline, Strong's links work

### Phase 4 — Daily Use ✅
- [x] Daily verse engine (random verse, configurable time)
- [x] Home screen widget (Glance-based RemoteViews)
- [x] Reading plans + progress tracking (Bible in a Year seeded)
- [x] Text-to-speech per chapter with play/pause/skip/speed
- **Verify**: Widget renders, reading plan tracks, TTS plays chapter

### Phase 5 — Notes System ✅
- [x] Notebooks organizational structure
- [x] Notes CRUD with text mode
- [x] Pen/ink input with stroke capture (Canvas-based)
- [x] Image attachment (NoteImageEntity + file storage)
- [x] Auto verse-linking (regex detection in note text)
- [x] Note → Verse navigation
- [x] Split-screen Bible + Notes editor
- **Verify**: Create sermon note with pen + image + verse links, build clean

### Phase 6 — Polish & Ship
- [ ] Page flip sound asset — source a free public-domain paper-turn sound, bundle as raw resource, wire playback
- [ ] Accessibility audit (screen readers, contrast, touch targets ≥ 48dp)
- [ ] Performance profiling (cold start < 1s, scroll 60fps, release-mode R8)
- [ ] FTS5 migration for search performance
- [ ] F-Droid metadata + screenshots
- [ ] GitHub release with signed APK
- **Verify**: Full test pass, lint clean, APK publishable

---

## 9. Build Configuration

| Parameter | Value |
|---|---|
| **minSdk** | 29 (Android 10) |
| **compileSdk** | 34 (Android 14) |
| **targetSdk** | 34 |
| **Build system** | Gradle 8.4 KTS + version catalog |
| **Language** | Kotlin 2.0.0 |
| **KSP** | 2.0.0-1.0.22 |
| **Compose BOM** | 2024.06.00 |
| **Room** | 2.6.1 |
| **Hilt** | 2.51.1 |
| **Navigation** | 2.7.7 |
| **R8** | Full mode, resource shrinking on |
| **ABI splits** | Universal APK for F-Droid |
| **Signing** | Release signing via CI or manual |

### Dependencies
```kotlin
// Core
androidx.core.ktx
androidx.lifecycle.runtime.ktx
androidx.lifecycle.viewmodel-compose
androidx.lifecycle.runtime-compose
androidx.activity.compose
androidx.navigation.compose

// Compose
androidx.compose.bom
androidx.compose.ui
androidx.compose.ui.graphics
androidx.compose.ui.tooling.preview
androidx.compose.material3
androidx.compose.material.icons.extended
androidx.compose.ui.tooling (debug)

// Data
androidx.room.runtime
androidx.room.ktx
androidx.room.compiler (ksp)
androidx.datastore.preferences

// DI
com.google.dagger.hilt.android
com.google.dagger.hilt.compiler (ksp)
androidx.hilt.navigation.compose

// Widget
androidx.glance
androidx.glance.appwidget

// Coroutines
kotlinx.coroutines.android

// Testing
junit
androidx.room.testing
kotlinx.coroutines.test
org.json (json)
app.cash.turbine
io.mockk
androidx.compose.ui.test

// Data pipeline (Python, dev-only)
data/import_bible.py — fetches scrollmapper sources, builds openbible.db
```

### Key Design Decisions (Updated)
- **Hilt** for DI — single module (`DatabaseModule`), 9 DAO providers + Database + Preferences
- **Room** prepopulated via `createFromAsset("databases/openbible.db")` — generated by `import_bible.py`
- **DataStore** for user preferences (14 keys: theme, fonts, daily verse, TTS, retro, page flip)
- **Strong's + Location importers** — fire-and-forget on first launch, JSON from assets, skip via SharedPreferences
- **Pen input** — custom Compose Canvas drawing engine (`DrawingCanvas.kt`), not an external library
- **No network layer** — fully offline. Device sync architecture is ready but not wired in v1.

---

## 10. Project Structure

```
openbible/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/openbible/
│   │   │   │   ├── OpenBibleApp.kt              // Hilt entry point
│   │   │   │   ├── MainActivity.kt               // Single activity, edge-to-edge
│   │   │   │   ├── di/
│   │   │   │   │   └── DatabaseModule.kt         // Hilt module — 9 DAOs + DB + Prefs
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavGraph.kt               // 14 routes, 6-item bottom nav
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── OpenBibleDatabase.kt  // v5, 18 entities, 4 migrations
│   │   │   │   │   │   ├── entity/               // 18 entity files
│   │   │   │   │   │   ├── dao/                  // 9 DAO interfaces + 3 DTOs
│   │   │   │   │   │   └── converter/            // TypeConverters (Testament, PenMode)
│   │   │   │   │   ├── preferences/
│   │   │   │   │   │   └── UserPreferences.kt    // DataStore (14 keys)
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── NoteRepository.kt     // Notes CRUD + verse linking
│   │   │   │   │   ├── locations/
│   │   │   │   │   │   └── LocationImporter.kt   // JSON → Room on first launch
│   │   │   │   │   ├── translation/
│   │   │   │   │   │   └── TranslationImporter.kt // BBE/NKJV from SQLite assets
│   │   │   │   │   ├── strongs/
│   │   │   │   │   │   └── StrongImporter.kt     // Strong's JSON → Room
│   │   │   │   │   └── model/
│   │   │   │   │       └── Enums.kt             // Testament, ThemeMode, PenMode, HighlightColor
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/                   // Color, Theme, Type, RetroPixel
│   │   │   │   │   ├── home/                    // HomeScreen + HomeViewModel
│   │   │   │   │   ├── bible/                   // BibleScreen, BibleReaderScreen,
│   │   │   │   │   │                            //   BibleViewModel, BookChapterSelector,
│   │   │   │   │   │                            //   TtsControls
│   │   │   │   │   ├── search/                  // SearchScreen + SearchViewModel
│   │   │   │   │   ├── bookmarks/               // BookmarksScreen + BookmarksViewModel
│   │   │   │   │   ├── notes/                   // NotebookListScreen, NoteEditorScreen,
│   │   │   │   │   │                            //   BibleWithNotesScreen, NoteEditorViewModel,
│   │   │   │   │   │                            //   DrawingCanvas
│   │   │   │   │   ├── locations/               // LocationListScreen, LocationDetailScreen,
│   │   │   │   │   │                            //   LocationViewModel
│   │   │   │   │   ├── strongs/                 // StrongSearchScreen, StrongDetailScreen,
│   │   │   │   │   │                            //   StrongVerseBottomSheet, StrongViewModel
│   │   │   │   │   ├── readingplan/             // ReadingPlanScreen + ReadingPlanViewModel
│   │   │   │   │   └── settings/                // SettingsScreen + SettingsViewModel
│   │   │   │   ├── tts/
│   │   │   │   │   └── TtsController.kt         // android.speech.tts wrapper
│   │   │   │   └── notification/
│   │   │   │       ├── DailyVerseReceiver.kt
│   │   │   │       ├── DailyVerseScheduler.kt
│   │   │   │       └── DailyVerseWidgetProvider.kt
│   │   │   ├── assets/
│   │   │   │   ├── databases/
│   │   │   │   │   └── openbible.db             // Prepopulated SQLite (~8MB)
│   │   │   │   ├── locations/
│   │   │   │   │   ├── locations.json
│   │   │   │   │   └── verse_links.json
│   │   │   │   └── strongs/
│   │   │   │       ├── strong_numbers.json
│   │   │   │       └── verse_links.json
│   │   │   ├── res/
│   │   │   │   ├── drawable/                    // Launcher icons, notification icon
│   │   │   │   ├── font/
│   │   │   │   │   └── pixelify_sans.ttf
│   │   │   │   ├── layout/
│   │   │   │   │   └── widget_daily_verse.xml
│   │   │   │   ├── mipmap-{hdpi-mdpi-xhdpi-xxhdpi-xxxhdpi}/
│   │   │   │   ├── values/                      // colors, dimens, strings, themes
│   │   │   │   └── xml/
│   │   │   │       └── widget_daily_verse_info.xml
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   │   └── java/com/openbible/
│   │   │       ├── EntitiesAndEnumsTest.kt      // 31 tests: 18 entities, enums, converters
│   │   │       ├── DataLayerTest.kt             // 15 tests: ReadingPlanSeeder, JSON import patterns, NoteEntity
│   │   │       └── ViewModelTest.kt             // 12 tests: HomeViewModel, StrongViewModel (MockK)
│   │   └── androidTest/                         // Not created yet
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── schemas/
│       └── com.openbible.data.db.OpenBibleDatabase/  // Room schema exports v1-v5
├── data/
│   ├── README.md                                // Pipeline documentation
│   ├── import_bible.py                          // 739-line Python import script
│   └── venv/                                    // Python virtualenv
├── docs/                                        // Empty (registry + handoff cover it)
├── gradle/
│   └── libs.versions.toml                       // Version catalog
├── build.gradle.kts                             // Root (7 lines, plugins only)
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
├── PROJECT_REGISTRY.md                          // This file
└── OPENSOURCE_HANDOFF.md                        // 451-line production handoff document
```

---

## 11. Risk Register

| Risk | Severity | Status | Mitigation |
|---|---|---|---|
| Bible translation copyright claims | High | ✅ Mitigated | Only ship public domain (KJV, WEB, ASV, YLT, BBE). NKJV bundled with copyright notice. Import mechanism for others. |
| Pen input latency on older devices | Medium | ⚠️ Not tested | Canvas-based stroke engine (`DrawingCanvas.kt`), not SurfaceView. Needs testing on API 29 hardware. |
| Page flip animation performance | Medium | ⚠️ Not profiled | Limit to 7"+ devices with hardware acceleration. Current implementation: `AnimatedContent` slide+fade. |
| Room DB with Strong's + Locations data | Low | ✅ Monitored | Core Bible text ~8MB. Strong's + Locations add ~3MB JSON. Total still well within SQLite limits. |
| Widget update rate limits | Low | ✅ Managed | Glance handles. Daily verse widget refreshes on alarm + BOOT_COMPLETED only. |
| TTS voice quality varies by device | Low | ✅ Acceptable | Platform TTS engine. User can install preferred voice. Speed control exposed. |
| Screen rotation state loss | Medium | ✅ Mitigated | ViewModel + SavedStateHandle throughout. |
| Strong's data import failure | Low | ✅ Fire-and-forget | `StrongImporter.importIfNeeded()` — skips on failure, retries on next launch. Non-blocking IO. |
| Location import asset missing | Low | ✅ Graceful | `LocationImporter.loadAsset()` logs warning and continues. App works without location data. |
| Hilt DI misconfiguration | Low | ✅ Compile-time | Dagger validates at compile time. All 9 DAOs have `@Provides` in `DatabaseModule`. |
| Missing prepopulated DB asset | High | ⚠️ Partial mitigation | `try/catch` on asset open (see OPENSOURCE_HANDOFF §11.1 for recommended hardening). |
| No CI — regression risk | Medium | ⚠️ Unaddressed | No `.github/workflows`. Build verification is manual only. |
| `VerseEntity.id` contract drift | Medium | ⚠️ Unaddressed | Stable IDs from `import_bible.py`. No golden-snapshot CI check. |
| `fallbackToDestructiveMigration` | High | ⚠️ Temporary | Acceptable while prepopulated DB regenerated alongside schema. Must replace before production. |
| `runBlocking` in BroadcastReceiver | Low | ⚠️ Fragile | DailyVerseReceiver uses `runBlocking` + Room. Small risk on slow I/O. |
| Search is LIKE-based, no FTS | Medium | ⚠️ Known limit | `LIKE '%query%'` works for current corpus size. FTS5 migration planned for Phase 6. |
| ReadingPlan JSON column | Low | ✅ Accepted | Readings stored as JSON string. Not queryable individually — acceptable for v1 seeder. |
| Manifest exported receivers | Medium | ⚠️ Unaddressed | DailyVerseReceiver + WidgetProvider `exported=true`. Needs signature permission before production. |
| No tests | High | ✅ Mitigated | 58 unit tests across 3 test classes: entities/enums (31), data layer (15), ViewModels (12). All pass. JSON patterns tested with real `org.json:json` library. ViewModel coroutines tested with `Dispatchers.setMain(StandardTestDispatcher)`. |

---

## 12. Engineering Laws Applied to This Project

| Law | How It Applies |
|---|---|
| **Data Structures First** | All 18 entities defined before UI code. Schema is the source of truth. |
| **Remove Special Cases** | Verse linking regex catches `Book Chap:Verse` uniformly. Uniform DAO pattern across all 9 interfaces. |
| **Simplicity First** | Hilt replaced manual DI when complexity crossed the threshold (9 DAOs, importers). Pen input uses Canvas, not a library. Strong's + Locations importers are fire-and-forget. |
| **Hardware Truth** | Pixel theme renders on Canvas. Page flip only on 7"+ devices. TTS uses platform engine. DrawingCanvas optimized for touch/stylus input frequency. |
| **Surgical Changes** | One feature per commit. No drive-by refactors. |
| **Do Not Break Userspace** | Room migrations are backward-compatible (v1→v2, v2→v3, v3→v4, v4→v5). Never delete a column without migration. |
| **Worse is Better** | Ship public domain translations first. FTS5 can wait. Pretty notes UI can wait. Get Bible text working. |
| **Details Matter** | Every entity field justified. Every index has a reason. No magic numbers. Enums are sealed types. |
| **Show Me the Code** | `./gradlew clean assembleDebug` passes. APK installs. Every phase is buildable. |

---

## Next Steps

### ✅ Completed (All Phases 0-5)
- **Phase 0 — Foundation**: Gradle scaffolding, Room v1 schema (18 entities), prepopulated DB pipeline (KJV+WEB+ASV+YLT), DataStore preferences, navigation shell, bottom nav (6 destinations), theme engine (Light/Dark/Sepia)
- **Phase 1 — Bible Reader**: Verse rendering (retro/standard), chapter navigation, two-pane book/chapter selector, font size controls, Pixelify Sans font, Canvas parchment + ornamental borders, page flip animation, TTS controls
- **Phase 2 — Personalization**: Bookmarks with tags, 5-color highlights, reading history + resume, copy verse with citation formatting
- **Phase 3 — Search & Study**: Full-text search (LIKE-based), cross-references inline, Strong's Concordance (search/browse/bottom-sheet), split-pane translations (10"+ tablet), Bible Geography (locations list/map/detail)
- **Phase 4 — Daily Use**: Daily verse notification (configurable time), Glance widget, reading plans (Bible in a Year + progress), TTS per-chapter with play/pause/skip/speed
- **Phase 5 — Notes System**: Notebooks CRUD, text/ink/both modes, Canvas drawing engine, image attachments, auto verse-linking, split-screen Bible+Notes editor

### Remaining Work — Phase 6 (Polish & Ship)
1. **Page flip sound** — source free public-domain paper-turn sound, bundle as raw resource, wire playback
2. **Accessibility audit** — screen readers, contrast ratios, touch targets ≥ 48dp
3. **Performance profiling** — cold start < 1s, scroll 60fps, measure with release-mode R8
4. **FTS5 migration** — replace `LIKE` search with Room FTS for relevance ranking
5. **Prepopulated DB hardening** — replace `fallbackToDestructiveMigration()` with explicit migrations; validate asset presence
6. **CI pipeline** — GitHub Actions: `assembleDebug`, python import check, schema golden snapshot comparison
7. **Security cleanup** — `exported=false` or signature permission on DailyVerseReceiver + WidgetProvider
8. **Tests** — ~~unit tests for DAOs + ViewModels~~ ✅ **Done** (58 tests). UI tests for critical paths remain.
9. **F-Droid metadata** — screenshots, description, build recipe
10. **Signed release APK** — GitHub release with signed universal APK

### Open Items from OPENSOURCE_HANDOFF.md
- [ ] Replace `fallbackToDestructiveMigration()` with explicit Migrations
- [ ] Add FTS5 for search performance
- [ ] Add CI pipeline (build + data import verification)
- [ ] Secure DailyVerseReceiver export with signature permission
- [ ] Document VerseEntity.id generation contract in data/README
- [ ] Add schema-snapshot CI gate to detect import-script drift
- [ ] Normalize reading_plan_days.readings JSON or accept current shape for v1
