# OpenBible Full Code Review — Findings
Date: 2026-08-24 · HEAD: 8cd1b43 (with ~8 modified files, uncommitted) · Build: GREEN (`compileDebugKotlin --rerun-tasks`)

Scope: full repo review at `/home/keith/projects/openbible/`. Report-only — nothing was changed.

Every claim below was verified against the shipped prepopulated DB (`app/src/main/assets/databases/openbible.db`, 142MB), the source datasets, or compiler output this session. File:line references included.

---

## P0 — Broken headline features

### P0-1. Strong's search is crippled: ALL 5,523 Greek lexicon entries have EMPTY lemma AND transliteration
**Root cause found — data pipeline bug, verified end-to-end.**

- Shipped DB: `SELECT COUNT(*) FROM strong_numbers WHERE lemma != ''` → **0 rows**. Transliteration populated only for the 8,674 Hebrew entries; all 5,523 Greek entries have `transliteration = ''`.
- Source JSON (`app/src/main/assets/strongs/strong_numbers.json`) **has** the data (`lemma: 'ἀγάπη'`, `translit: 'agápē'`).
- The live upstream dataset (kjvstudy.org greek.json, fetched this session) uses fields `['derivation','kjv_def','lemma','strongs_def','translit']`.
- `data/import_bible.py:831-832` reads the WRONG fields:
  - `entry.get("word", "")` → Greek entries have no `word` field → lemma = `''`
  - `entry.get("transliteration", entry.get("xlit", ""))` → Greek entries have neither `transliteration` nor `xlit` (they use `translit`) → `''`
  - Hebrew works only by accident (`xlit` exists there).
- Same wrong-field mapping duplicated in `data/fetch_strongs_data.py:109-110`.

Consequences in the UI:
1. Searching any Greek word in Greek script (ἀγάπη, λόγος…) returns **nothing** — `StrongDao.kt:43-52` searches lemma, which is empty.
2. Searching by transliteration ("agape", "logos") returns nothing for Greek — transliteration column empty.
3. `StrongSearchScreen.kt:106-112`: result card subtitle renders blank for every Greek entry.
4. `StrongVerseBottomSheet.kt:119` renders `"G26 — "` (number followed by empty lemma) for every interlinear tap.
5. Only English substring-in-definition search works, and it's noisy: definitions contain embedded cross-refs (`from G1537 (ἐκ)`), so typing digits matches hundreds of irrelevant entries.

Fix: remap fields in `import_bible.py` (`lemma`/`translit`, fallback to legacy names), regenerate the prepopulated DB, rebuild asset. Also strip the leading space on definitions (5,490 rows start with `' '` — visible in every card).

### P0-2. Map shows Hebrew labels — tile-source property, not an app bug
`LocationMapScreen.kt:89` uses `TileSourceFactory.MAPNIK`. Standard OSM Mapnik tiles render place names in local language first — in Israel/Palestine that is Hebrew (and Arabic). No code defect; the tiles themselves are pre-rendered images with Hebrew burned in.

