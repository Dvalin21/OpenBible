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
| Amplified Bible | AMP | © Lockman Foundation | ❌ No | Requires paid license |
| The Message | MSG | © NavPress | ❌ No | Requires paid license |
| *Future: NET Bible* | NET | © Biblical Studies Press | ⏳ Conditional | Free license with restrictions |

**Strategy**: Ship KJV + WEB bundled. Provide import/download mechanism for others. App never distributes copyrighted text.

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
enum class HighlightColor(val hex: Int) {
    YELLOW(0xFFFFEB3B),
    GREEN(0xFF4CAF50),
    BLUE(0xFF2196F3),
    PINK(0xFFE91E63),
    ORANGE(0xFFFF9800)
}
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

## 7. Feature List (Final, Per Your Direction)

### Tier 1 — Core (MVP)
- [x] Full Bible text — KJV + WEB bundled
- [x] Books, chapters, verses navigation
- [x] Full-text search across all books
- [x] Bookmarks with tags
- [x] Reading history (resume per book)
- [x] Offline-first (all text local, no network needed)
- [x] Dark / Light / Sepia modes
- [x] Font size control
- [x] Copy verse(s) with citation formatting

### Tier 2 — Daily Use
- [x] Daily verse — configurable notification + widget
- [x] Configurable home screen widget (daily verse, reading plan progress, quick nav)
- [x] Reading plans (90-day NT, 1-year, topical)
- [x] Highlighting with color categories
- [x] Cross-references shown inline (linked verses)
- [x] Split-pane: two translations side-by-side (10"+ devices)
- [x] Audio text-to-speech per chapter
- [x] Page flip animation + sound effects (7"+ devices)

### Tier 3 — Power User
- [x] Parallel view up to 4 translations
- [x] Strong's Concordance (public domain data)
- [x] Interlinear with Strong's numbers
- [x] Community reading plans
- [x] Optional device sync (opt-in, no account required)
- [x] Export notes/highlights to Markdown, JSON, plain text
- [x] Biblical maps integration (public domain)
- [x] Fully functional sermon/study notes with pen input, image support, auto verse-linking

### Notes Feature Detail (Per Your Spec)
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
- [x] Bible text data import pipeline (SQLite prepopulation) — KJV + WEB + ASV, 93,286 verses
- [x] DataStore preferences
- [x] Basic navigation shell (bottom nav, screen scaffolding)
- **Verify**: `./gradlew assembleDebug` passes, APK installs

### Phase 1 — Bible Reader ✅ (2026-06-02)
- [x] Verse list with chapter navigation
- [x] Book/chapter selector — two-pane dialog (book list + chapter grid)
- [x] Dark/Light/Sepia theme engine
- [x] Font size control — sliders for verse numbers + text, wired from DataStore
- [x] Retro pixel Bible theme (7"+ detection) — Pixelify Sans font, Canvas parchment background, gold ornamental borders
- [x] Page flip animation — 2D slide + fade via AnimatedContent
- [ ] Page flip sound — preference stored, playback not yet implemented (needs raw audio asset)
- **Verify**: `./gradlew clean assembleDebug` passes, all features render

### Phase 2 — Personalization
- [ ] Bookmarks with tags
- [ ] Highlights with color picker
- [ ] Reading history + resume
- [ ] Copy verse with citation format chooser
- **Verify**: Bookmark, highlight, copy work across rotation

### Phase 3 — Search & Study
- [ ] Full-text search with results grouped by book
- [ ] Cross-reference inline display
- [ ] Strong's Concordance (public domain data)
- [ ] Split-pane translations
- **Verify**: Search all translations, cross-refs render inline

### Phase 4 — Daily Use
- [ ] Daily verse engine
- [ ] Configurable widget
- [ ] Reading plans + progress tracking
- [ ] Text-to-speech audio
- **Verify**: Widget renders, reading plan tracks, TTS plays chapter

### Phase 5 — Notes System
- [ ] Notebooks organizational structure
- [ ] Notes CRUD with text mode
- [ ] Pen/ink input with stroke capture
- [ ] Image attachment
- [ ] Auto verse-linking (regex detection)
- [ ] Note → Verse navigation
- **Verify**: Create sermon note with pen + image + verse links

### Phase 6 — Polish & Ship
- [ ] Accessibility audit (screen readers, contrast, touch targets)
- [ ] Performance profiling (cold start < 1s, scroll 60fps)
- [ ] Translation import mechanism
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
| **Build system** | Gradle KTS + version catalog |
| **Language** | Kotlin 2.0+ |
| **Compose BOM** | 2024.09+ |
| **Room** | 2.6.x |
| **R8** | Full mode, resource shrinking on |
| **ABI splits** | Universal APK for F-Droid |
| **Signing** | Release signing via CI or manual |

### Dependencies (v1)
```kotlin
// Core
androidx.compose.bom
androidx.activity.compose
androidx.navigation.compose
androidx.lifecycle.viewmodel-compose

// Data
androidx.room.runtime
androidx.room.ktx
androidx.room.compiler (ksp)
androidx.datastore.preferences

// UI
androidx.compose.material3
androidx.compose.material3.adaptive
androidx.compose.material.icons.extended
androidx.compose.ui.tooling
androidx.glance (widget)

// TTS
androidx.tts (built-in Android TTS)

// Pen input
androidx.graphics (for ink strokes)
OR custom Canvas-based stroke engine

// Testing
junit
androidx.compose.ui.test
androidx.test.ext.junit
```

---

## 10. Project Structure

```
openbible/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/openbible/
│   │   │   │   ├── OpenBibleApp.kt          // Application class
│   │   │   │   ├── MainActivity.kt           // Single activity
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavGraph.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── OpenBibleDatabase.kt
│   │   │   │   │   │   ├── entity/           // All entities above
│   │   │   │   │   │   ├── dao/              // Data access objects
│   │   │   │   │   │   └── converter/        // Type converters
│   │   │   │   │   ├── preferences/
│   │   │   │   │   │   └── UserPreferences.kt
│   │   │   │   │   └── repository/           // If complexity warrants
│   │   │   │   ├── domain/
│   │   │   │   │   └── model/               // Domain models (not entities)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/                // Color, typography, shapes
│   │   │   │   │   ├── bible/               // Bible reader screen
│   │   │   │   │   ├── search/              // Search screen
│   │   │   │   │   ├── bookmarks/           // Bookmarks screen
│   │   │   │   │   ├── notes/               // Notes + notebook screens
│   │   │   │   │   ├── plans/               // Reading plans screen
│   │   │   │   │   ├── settings/            // Settings screen
│   │   │   │   │   └── components/          // Shared composables
│   │   │   │   ├── widget/                   // Glance widget
│   │   │   │   └── tts/                      // Text-to-speech
│   │   │   ├── assets/
│   │   │   │   ├── fonts/                    // Pixel + serif fonts
│   │   │   │   ├── textures/                 // Paper textures, etc.
│   │   │   │   └── sounds/                   // Page flip sounds
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/                             // Unit tests
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml                    // Version catalog
├── build.gradle.kts                          // Root build file
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── docs/
    └── translations.md                       // Translation licensing doc
```

---

## 11. Risk Register

| Risk | Severity | Mitigation |
|---|---|---|
| Bible translation copyright claims | High | Only ship public domain. Import mechanism for others. |
| Pen input latency on older devices | Medium | Use Canvas-based stroke engine, not SurfaceView. Test on API 29. |
| Page flip animation performance | Medium | Limit to 7"+ devices with hardware acceleration. Fallback to swipe. |
| Room DB size with full Bible text | Low | KJV + WEB = ~8MB total. Well within SQLite limits. |
| Widget update rate limits | Low | Glance handles this. Use minimal update intervals. |
| TTS voice quality varies by device | Low | Use platform TTS engine. User can install preferred voice. |
| Screen rotation state loss | Medium | ViewModel + SavedStateHandle. Test all rotation scenarios. |

---

## 12. Engineering Laws Applied to This Project

| Law | How It Applies |
|---|---|
| **Data Structures First** | All 14 entities defined above. No UI code until these are proven correct. |
| **Remove Special Cases** | Verse linking regex catches `Book Chap:Verse` uniformly. No hand-parsed variants. |
| **Simplicity First** | No Hilt, no Dagger, no network layer in v1. Room DAO → ViewModel → Compose. |
| **Hardware Truth** | Pixel theme renders on Canvas. Page flip only on 7"+ devices. TTS uses platform engine. |
| **Surgical Changes** | One feature per commit. No drive-by refactors. |
| **Do Not Break Userspace** | Room migrations are backward-compatible. Never delete a column without migration. |
| **Worse is Better** | Ship KJV + WEB. Import mechanism for others later. Don't wait for perfect translation licensing. |
| **Details Matter** | Every entity field justified. Every index has a reason. No magic numbers. |
| **Show Me the Code** | Every phase ends with a buildable, installable APK. Proof of progress. |

---

## Next Steps

### ✅ Completed
- **Phase 0**: Foundation — scaffolding, Room schema, 14 entities, 6 DAOs, database import (93,286 verses), DataStore preferences, navigation shell, 5-destination bottom nav, theme engine (Light/Dark/Sepia), retro pixel config
- **Phase 1**: Bible Reader — verse rendering (retro/standard), chapter navigation, two-pane book/chapter selector, font size controls, Pixelify Sans font, Canvas parchment background + gold ornamental borders, page flip animation (2D slide + fade), Settings screen with real preference controls (theme, font sizes, line spacing, retro toggle, page flip toggles, daily verse time)

### Next Up
1. **Phase 2 — Personalization**: Bookmarks with tags, highlights with color picker, reading history + resume, copy verse with citation format chooser
2. **Remaining Phase 1 item**: Page flip sound effect (preference stored, needs raw audio asset + playback)
3. **Phase 3 — Search & Study**: Full-text search, cross-references, Strong's Concordance, split-pane translations
4. **Phase 4 — Daily Use**: Daily verse notification, configurable Glance widget, reading plans, TTS
5. **Phase 5 — Notes System**: Notebooks, text/ink/both modes, image attachments, auto verse-linking
6. **Phase 6 — Polish & Ship**: Accessibility audit, performance profiling, F-Droid metadata, signed release
