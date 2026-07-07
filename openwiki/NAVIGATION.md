# Navigation

## Routes (all defined in `Routes.kt`, wired in `NavGraph.kt`)

| Route | Screen | Notes |
|-------|--------|-------|
| `HOME` | HomeScreen | Dashboard: quick actions, cards, reading resume |
| `BIBLE/{translationId}/{bookId}/{chapter}` | BibleReaderScreen | Main Bible reader with TTS, highlight, bookmark |
| `BIBLE_WITH_NOTES/{translationId}/{bookId}/{chapter}` | BibleReaderScreen | Same but with notes panel open |
| `SEARCH` | SearchScreen | FTS5-powered full-text search |
| `NOTES` | NotesListScreen | Browse/create/edit notes + notebooks |
| `NOTE/{noteId}` | NoteDetailScreen | Single note with verse links, images |
| `READING_PLANS` | ReadingPlanListScreen | Bible reading plans |
| `READING_PLAN/{planId}` | ReadingPlanDetailScreen | Day-by-day plan view |
| `STRONG_SEARCH` | StrongSearchScreen | Search Strong's numbers |
| `STRONG_DETAIL/{number}` | StrongDetailScreen | Word study with occurrences |
| `LOCATIONS` | LocationListScreen | All 64 biblical locations |
| `LOCATION_DETAIL/{locationId}` | LocationDetailScreen | Location info + events + map + parallels |
| `LOCATION_MAP` | LocationMapScreen | Full-screen osmdroid map |
| `PARALLEL_TRADITIONS` | ParallelTraditionScreen | Browse all 39 entries |
| `PARALLEL_TRADITIONS_EVENT/{eventId}` | ParallelTraditionScreen | Filtered by event |
| `DAILY_VERSE` | — | Widget launch point |

## Navigation Patterns
- `navController.navigate(Route)` with `launchSingleTop=true` to avoid stack duplication
- Back navigation via `navController.popBackStack()`
- NavHost uses `composable()` with `navArguments` for parameterized routes
- Deep links: `BIBLE_WITH_NOTES` route has explicit `NavType` declarations for arguments
