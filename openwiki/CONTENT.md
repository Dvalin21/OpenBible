# Content Data Files

All JSON files in `assets/locations/`.

## locations.json
- 64 biblical locations with: id, name, modern_name, latitude, longitude, description, category, significance
- Categories: city, region, body_of_water, mountain, wilderness, nation, island
- IDs are snake_case (e.g., `jerusalem`, `sea_of_galilee`, `mount_sinai`)
- **IDs must stay stable** — `events.json` and `parallel_traditions.json` FK to `locations.id`
- Modern names include biblical alternate names as fallbacks for verse linking

## verse_links.json
- 394 links mapping locations to Bible verses
- Uses word-boundary regex (`\b`) matching against KJV text
- Includes demonym patterns (e.g., Corinth→Corinthians)
- Format: `{ "locationId": "corinth", "verseId": 12345, "reference": "Acts 18:1" }`

## events.json
- 102 events across all 64 locations
- Fields: id, locationId, title, description, reference, bookId, chapter, category, era, sortOrder
- Categories: battle, birth, covenant, exile, healing, judgment, law, miracle, prophecy, resurrection, visit, worship
- Eras: Patriarchal, Exodus, Conquest, Judges, Monarchy, Exile, Post-Exile, Intertestamental, Gospel, Apostolic, Apocalyptic
- Book IDs use canonical numbers (Genesis=1, Matthew=40, Revelation=66)

## parallel_traditions.json
- 39 entries across 25 cultures
- Fields: id, eventId (nullable FK→location_events), biblicalReference, biblicalBookId, biblicalChapter, culture, documentName, title, description, similarities, differences, scholarlyNote, dateRange, category, sortOrder
- Categories: battle, birth, covenant, creation, flood, judgment, law, miracle, prophecy, resurrection, ritual, wisdom
- Can link to an existing event OR stand alone (eventId=null)
- Including scholarly notes with citations is strongly encouraged

## Import Flow
1. `OpenBibleApp.onCreate()` launches `CoroutineScope(IO)` importers
2. Each importer reads from assets, batch-inserts via DAO, tracks completion via SharedPreferences
3. Importers are idempotent — checking a flag before running
4. EventImporter and ParallelTraditionImporter must run AFTER LocationImporter
