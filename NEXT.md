# NEXT.md — OpenBible review follow-up

## Task
Full code review of OpenBible (report-only pass). Findings delivered.

## Verified state
- Full report: `/home/keith/projects/openbible/REVIEW-FINDINGS.md` (P0×3, P1×6, P2×7, P3×6)
- Build green (`compileDebugKotlin --rerun-tasks`), only 2 non-deprecation warnings
- Working tree has ~8 uncommitted modified files from a prior session — commit/discard FIRST

## Next actions (in order)
1. Keith decision needed: map tile source for English labels (P0-2 options in findings)
2. Commit or discard the uncommitted working-tree changes (P3-3) before any fix lands
3. Fix P0-1 Strong's data: remap `lemma`/`translit` fields in `data/import_bible.py` (~line 831), regenerate prepopulated DB, rebuild asset
4. Default new notes to INK/BOTH mode + label the pen-mode toggle (P0-3)
5. Gate reading-history recording until initial chapter nav consumed (P1-1) and make lastReadingPosition apply-once (P1-2)

## Done when
- Each fix verified by exercising the real path on emulator/device (Strong's search returns Greek words; ink is default on new note; history records the navigated chapter)

## Constraints
- Report-only session: nothing changed yet
- NKJV cannot ship verse data in an F-Droid APK (Thomas Nelson copyright) — delete wiring or leave empty knowingly
- F-Droid only app: no non-free deps when picking a map tile strategy