Options (pick one):
1. Keep MAPNIK and accept local names (what most Bible apps do).
2. Switch to an English-labeled raster provider usable under F-Droid policy — limited choices; most "English OSM" servers violate the OSM tile policy.
3. Ship a small offline self-hosted tile pack of the Levant rendered with English-only labels (fits the app's offline-first story; more work).

Decision needed from you before anyone codes this.

### P0-3. New notes open in TEXT mode: handwriting attempts become typed words
- `NoteEditorViewModel.kt:20` — `NoteEditorState.penMode: PenMode = PenMode.TEXT`. A brand-new note (`seedNewNote`, line 106) keeps TEXT.
- In TEXT mode the body is a plain `BasicTextField` (`NoteEditorScreen.kt:344-347`). There is **no handwriting-to-text conversion anywhere in the codebase** (no OCR/ink-recognition dependency) — what you observed is simply the keyboard text field receiving your touches.
- The mode toggle is an unlabeled icon cycling TEXT→INK→BOTH (`NoteEditorScreen.kt:203-220`); nothing tells the user ink exists.
- Samsung-Notes-parity gaps in the ink implementation itself (see P1-4/P2 items below): eraser deletes whole strokes by bounding box, fountain-pen bakes a single pressure value per stroke, no palm rejection, no stroke smoothing.

Minimum fix: default new notes to INK (or BOTH), label the mode toggle, and surface the tool row when a pen/stylus is detected.

---

## P1 — Correctness bugs

### P1-1. Reading history records the WRONG chapter on `bible/{t}/{b}/{c}` navigation
`BibleScreen.kt:182-192`: `LaunchedEffect(selectedChapter) { viewModel.recordReading() … }` fires on the FIRST composition with the ViewModel's initial state (`chapter=1` / last-read position from prefs) *before* the `initialChapter` argument from the route is applied (line 175). Every cold navigation to a specific chapter therefore logs a spurious read of Genesis 1 (or whatever the VM happened to be showing). Reading-history stats and "Continue Reading" are polluted. Fix: gate recording until initial navigation has been consumed (e.g. skip the first emission, or record only when `verses` for the target chapter are non-empty).

### P1-2. Two preference collectors fight over the reader's position
`BibleViewModel.kt:307-320`: `init` collects `defaultTranslation` AND `lastReadingPosition`, both unconditionally writing `_selectedTranslationId/_selectedBookId/_selectedChapter`. Any re-emission of `lastReadingPosition` (which `recordReading()` writes on every chapter change) can clobber a just-selected book/chapter depending on collector ordering. Combined with P1-1 this makes "navigate to X, land on Y" possible. Fix: apply lastReadingPosition once at init (first()), not as a continuous collector; let explicit selections win.

### P1-3. "Create one" notebook button does nothing
`NoteEditorScreen.kt:258` wires `onCreateClick = onNotebooksClick`, and `onNotebooksClick` defaults to `{}` — `NavGraph.kt:306-325` never passes it. When a user has no notebooks, the editor shows "No notebooks — Create one" and the button is dead. Classic fake affordance. Fix: pass a real navigation (or an inline create dialog) or remove the button.

### P1-4. Eraser destroys entire strokes by bounding-box intersection
`NoteCanvas.kt:184-193`: an erase gesture deletes any element whose **bounding rectangle** (plus threshold) intersects the eraser path's bounding rectangle. Drawing near (not through) a long diagonal stroke erases it completely. Samsung Notes erases what you actually rub over. Fix: point-segment distance against the stored polyline, split stroke at intersection instead of deleting whole element.

### P1-5. Fountain pen pressure sampled once per stroke
`NoteCanvas.kt:125`: width = `penSize * touch.pressure` evaluated from the FINAL pointer event and applied to the whole stroke. Pressure variation across the stroke is discarded, so the "pressure-responsive" pen (advertised in `NoteCanvasModel.kt:26-27`) renders uniform width. Needs per-point width capture (`List<Pair<Offset, Float>>`) and variable-width rendering.

### P1-6. NKJV is advertised everywhere but has no data source
- `MIGRATION_3_4` (`OpenBibleDatabase.kt:187-196`) inserts NKJV with `isBundled=1`; comment says "imported separately via TranslationImporter".
- `TranslationImporter` expects `assets/translations/nkjv.db` — **the directory does not exist in the repo**.
- `BibleDao.kt:20` adds "nkjv" to `FTS_TRANSLATIONS`; FTS table `verses_fts_nkjv` exists but is empty (0 NKJV verses in shipped DB — verified).
- README advertises "BBE + NKJV import on first launch".
Result: a permanently-empty translation wired through four layers. Either ship the data (NKJV is copyrighted — Thomas Nelson; cannot ship in an F-Droid APK at all) or delete the NKJV rows/migration/FTS wiring. As shipped it's a dead feature that silently wastes migration code and misleads contributors.

---

## P2 — Latent bugs / data risks

### P2-1. Deleting a notebook orphans its notes
`NoteDao.kt:29-31` `@Delete deleteNotebook` removes only the notebook row. Notes keep `notebookId` pointing at a ghost; they disappear from their notebook view (`NotebookListScreen.kt:82` filters by id) and survive only in "All notes". No FK cascade declared. Fix: reassign notes to null (or delete) in a transaction.

### P2-2. `Float.MIN_VALUE` used as "negative infinity" in InkElement bounds
`NoteCanvasModel.kt:67-68`: `var maxX = Float.MIN_VALUE` — Kotlin's `Float.MIN_VALUE` is the smallest **positive** number (1.4E-45), not `-MAX_VALUE`. Any stroke whose max x/y ≤ 0 gets a corrupted bounding box, breaking selection/hit-test/erase for it. Currently masked because canvas coords are non-negative. Use `-Float.MAX_VALUE`.

### P2-3. Note list search matches raw JSON
`NotebookListScreen.kt:71-77` searches `pagesJson` verbatim — matches JSON syntax, escaped unicode, and element metadata, producing nonsense hits. Extract plain text (`pages` → `text` fields) for search.

### P2-4. Undo stack is unbounded and stores full JSON snapshots
`NoteEditorViewModel.kt:260-263`: every stroke pushes a complete `pagesToJson` snapshot onto `undoStack` with no cap. A long ink session on a multi-page note grows MBs on the Java heap. Cap depth (e.g. 50) or switch to element-level ops.

### P2-5. Widget `PendingIntent` extras without explicit activity action
`DailyVerseWidgetProvider.kt` builds the tap intent with only `FLAG_ACTIVITY_NEW_TASK|CLEAR_TOP` and extras. Works, but note `DailyVerseReceiver` is `exported="true"` (manifest) with a BOOT_COMPLETED filter — required, but any external app can also fire it; it only reads public DB content, so impact is low. Already on the handoff open-items list.

### P2-6. `ParallelTraditionImporter.kt:86-87` — `optString(key, null)` type-mismatch warnings
Compiler flags these two lines (Java overload ambiguity). Behaviorally fine today, but it's the only non-deprecation warning in the build; clean it up before it bites on an AGP bump.

### P2-7. Coordinates display hardcodes N/E
`LocationDetailScreen.kt:169` formats `"%.4f°N, %.4f°E"` unconditionally. Harmless for biblical locations (all N/E) but wrong-by-construction if data ever includes a western/southern site.

---

## P3 — Tech debt / polish

- **P3-1. Docs drift**: `openwiki/NAVIGATION.md` documents a `NOTE/{noteId}` → NoteDetailScreen route that does not exist (grep: 0 references). Wiki should match `Routes.kt`.
- **P3-2. Deprecated icon properties**: ~15 warnings (`Icons.Default.Undo`, `KeyboardArrowLeft`, `ArrowForward`, …). The uncommitted diff "fixed" several to non-AutoMirrored variants that are STILL deprecated (`Icons.Filled.KeyboardArrowLeft` etc.) — the fix is the `AutoMirrored` namespace, see `LocationMapScreen.kt:7` for correct import.
- **P3-3. Uncommitted work**: 8 modified files sit in the working tree (session-log fixes from 2026-08-12). Commit or discard before further changes — right now there's no clean restore point for them.
- **P3-4. `HomeScreen` Geography card bypasses the location list**: `NavGraph.kt:222` routes Home→LOCATION_MAP directly; `LocationListScreen` is reachable only via the map's "List" text button. Consider Home→LOCATIONS (list) with map one tap away — list is faster to scan.
- **P3-5. `NoteCanvas` pointerInput keyed on `page`** (`NoteCanvas.kt:95`): the gesture detector restarts on every committed edit (every stroke). Benign today because commits happen on pointer-up, but it discards in-flight gesture state if anything else mutates the page mid-gesture (e.g. text edit in BOTH mode). Key on stable identity (pageIndex) instead.
- **P3-6. Verse-line click is a no-op**: `BibleScreen.kt:771` `onClick = { /* scroll or select — future use */ }` — dead affordance on the primary reading surface.

## Verified NOT broken (checked, working)
- Navigation graph: every `navigate()` target has a registered `composable()` (all 17 routes cross-checked). Bottom bar/rail selection logic correct; tablet rail IS wired (`NavGraph.kt:168-179` — the older HANDOFF claim "NavigationRail missing" is FALSE for current HEAD).
- Bible FTS search: sanitizer strips FTS operators; UNION-all query binds params correctly; chips only show translations that actually have data.
- Strong's number-prefix path ("G1234", "g26") resolves correctly against the DB.
- verse_strong_links integrity: 444,076 links, 0 orphans, all anchored to KJV verse ids (by design — interlinear is KJV-only; detail screen joins correctly).
- Room schema v8 migrations chain 1→8 complete; prepopulated asset opens cleanly; Timber logging added to the previously-silent catch.
- Mic permission flow (RECORD_AUDIO request launcher) correctly gates recording.

## Root causes (cross-cutting)
1. **The data pipeline is unverified.** P0-1 (empty lemma), P1-6 (missing NKJV), and the gitignored prepopulated DB share one cause: nothing checks that imported columns are actually populated. A 10-line row-count/non-null assertion in `import_bible.py` would have caught both.
2. **Feature shells shipped ahead of behavior.** NKJV wiring, "Create one" button, verse-click, NoteDetail doc — each layer believes another layer owns the functionality.
3. **Samsung-Notes parity is visual, not behavioral.** Pen types/size presets mimic the UI, but erase granularity and pressure handling — the parts users actually feel — were not ported.
