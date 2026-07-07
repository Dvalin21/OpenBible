# OpenBible Architecture

## Stack
- **Language**: Kotlin (Java 17 compatible)
- **UI**: Jetpack Compose + Material 3 (MaterialYou theming)
- **DI**: Hilt (Dagger under the hood)
- **Database**: Room 2.6.1 (SQLite, prepopulated via `createFromAsset`)
- **Preferences**: DataStore (Preferences)
- **Maps**: osmdroid (OpenStreetMap tiles, offline caching)
- **TTS**: Android `TextToSpeech` API
- **Notifications**: `AlarmManager` + `BroadcastReceiver` for daily verse
- **Min SDK**: 29 (Android 10), Target: 34 (Android 14)
- **Build**: Gradle Kotlin DSL (AGP 8.x)

## Project Layout
```
app/src/main/java/com/openbible/
├── OpenBibleApp.kt              # Application class — Hilt entry, runs importers on launch
├── MainActivity.kt              # Single Activity, hosts Compose NavHost
├── data/
│   ├── db/                      # Room database, DAOs, entities
│   │   ├── OpenBibleDatabase.kt # Singleton builder, migrations 1→2→...→8
│   │   ├── dao/                 # 9 DAOs (BibleDao, BookmarkDao, LocationDao, etc.)
│   │   ├── entity/              # 20 entities
│   │   └── converter/           # TypeConverters
│   ├── preferences/             # DataStore UserPreferences
│   ├── locations/               # Importers for locations, events, parallel traditions
│   ├── translation/             # TranslationImporter (BBE/NKJV from separate assets)
│   ├── strongs/                 # StrongImporter
│   └── repository/              # Optional repository layer (limited use)
├── ui/
│   ├── bible/                   # BibleReaderScreen, BookChapterSelector, etc.
│   ├── home/                    # HomeScreen, HomeViewModel
│   ├── locations/               # LocationListScreen, LocationDetailScreen, MapScreen, ParallelTraditionScreen
│   ├── notes/                   # Notes CRUD
│   ├── readingplan/             # Reading plan screens
│   ├── search/                  # Search screen
│   ├── strongs/                 # Strong's Concordance screens
│   └── tts/                     # TTS reader (Text-to-Speech overlay)
├── navigation/
│   ├── NavGraph.kt              # All routes defined here
│   └── Routes.kt                # Route constants
├── notification/                # DailyVerseReceiver, notification channel
├── widget/                      # Android home screen widget
└── di/                          # Hilt modules (DatabaseModule, etc.)
```

## Key Patterns
- **Single Activity**, Compose NavHost for all navigation
- **ViewModel** per screen, injected via Hilt `hiltViewModel()`
- **Importers** run once on first launch via `SharedPreferences` flag (`importIfNeeded()`)
- **FTS5 search** via `@RawQuery` + `SimpleSQLiteQuery` (Room can't validate dynamic SQL against virtual tables)
- **StateFlow** for reactive UI state
- **Offline-first** — Bible text is prepopulated in assets; map tiles cache to disk
