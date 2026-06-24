#!/usr/bin/env python3
"""
OpenBible — Bible Text Data Import Pipeline
=============================================

Downloads public domain Bible translations (KJV, WEB, ASV) and generates
a prepopulated SQLite database for Room's createFromAsset().

Usage:
    python3 import_bible.py
    cp openbible.db ../app/src/main/assets/databases/

Output:
    openbible.db — SQLite3 database with Room-compatible schema

Data Source:
    https://github.com/midvash/bible-data — public domain Bible texts
    in JSON format with consistent schema across all translations.
"""

import sqlite3
import sys
import os
import json
import re
import time
import zipfile
import io
from typing import List, Dict, Tuple, Optional
import urllib.request

# ── Configuration ────────────────────────────────────────────────

BASE_URL = "https://raw.githubusercontent.com/midvash/bible-data/main/versions/en/{slug}/{slug}.json"
CROSS_REF_URL = "https://a.openbible.info/data/cross-references.zip"

TRANSLATIONS = {
    "kjv": {
        "name": "King James Version",
        "abbreviation": "KJV",
        "slug": "kjv",
        "copyright": "Public domain",
    },
    "web": {
        "name": "World English Bible",
        "abbreviation": "WEB",
        "slug": "web",
        "copyright": "Public domain",
    },
    "asv": {
        "name": "American Standard Version",
        "abbreviation": "ASV",
        "slug": "asv",
        "copyright": "Public domain",
    },
}

# Books of the Bible in canonical order (used for mapping and ordering)
BOOKS = [
    # id, name, abbreviation, chapters, testament
    (1, "Genesis", "Gen", 50, "OLD"),
    (2, "Exodus", "Exod", 40, "OLD"),
    (3, "Leviticus", "Lev", 27, "OLD"),
    (4, "Numbers", "Num", 36, "OLD"),
    (5, "Deuteronomy", "Deut", 34, "OLD"),
    (6, "Joshua", "Josh", 24, "OLD"),
    (7, "Judges", "Judg", 21, "OLD"),
    (8, "Ruth", "Ruth", 4, "OLD"),
    (9, "1 Samuel", "1Sam", 31, "OLD"),
    (10, "2 Samuel", "2Sam", 24, "OLD"),
    (11, "1 Kings", "1Kgs", 22, "OLD"),
    (12, "2 Kings", "2Kgs", 25, "OLD"),
    (13, "1 Chronicles", "1Chr", 29, "OLD"),
    (14, "2 Chronicles", "2Chr", 36, "OLD"),
    (15, "Ezra", "Ezra", 10, "OLD"),
    (16, "Nehemiah", "Neh", 13, "OLD"),
    (17, "Esther", "Esth", 10, "OLD"),
    (18, "Job", "Job", 42, "OLD"),
    (19, "Psalm", "Ps", 150, "OLD"),
    (20, "Proverbs", "Prov", 31, "OLD"),
    (21, "Ecclesiastes", "Eccl", 12, "OLD"),
    (22, "Song of Solomon", "Song", 8, "OLD"),
    (23, "Isaiah", "Isa", 66, "OLD"),
    (24, "Jeremiah", "Jer", 52, "OLD"),
    (25, "Lamentations", "Lam", 5, "OLD"),
    (26, "Ezekiel", "Ezek", 48, "OLD"),
    (27, "Daniel", "Dan", 12, "OLD"),
    (28, "Hosea", "Hos", 14, "OLD"),
    (29, "Joel", "Joel", 3, "OLD"),
    (30, "Amos", "Amos", 9, "OLD"),
    (31, "Obadiah", "Obad", 1, "OLD"),
    (32, "Jonah", "Jonah", 4, "OLD"),
    (33, "Micah", "Mic", 7, "OLD"),
    (34, "Nahum", "Nah", 3, "OLD"),
    (35, "Habakkuk", "Hab", 3, "OLD"),
    (36, "Zephaniah", "Zeph", 3, "OLD"),
    (37, "Haggai", "Hag", 2, "OLD"),
    (38, "Zechariah", "Zech", 14, "OLD"),
    (39, "Malachi", "Mal", 4, "OLD"),
    # New Testament
    (40, "Matthew", "Matt", 28, "NEW"),
    (41, "Mark", "Mark", 16, "NEW"),
    (42, "Luke", "Luke", 24, "NEW"),
    (43, "John", "John", 21, "NEW"),
    (44, "Acts", "Acts", 28, "NEW"),
    (45, "Romans", "Rom", 16, "NEW"),
    (46, "1 Corinthians", "1Cor", 16, "NEW"),
    (47, "2 Corinthians", "2Cor", 13, "NEW"),
    (48, "Galatians", "Gal", 6, "NEW"),
    (49, "Ephesians", "Eph", 6, "NEW"),
    (50, "Philippians", "Phil", 4, "NEW"),
    (51, "Colossians", "Col", 4, "NEW"),
    (52, "1 Thessalonians", "1Thess", 5, "NEW"),
    (53, "2 Thessalonians", "2Thess", 3, "NEW"),
    (54, "1 Timothy", "1Tim", 6, "NEW"),
    (55, "2 Timothy", "2Tim", 4, "NEW"),
    (56, "Titus", "Titus", 3, "NEW"),
    (57, "Philemon", "Phlm", 1, "NEW"),
    (58, "Hebrews", "Heb", 13, "NEW"),
    (59, "James", "Jas", 5, "NEW"),
    (60, "1 Peter", "1Pet", 5, "NEW"),
    (61, "2 Peter", "2Pet", 3, "NEW"),
    (62, "1 John", "1John", 5, "NEW"),
    (63, "2 John", "2John", 1, "NEW"),
    (64, "3 John", "3John", 1, "NEW"),
    (65, "Jude", "Jude", 1, "NEW"),
    (66, "Revelation", "Rev", 22, "NEW"),
]

