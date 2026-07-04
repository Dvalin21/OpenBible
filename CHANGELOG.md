# Changelog

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