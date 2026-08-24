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
1. Tablet layout (from HANDOFF §15.3-15.4 — partially stale now):
   - NavigationRail EXISTS in NavGraph.kt:463 (HANDOFF's "bottom-bar only" claim
     is outdated) — verify it renders on a tablet AVD, don't rebuild it
   - Still open: content max-width on reading screens (only 2 `widthIn` uses in
     BibleScreen.kt); create 10"+ AVD and re-capture screenshots/tablet/
2. Structural items from HANDOFF §11 that still apply (verified this session):
   - exported=true receivers in AndroidManifest.xml (daily verse + widget) — no
     permission gate (§11.7)
   - runBlocking in DailyVerseReceiver — NOT found in current code; re-grep
     before acting (§11.4 may be resolved)
   - No CI (.github/workflows missing) (§11.9)
   - Prepopulated DB lifecycle: try/catch assets check still present at
     OpenBibleDatabase.kt:133-139; explicit migrations now exist for v1→v12 so
     fallbackToDestructiveMigration is GONE (§11.1/§11.3 partially resolved)
   - ReadingPlanSeeder JSON readings column (§11.6) — low priority
   - VerseEntity.id stability contract undocumented (§11.2) — matters more now
     that import_translations.py shares the id space
3. Keith decision pending: map tile source for English labels — LEFT AS-IS per
   Keith ("will live with it or find another avenue")

## Done when
- Tablet AVD run: NavigationRail visible, no text line >~90 chars on reading
  screens, screenshots archived to screenshots/tablet/
- Exported receivers either gated by signature permission or exported=false
- Each remaining fix verified by exercising the real path on emulator/device

## Constraints
- Personal use — copyright OK for NASB/ESV/CSB; do not redistribute the APK publicly
- Map left as-is unless Keith picks a new tile source
- Never regenerate VerseEntity.id assignments casually — cross-references break
- Debug package: com.openbible.debug/com.openbible.MainActivity
- Emulators: emulator-5554 and emulator-5556 both live this session