# Build name → id mapping for midvash book names
# midvash uses names like "1 Samuel" — same as our BOOKS list
BOOK_NAME_TO_ID = {book[1]: book[0] for book in BOOKS}
# Also map abbreviations just in case
BOOK_ABBR_TO_ID = {book[2]: book[0] for book in BOOKS}


def download_json(url: str) -> Optional[dict]:
    """Download JSON Bible data from midvash/bible-data."""
    print(f"  Downloading: {url}")
    try:
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "OpenBible-Import/1.0"},
        )
        with urllib.request.urlopen(req, timeout=120) as f:
            data = json.loads(f.read().decode("utf-8"))
        print(f"  Downloaded successfully")
        return data
    except Exception as e:
        print(f"  ERROR: {e}", file=sys.stderr)
        return None


def create_database(output_path: str):
    """Create the prepopulated SQLite database with Room-compatible schema."""

    if os.path.exists(output_path):
        os.remove(output_path)

    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()

    # Performance pragmas
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("PRAGMA synchronous=OFF;")
    cursor.execute("PRAGMA cache_size=-64000;")  # 64MB cache

    # ── Create tables matching Room entities ─────────────────────

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS translations (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            abbreviation TEXT NOT NULL,
            language TEXT NOT NULL DEFAULT 'en',
            copyright TEXT,
            isPublicDomain INTEGER NOT NULL DEFAULT 1,
            isBundled INTEGER NOT NULL DEFAULT 1
        );
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER NOT NULL PRIMARY KEY,
            translationId TEXT NOT NULL,
            name TEXT NOT NULL,
            abbreviation TEXT NOT NULL,
            number INTEGER NOT NULL,
            chapterCount INTEGER NOT NULL,
            testament TEXT NOT NULL,
            totalVerses INTEGER NOT NULL DEFAULT 0
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_books_translation ON books(translationId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS verses (
            id INTEGER NOT NULL PRIMARY KEY,
            translationId TEXT NOT NULL,
            bookId INTEGER NOT NULL,
            chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL,
            text TEXT NOT NULL
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_verses_book ON verses(bookId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_verses_translation ON verses(translationId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_verses_chapter ON verses(translationId, bookId, chapter);"
    )
    cursor.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_verses_unique ON verses(translationId, bookId, chapter, verse);"
    )

    # Cross-references (prepopulated from Treasury of Scripture Knowledge)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cross_references (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            fromVerseId INTEGER NOT NULL,
            toBookId INTEGER NOT NULL,
            toChapter INTEGER NOT NULL,
            toVerseStart INTEGER NOT NULL,
            toVerseEnd INTEGER,
            relevance INTEGER NOT NULL DEFAULT 0
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_cr_from ON cross_references(fromVerseId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_cr_to ON cross_references(toBookId, toChapter, toVerseStart);"
    )

    # User-data tables (empty — populated at runtime)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS bookmarks (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            verseId INTEGER NOT NULL,
            label TEXT,
            createdAt INTEGER NOT NULL,
            tags TEXT
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_bookmarks_verse ON bookmarks(verseId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_bookmarks_time ON bookmarks(createdAt);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS highlights (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            verseId INTEGER NOT NULL,
            color INTEGER NOT NULL,
            createdAt INTEGER NOT NULL
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_highlights_verse ON highlights(verseId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS notebooks (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            color INTEGER NOT NULL DEFAULT -1033074,
            icon TEXT,
            createdAt INTEGER NOT NULL
        );
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            notebookId INTEGER,
            title TEXT NOT NULL,
            contentText TEXT,
            penStrokes TEXT,
            penMode TEXT NOT NULL DEFAULT 'TEXT',
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            tags TEXT,
            color INTEGER
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_notes_notebook ON notes(notebookId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(updatedAt);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS note_images (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            noteId INTEGER NOT NULL,
            filePath TEXT NOT NULL,
            caption TEXT,
            position INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_note_images_note ON note_images(noteId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS note_verse_links (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            noteId INTEGER NOT NULL,
            verseId INTEGER NOT NULL,
            FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE,
            FOREIGN KEY (verseId) REFERENCES verses(id) ON DELETE CASCADE
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_nvl_note ON note_verse_links(noteId);"
    )
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_nvl_verse ON note_verse_links(verseId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS reading_plans (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            description TEXT,
            durationDays INTEGER NOT NULL,
            isPrebuilt INTEGER NOT NULL DEFAULT 0
        );
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS reading_plan_days (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            planId INTEGER NOT NULL,
            dayNumber INTEGER NOT NULL,
            title TEXT,
            readings TEXT NOT NULL,
            FOREIGN KEY (planId) REFERENCES reading_plans(id) ON DELETE CASCADE
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_rpd_plan ON reading_plan_days(planId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS reading_progress (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            planId INTEGER NOT NULL,
            dayNumber INTEGER NOT NULL,
            completed INTEGER NOT NULL DEFAULT 0,
            completedAt INTEGER,
            FOREIGN KEY (planId) REFERENCES reading_plans(id) ON DELETE CASCADE
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_rp_plan ON reading_progress(planId);"
    )

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS reading_history (
            verseId INTEGER NOT NULL PRIMARY KEY,
            lastReadAt INTEGER NOT NULL,
            readCount INTEGER NOT NULL DEFAULT 0
        );
    """)
    cursor.execute(
        "CREATE INDEX IF NOT EXISTS idx_rh_time ON reading_history(lastReadAt);"
    )

    # ── Insert Translations ─────────────────────────────────────

    for tid, info in TRANSLATIONS.items():
        cursor.execute(
            "INSERT INTO translations (id, name, abbreviation, language, copyright, isPublicDomain, isBundled) "
            "VALUES (?, ?, ?, 'en', ?, 1, 1)",
            (tid, info["name"], info["abbreviation"], info["copyright"]),
        )

    # ── Insert Books (same IDs across all translations) ─────────

    for book_num, name, abbr, chapters, testament in BOOKS:
        for tid in TRANSLATIONS:
            cursor.execute(
                "INSERT OR IGNORE INTO books "
                "(id, translationId, name, abbreviation, number, chapterCount, testament, totalVerses) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                (book_num, tid, name, abbr, book_num, chapters, testament),
            )

    # ── Download and Insert Verses ──────────────────────────────

    total_verses = 0
    global_verse_id = 1

    for tid, info in TRANSLATIONS.items():
        print(f"\nProcessing {info['name']} ({info['abbreviation']}):")
        url = BASE_URL.format(slug=info["slug"])
        data = download_json(url)

        if not data:
            print(f"  WARNING: No data for {tid}", file=sys.stderr)
            continue

        # midvash JSON structure:
        # {
        #   "version": "kjv",
        #   "name": "King James Version",
        #   "books": [
        #     {
        #       "book": "Gen",
        #       "bookId": 1,
        #       "englishName": "Genesis",
        #       "testament": "OT",
        #       "chapters": [
        #         {
        #           "chapter": 1,
        #           "verses": [
        #             {"number": 1, "text": "In the beginning..."},
        #             ...
        #           ]
        #         }
        #       ]
        #     }
        #   ]
        # }

        books_data = data.get("books", [])
        if not books_data:
            print(f"  ERROR: No books in JSON data", file=sys.stderr)
            continue

        book_verse_counts: Dict[int, int] = {}
        batch = []

        for book_entry in books_data:
            book_name = book_entry.get("englishName", "")
            book_id = book_entry.get("bookId")

            # Validate we know this book
            canonical_id = BOOK_NAME_TO_ID.get(book_name)
            if canonical_id is None or canonical_id != book_id:
                if book_id is not None:
                    # Accept the bookId from the data if it matches our range
                    if 1 <= book_id <= 66:
                        pass  # Use book_id as-is
                    else:
                        print(f"  WARNING: Unknown book '{book_name}' (id={book_id}), skipping")
                        continue
                else:
                    print(f"  WARNING: Unknown book '{book_name}', skipping")
                    continue

            chapters = book_entry.get("chapters", [])
            for chapter_entry in chapters:
                chapter_num = chapter_entry.get("chapter", 1)
                verses = chapter_entry.get("verses", [])

                for verse_entry in verses:
                    verse_num = verse_entry.get("number", 0)
                    text = verse_entry.get("text", "").strip()

                    if not text:
                        continue

                    batch.append((
                        global_verse_id,
                        tid,
                        book_id,
                        chapter_num,
                        verse_num,
                        text,
                    ))
                    book_verse_counts[book_id] = book_verse_counts.get(book_id, 0) + 1
                    global_verse_id += 1
                    total_verses += 1

                    # Flush batch every 500 verses
                    if len(batch) >= 500:
                        cursor.executemany(
                            "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) "
                            "VALUES (?, ?, ?, ?, ?, ?)",
                            batch,
                        )
                        batch = []

        # Flush remaining
        if batch:
            cursor.executemany(
                "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                batch,
            )

        # Update totalVerses per book for this translation
        for bid, count in book_verse_counts.items():
            cursor.execute(
                "UPDATE books SET totalVerses = ? WHERE id = ? AND translationId = ?",
                (count, bid, tid),
            )

        print(f"  Inserted {sum(book_verse_counts.values()):,} verses")

    # ── Insert Prebuilt Reading Plans ───────────────────────────

    # 1-Year Bible Reading Plan
    cursor.execute(
        "INSERT INTO reading_plans (name, description, durationDays, isPrebuilt) "
        "VALUES (?, ?, ?, 1)",
        ("1-Year Bible", "Read the entire Bible in one year", 365),
    )
    plan_id = cursor.lastrowid
    day = 1
    for book_num, name, abbr, chapters, testament in BOOKS:
        for ch in range(1, chapters + 1, 4):
            ch_end = min(ch + 3, chapters)
            readings = json.dumps([{
                "bookId": book_num,
                "chapterStart": ch,
                "chapterEnd": ch_end,
            }])
            cursor.execute(
                "INSERT INTO reading_plan_days (planId, dayNumber, title, readings) "
                "VALUES (?, ?, ?, ?)",
                (plan_id, day, f"{name} {ch}-{ch_end}", readings),
            )
            day += 1
            if day > 365:
                break
        if day > 365:
            break

    # 90-Day New Testament
    cursor.execute(
        "INSERT INTO reading_plans (name, description, durationDays, isPrebuilt) "
        "VALUES (?, ?, ?, 1)",
        ("90-Day New Testament", "Read the New Testament in 90 days", 90),
    )
    nt_plan_id = cursor.lastrowid
    nt_day = 1
    for book_num, name, abbr, chapters, testament in BOOKS:
        if testament != "NEW":
            continue
        for ch in range(1, chapters + 1, 2):
            ch_end = min(ch + 1, chapters)
            readings = json.dumps([{
                "bookId": book_num,
                "chapterStart": ch,
                "chapterEnd": ch_end,
            }])
            cursor.execute(
                "INSERT INTO reading_plan_days (planId, dayNumber, title, readings) "
                "VALUES (?, ?, ?, ?)",
                (nt_plan_id, nt_day, f"{name} {ch}-{ch_end}", readings),
            )
            nt_day += 1
            if nt_day > 90:
                break
        if nt_day > 90:
            break

    # ── Cross-References (Treasury of Scripture Knowledge) ────

    import_cross_references(cursor)

    # ── Finalize ────────────────────────────────────────────────

    conn.commit()

    # Verify
    cursor.execute("SELECT COUNT(*) FROM verses")
    vcount = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM translations")
    tcount = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM books")
    bcount = cursor.fetchone()[0]

    conn.close()

    file_size = os.path.getsize(output_path)
    print(f"\n{'='*60}")
    print(f"Database created: {output_path}")
    print(f"  Translations:  {tcount} ({', '.join(TRANSLATIONS.keys())})")
    print(f"  Books:         {bcount}")
    print(f"  Total verses:  {vcount:,}")
    print(f"  File size:     {file_size / 1024 / 1024:.1f} MB")
    print(f"{'='*60}")


def import_cross_references(cursor):
    """Download and import Treasury of Scripture Knowledge cross-references."""

    # Build abbreviation → bookId mapping
    abbr_to_id = {book[2]: book[0] for book in BOOKS}

    print("\nImporting cross-references:")
    print(f"  Downloading: {CROSS_REF_URL}")
    try:
        req = urllib.request.Request(
            CROSS_REF_URL,
            headers={"User-Agent": "OpenBible-Import/1.0"},
        )
        with urllib.request.urlopen(req, timeout=120) as f:
            zip_data = f.read()
        print(f"  Downloaded {len(zip_data):,} bytes")
    except Exception as e:
        print(f"  ERROR downloading cross-references: {e}", file=sys.stderr)
        print("  Cross-references will NOT be available in the database.")
        return

    inserted = 0
    errors = 0
    batch = []

    # Pre-build a cache: (bookId, chapter, verse) → verseId for KJV
    # This avoids N lookups per row
    cursor.execute(
        "SELECT id, bookId, chapter, verse FROM verses WHERE translationId = 'kjv'"
    )
    verse_map = {}  # (bookId, chapter, verse) → verseId
    for vid, bid, ch, vs in cursor.fetchall():
        verse_map[(bid, ch, vs)] = vid
    print(f"  Built KJV verse lookup cache: {len(verse_map):,} verses")

    with zipfile.ZipFile(io.BytesIO(zip_data)) as z:
        with z.open("cross_references.txt") as f:
            for line_bytes in f:
                line = line_bytes.decode("utf-8").strip()
                if not line or line.startswith("From Verse"):
                    continue

                parts = line.split("\t")
                if len(parts) < 3:
                    errors += 1
                    continue

                from_str, to_str, votes_str = parts[0], parts[1], parts[2]

                # Parse "Gen.1.1" → (bookId, chapter, verse)
                from_ref = _parse_ref(from_str, abbr_to_id)
                if from_ref is None:
                    errors += 1
                    continue
                from_book_id, from_ch, from_vs = from_ref

                # Look up fromVerseId in KJV
                from_verse_id = verse_map.get((from_book_id, from_ch, from_vs))
                if from_verse_id is None:
                    errors += 1
                    continue

                # Parse to reference — may be range "Ps.148.4-Ps.148.5"
                # or single "Ps.90.2"
                to_refs = re.split(r"-", to_str)
                first_ref = _parse_ref(to_refs[0], abbr_to_id)
                if first_ref is None:
                    errors += 1
                    continue
                to_book_id, to_ch, to_vs_start = first_ref

                to_vs_end = None
                if len(to_refs) > 1:
                    last_ref = _parse_ref(to_refs[1], abbr_to_id)
                    if last_ref is not None:
                        _, _, to_vs_end = last_ref
                    else:
                        to_vs_end = to_vs_start
                else:
                    to_vs_end = to_vs_start

                try:
                    votes = int(votes_str)
                except ValueError:
                    votes = 0

                batch.append((
                    from_verse_id, to_book_id, to_ch,
                    to_vs_start, to_vs_end, votes,
                ))
                inserted += 1

                # Flush every 1000 rows
                if len(batch) >= 1000:
                    cursor.executemany(
                        "INSERT INTO cross_references "
                        "(fromVerseId, toBookId, toChapter, toVerseStart, toVerseEnd, relevance) "
                        "VALUES (?, ?, ?, ?, ?, ?)",
                        batch,
                    )
                    batch = []

    # Flush remaining
    if batch:
        cursor.executemany(
            "INSERT INTO cross_references "
            "(fromVerseId, toBookId, toChapter, toVerseStart, toVerseEnd, relevance) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            batch,
        )

    print(f"  Inserted {inserted:,} cross-references")
    if errors:
        print(f"  WARNING: {errors} rows could not be parsed")


def _parse_ref(ref: str, abbr_to_id: dict) -> Optional[Tuple[int, int, int]]:
    """Parse 'Gen.1.1' or 'Ps.148.4' → (bookId, chapter, verse)."""
    ref = ref.strip()
    parts = ref.split(".")
    if len(parts) != 3:
        return None
    abbr, chapter_str, verse_str = parts
    book_id = abbr_to_id.get(abbr)
    if book_id is None:
        return None
    try:
        chapter = int(chapter_str)
        verse = int(verse_str)
    except ValueError:
        return None
    return (book_id, chapter, verse)


if __name__ == "__main__":
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "openbible.db")
    print("OpenBible Data Import Pipeline")
    print("=" * 60)
    start = time.time()
    create_database(output_path)
    elapsed = time.time() - start
    print(f"Completed in {elapsed:.1f}s")
