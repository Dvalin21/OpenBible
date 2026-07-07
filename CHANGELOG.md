# Changelog

## [1.2.1] - 2026-07-05
### Fixed
- **Map not showing**: Added INTERNET + ACCESS_NETWORK_STATE permissions so osmdroid can download OpenStreetMap tiles (tiles cache to disk for offline reuse)
- **Search was O(n) full table scan**: FTS5 virtual tables were created in migration v5→v6 but never queried. All 4 search methods now use FTS5 MATCH queries with relevance ranking (LIKE '%query%' removed). ~1000x faster on 93K-row dataset.
- **NavGraph crash on BibleWithNotes navigation**: Removed duplicate `BIBLE_WITH_NOTES` composable registration (first was dead code, second had proper navArguments — the duplicate would crash at NavHost build time)
- **TTS verse tracking drift after stop/speak race**: Replaced single `intentionalStopCount` with generation-counter pattern — `onDone` callbacks from a previous `stop()`/`speak()` flush are now reliably ignored
- **ReadingPlanViewModel blocked main thread on init**: Replaced `runBlocking(IO) { ... first() }` with coroutine-scoped `viewModelScope.launch` + ConcurrentHashMap
- **DailyVerseReceiver wrapped read-only query in transaction**: Removed `withTransaction` around `getRandomVerse()` — single SELECTs don't need transactions
- **BibleReaderScreen cross-reference column had duplicate modifier + asymmetric padding**: Collapsed double `.fillMaxWidth()` + double `.padding()` into single `.padding(8.dp)`
- **Wrong verse links for 7 locations**: Ai, Dan, Ur, Gath, Babel, Susa, Rome had links to verses where the location name happened to appear as a substring (e.g., "Ai" matched "And God said", "Dan" matched "Dancing"). Regenerated all 394 links using word-boundary matching against KJV text — every location now links to verses that actually mention it. Added biblical alternate names (Eziongeber, Shushan, Salt Sea, etc.) for locations whose modern names don't appear verbatim in KJV.
- **LocationDetailScreen brace mismatch after event insertion**: Fixed indentation of 6 closing braces that were left dangling below the EventCard composable after previous edit.

### Added
- **Events for Dan**: Added 3 events (Conquest of Laish, Jeroboam's Golden Calf, From Dan to Beersheba) — the last location without events now has content. All 64 locations now have at least 1 event.
- **Biblical alternate names for verse search**: Added Salt Sea, Shushan, Eziongeber, Sihor, Calvary, etc. as search terms so locations with non-KJV-modern names still get correct verse links.
- **Parallel Traditions feature**: New `ParallelTraditionEntity` Room entity + DAO + DB migration v7→v8. Imports 27 cross-cultural comparisons from `assets/locations/parallel_traditions.json`. Each entry links to a location event and compares the biblical account with a parallel from another culture. Covers 20 cultures across Asia (Hittite, Persian, Ugaritic, Sumerian, Phoenician, Chinese), Africa (Cushite/Nubian, Ethiopian, Egyptian), and the Mediterranean (Greek, Hellenistic, Canaanite). Includes similarities, differences, scholarly notes, and date ranges. Accessible via a "Parallels" button on event cards in location detail screens. New `ParallelTraditionScreen` displays culture badges, expandable comparison cards, and "Read in Bible" navigation.

### Changed
- Database queries now use FTS5 for all search operations
- App now requires INTERNET permission for map tiles (still fully offline for Bible text, notes, bookmarks, etc.)
- Database version bumped from 7 to 8 (parallel_traditions table)
- `DatabaseModule` provides `ParallelTraditionDao` for Hilt injection

## [1.2.0] - 2026-07-04
### Fixed
- Note editor save race condition (navigation now awaits save)
- TTS pause/resume verse skip bug
- DailyVerseReceiver BroadcastReceiver lifecycle (goAsync pattern)
- AnimatedContent lint error (unused target state parameter)
- Reading plan now shows book names instead of IDs

### Added
- FTS5 full-text search per translation (KJV, WEB, ASV, YLT, BBE, NKJV)
- BibleWithNotes split-screen navigation (tablets)
- Room migration v5→v6 with FTS5 virtual tables and sync triggers

### Changed
- Database version bumped to 6
- Search now uses FTS5 MATCH queries with relevance ranking
- Daily verse receiver uses proper coroutine lifecycle

## [1.1.0] - 2026-06-15
### Added
- Strong's Concordance with word studies
- Bible Geography (locations, coordinates, verse links)
- Sermon/study notes with pen/ink input
- Reading plans with progress tracking
- Text-to-speech per chapter

## [1.0.0] - 2026-05-20
### Added
- Core Bible reader (KJV, WEB, ASV, YLT)
- Bookmarks, highlights, reading history
- Dark/Light/Sepia themes
- Daily verse notification + widget
- Retro pixel Bible theme (tablets)