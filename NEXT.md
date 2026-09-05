# NEXT.md — OpenBible checkpoint

Read before every action. Update after each step.

---

## Task
Personal-use Bible app (F-Droid requirement dropped 2026-08-24). Carry the
2026-08-24 fix/translation pass forward; remaining work is tablet polish and
the stable structural issues below.

## Verified state (as of commit 39eb008, 2026-08-24)
- Build GREEN (`./gradlew :app:assembleDebug --offline`)
- All P0/P1/P2 review bugs fixed and verified on emulator-5556:
  Strong's lemma/translit data, reader-history gating, ink-default notes,
  proximity eraser, notebook-delete orphans, undo cap, note-search plain-text,
  Float.MIN_VALUE bounds, dead "Create one" button, side-swap toggle
- NASB + ESV + CSB shipped in asset DB (v12, hash b206f0bc81eb46551d197888fc3f5ad3):
  CSB/ESV 31,086 verses each, NASB 31,103; FTS tables built; picker shows all 9
- Translation picker verified on-device; ASV render verified; Strong's G3779
  verified on-device
- Full findings history: REVIEW-FINDINGS.md; prior session log: OPENSOURCE_HANDOFF.md §15

## Next actions (in order)
1. Tablet layout — DONE 2026-09-05 (emulator-5556, 2960x1848 landscape, this tree):
   - NavigationRail renders with all 6 labels (Home/Bible/Search/Saved/Notes/Settings);
     tablet-only compare ⇔ button present in reader header
   - Fixed defeated max-width: BibleScreen inner Box had
     fillMaxSize().widthIn(800.dp) — tight fill swallows a later cap, reader ran
     full-width (~200ch). Reordered to widthIn().fillMaxSize() (compare: 1200.dp),
     matching BibleReaderScreen.kt:43. Verified centered ~72ch column on-device.
   - Fresh shots: screenshots/phone/{home,bible,search}.png,
     screenshots/tablet/{home,bible}.png
2. Structural items from HANDOFF §11 — CLOSED 2026-09-05 (all verified, no images needed):
   - exported=true receivers (§11.7): KEPT, contained by construction. Alarm uses
     explicit immutable PendingIntents (DailyVerseScheduler.kt:38-67); the custom
     SHOW_DAILY_VERSE action has no intent-filter so only our own explicit intent
     fires it; onReceive ignores any other action; widget reads only public verse
     text via immutable tap intents. Manifest comment already documents this.
   - runBlocking in DailyVerseReceiver (§11.4): RESOLVED — zero hits repo-wide;
     receiver uses goAsync + IO coroutine.
   - No CI (§11.9): ADDED .github/workflows/android.yml (JDK 17, unit tests +
     assembleDebug — the same tasks verified green locally).
   - Prepopulated DB lifecycle (§11.1/§11.3): RESOLVED — try/catch assets probe +
     createFromAsset with full explicit migration chain (now 1→15), no
     fallbackToDestructiveMigration anywhere in the tree.
   - ReadingPlanSeeder JSON readings (§11.6): RESOLVED by existing code — reader
     accepts both shapes (ReadingPlanViewModel.kt:136-137), seeder writes the
     documented one (ReadingPlanDayEntity KDoc).
   - VerseEntity.id stability (§11.2): DOCUMENTED on the entity — append-only ids
     (import_bible.py sequential from 1; import_translations.py MAX+1), six
     verseId dependents listed; never regenerate.
3. Pending decision: map tile source for English labels — LEFT AS-IS per
   the owner ("will live with it or find another avenue")

## Done when
- Tablet AVD run: NavigationRail visible, no text line >~90 chars on reading
  screens, screenshots archived to screenshots/tablet/
- Exported receivers either gated by signature permission or exported=false
- Each remaining fix verified by exercising the real path on emulator/device

## Constraints
- Personal use — copyright OK for NASB/ESV/CSB; do not redistribute the APK publicly
- Map left as-is unless a new tile source is picked a new tile source
- Never regenerate VerseEntity.id assignments casually — cross-references break
- Debug package: com.openbible.debug/com.openbible.MainActivity
- Emulators: emulator-5554 and emulator-5556 both live this session
