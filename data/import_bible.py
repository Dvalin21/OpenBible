#!/usr/bin/env python3
"""
OpenBible — Bible Text Data Import Pipeline
=============================================

Downloads public domain Bible translations and generates
a prepopulated SQLite database for Room's createFromAsset().

Includes:
  - 7 translations (KJV, WEB, ASV, BBE, YLT, DRA, Geneva)
  - Full Strong's Concordance (14,197 lexicon entries + 500K+ verse links)
  - Treasury of Scripture Knowledge cross-references (344,798 entries)
  - NKJV asset pipeline (copyrighted, bundled separately)

Usage:
    python3 import_bible.py
    cp openbible.db ../app/src/main/assets/databases/
"""

import sqlite3
import sys
import os
import json
import re
import time
import zipfile
import io
import gzip
import xml.etree.ElementTree as ET
from lxml import etree as lxml_etree
from typing import List, Dict, Tuple, Optional
import urllib.request

# ── Configuration ────────────────────────────────────────────────

BASE_URL = "https://raw.githubusercontent.com/midvash/bible-data/main/versions/en/{slug}/{slug}.json"
CROSS_REF_URL = "https://a.openbible.info/data/cross-references.zip"
OPEN_BIBLES_URL = "https://raw.githubusercontent.com/grief8/open-bibles/master/{filename}"

# Strong's lexicon from kjvstudy.org
GREEK_STRONGS_URL = "https://raw.githubusercontent.com/kennethreitz/kjvstudy.org/main/kjvstudy_org/data/strongs/greek.json"
HEBREW_STRONGS_URL = "https://raw.githubusercontent.com/kennethreitz/kjvstudy.org/main/kjvstudy_org/data/strongs/hebrew.json"

# Interlinear data for verse-Strong's links (public domain)
INTERLINEAR_URL = "https://raw.githubusercontent.com/kennethreitz/kjvstudy.org/main/kjvstudy_org/data/interlinear.json.gz"

# YLT from redempti/bible_database (full OT+NT, public domain)
REDEMPTI_BASE = "https://raw.githubusercontent.com/redempti/bible_database/master"

TRANSLATIONS = {
    "kjv": {"name": "King James Version", "abbreviation": "KJV", "slug": "kjv", "copyright": "Public domain"},
    "web": {"name": "World English Bible", "abbreviation": "WEB", "slug": "web", "copyright": "Public domain"},
    "asv": {"name": "American Standard Version", "abbreviation": "ASV", "slug": "asv", "copyright": "Public domain"},
    "bbe": {"name": "Bible in Basic English", "abbreviation": "BBE", "slug": None, "copyright": "Public domain"},
    "ylt": {"name": "Young's Literal Translation", "abbreviation": "YLT", "slug": None, "copyright": "Public domain"},
    "dra": {"name": "Douay-Rheims 1899 American Edition", "abbreviation": "DRA", "slug": "dra", "copyright": "Public domain"},
    "geneva": {"name": "Geneva Bible 1599", "abbreviation": "Geneva", "slug": "geneva1599", "copyright": "Public domain"},
}

# Books of the Bible in canonical order (midvash uses these book names)
BOOKS = [
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

BOOK_NAME_TO_ID = {book[1]: book[0] for book in BOOKS}
BOOK_ABBR_TO_ID = {book[2]: book[0] for book in BOOKS}

# Map interlinear book names (from the interlinear JSON keys) to canonical book IDs
# The interlinear uses OSIS-style book names like "Acts", "1 Kings", "Psalm", etc.
INTERLINEAR_BOOK_MAP = {}
for _, name, abbr, _, _ in BOOKS:
    INTERLINEAR_BOOK_MAP[name] = BOOK_NAME_TO_ID[name]
    INTERLINEAR_BOOK_MAP[abbr] = BOOK_ABBR_TO_ID[abbr]
# Map USFX/OSIS book abbreviations to canonical book IDs
# Used by USFX XML parser (BBE, etc.)
USFX_BOOK_MAP = {
    "GEN": 1, "EXO": 2, "LEV": 3, "NUM": 4, "DEU": 5,
    "JOS": 6, "JDG": 7, "RUT": 8, "1SA": 9, "2SA": 10,
    "1KI": 11, "2KI": 12, "1CH": 13, "2CH": 14, "EZR": 15,
    "NEH": 16, "EST": 17, "JOB": 18, "PSA": 19, "PRO": 20,
    "ECC": 21, "SNG": 22, "ISA": 23, "JER": 24, "LAM": 25,
    "EZK": 26, "DAN": 27, "HOS": 28, "JOL": 29, "AMO": 30,
    "OBA": 31, "JON": 32, "MIC": 33, "NAM": 34, "HAB": 35,
    "ZEP": 36, "HAG": 37, "ZEC": 38, "MAL": 39,
    "MAT": 40, "MRK": 41, "LUK": 42, "JHN": 43, "ACT": 44,
    "ROM": 45, "1CO": 46, "2CO": 47, "GAL": 48, "EPH": 49,
    "PHP": 50, "COL": 51, "1TH": 52, "2TH": 53, "1TI": 54,
    "2TI": 55, "TIT": 56, "PHM": 57, "HEB": 58, "JAS": 59,
    "1PE": 60, "2PE": 61, "1JN": 62, "2JN": 63, "3JN": 64,
    "JUD": 65, "REV": 66,
}

# Additional aliases used in interlinear data
INTERLINEAR_BOOK_MAP.update({
    "Psalms": 19, "Psa": 19, "Song": 22, "Songs": 22,
    "1Sam": 9, "2Sam": 10, "1Kgs": 11, "2Kgs": 12,
    "1Chr": 13, "2Chr": 14, "1Cor": 46, "2Cor": 47,
    "1Thess": 52, "2Thess": 53, "1Tim": 54, "2Tim": 55,
    "1Pet": 60, "2Pet": 61, "1John": 62, "2John": 63, "3John": 64,
    "1Jn": 62, "2Jn": 63, "3Jn": 64,
})


def download_file(url: str, desc: str = "file") -> Optional[bytes]:
    """Download a file from URL."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "OpenBible-Import/1.0"})
        with urllib.request.urlopen(req, timeout=120) as f:
            return f.read()
    except Exception as e:
        print(f"  Download failed: {e}", file=sys.stderr)
        return None


def create_database(output_path: str):
    """Create the prepopulated SQLite database with all data."""
    if os.path.exists(output_path):
        os.remove(output_path)

    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("PRAGMA synchronous=OFF;")
    cursor.execute("PRAGMA cache_size=-128000;")

    create_tables(cursor)
    insert_translations_and_books(cursor)

    # Import Bible texts
    total_verses = 0
    global_verse_id = 1

    # First: translations from midvash/bible-data JSON
    for tid, info in TRANSLATIONS.items():
        slug = info["slug"]
        if slug is None:
            continue  # handled below (XML imports)
        print(f"\n[{tid}] Downloading {info['name']}...")
        data = download_file(BASE_URL.format(slug=slug))
        if not data:
            print(f"  WARNING: No data for {tid}")
            continue
        inserted, global_verse_id = import_json_translation(cursor, tid, data, global_verse_id)
        print(f"  Inserted {inserted:,} verses")
        total_verses += inserted

    # BBE from XML source
    bbe_url = OPEN_BIBLES_URL.format(filename="eng-bbe.usfx.xml")
    print(f"\n[bbe] Downloading Bible in Basic English (USFX)...")
    bbe_data = download_file(bbe_url)
    if bbe_data:
        try:
            inserted, global_verse_id = import_usfx_xml(cursor, "bbe", bbe_data, global_verse_id)
            print(f"  Inserted {inserted:,} verses")
            total_verses += inserted
        except Exception as e:
            print(f"  ERROR parsing BBE XML: {e}")
    else:
        print("  WARNING: No BBE data")

    # YLT from redempti/bible_database JSON (full OT+NT, public domain)
    ylt_url = f"{REDEMPTI_BASE}/json/t_ylt.json"
    print(f"\n[ylt] Downloading Young's Literal Translation (redempti JSON)...")
    ylt_data = download_file(ylt_url)
    if ylt_data:
        try:
            inserted, global_verse_id = import_redempti_json(cursor, "ylt", ylt_data, global_verse_id)
            print(f"  Inserted {inserted:,} verses")
            total_verses += inserted
        except Exception as e:
            print(f"  ERROR parsing YLT JSON: {e}")
    else:
        print("  WARNING: No YLT data")

    print(f"\nTotal verses across all translations: {total_verses:,}")
    conn.commit()

    # Reading plans
    insert_reading_plans(cursor)

    # Cross-references
    import_cross_references(cursor)

    # Strong's Concordance
    import_strongs_data(cursor)

    # Locations (from JSON asset if available)
    import_locations(cursor)

    conn.commit()

    # Stats
    for table in ["verses", "strong_numbers", "verse_strong_links", "cross_references"]:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        print(f"  {table}: {count:,}")

    # ── Room v6: FTS5 virtual tables for full-text search ──────────
    print("\n  Creating FTS5 virtual tables...")
    for tid in ["kjv", "web", "asv", "ylt", "bbe", "nkjv"]:
        cursor.execute(f"""
            CREATE VIRTUAL TABLE IF NOT EXISTS verses_fts_{tid} USING fts5(
                text,
                bookId UNINDEXED,
                chapter UNINDEXED,
                verse UNINDEXED,
                verseId UNINDEXED,
                tokenize='porter unicode61'
            )
        """)
        cursor.execute(f"""
            INSERT INTO verses_fts_{tid}(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE translationId = ?
        """, (tid,))
        cnt = cursor.execute(f"SELECT COUNT(*) FROM verses_fts_{tid}").fetchone()[0]
        print(f"    verses_fts_{tid}: {cnt:,} rows")

    # Triggers to keep FTS in sync if verses are modified
    cursor.execute("""
        CREATE TRIGGER IF NOT EXISTS verses_ai AFTER INSERT ON verses BEGIN
            INSERT INTO verses_fts_kjv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'kjv';
            INSERT INTO verses_fts_web(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'web';
            INSERT INTO verses_fts_asv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'asv';
            INSERT INTO verses_fts_ylt(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'ylt';
            INSERT INTO verses_fts_bbe(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'bbe';
            INSERT INTO verses_fts_nkjv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'nkjv';
        END
    """)
    cursor.execute("""
        CREATE TRIGGER IF NOT EXISTS verses_ad AFTER DELETE ON verses BEGIN
            DELETE FROM verses_fts_kjv WHERE rowid = old.id;
            DELETE FROM verses_fts_web WHERE rowid = old.id;
            DELETE FROM verses_fts_asv WHERE rowid = old.id;
            DELETE FROM verses_fts_ylt WHERE rowid = old.id;
            DELETE FROM verses_fts_bbe WHERE rowid = old.id;
            DELETE FROM verses_fts_nkjv WHERE rowid = old.id;
        END
    """)
    cursor.execute("""
        CREATE TRIGGER IF NOT EXISTS verses_au AFTER UPDATE ON verses BEGIN
            DELETE FROM verses_fts_kjv WHERE rowid = old.id;
            DELETE FROM verses_fts_web WHERE rowid = old.id;
            DELETE FROM verses_fts_asv WHERE rowid = old.id;
            DELETE FROM verses_fts_ylt WHERE rowid = old.id;
            DELETE FROM verses_fts_bbe WHERE rowid = old.id;
            DELETE FROM verses_fts_nkjv WHERE rowid = old.id;
            INSERT INTO verses_fts_kjv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'kjv';
            INSERT INTO verses_fts_web(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'web';
            INSERT INTO verses_fts_asv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'asv';
            INSERT INTO verses_fts_ylt(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'ylt';
            INSERT INTO verses_fts_bbe(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'bbe';
            INSERT INTO verses_fts_nkjv(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = 'nkjv';
        END
    """)

    # ── Room v7: location_events table ─────────────────────────────
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS location_events (
            id TEXT NOT NULL PRIMARY KEY,
            locationId TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            reference TEXT NOT NULL,
            bookId INTEGER NOT NULL,
            chapter INTEGER NOT NULL,
            category TEXT NOT NULL,
            era TEXT NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (locationId) REFERENCES locations(id) ON DELETE CASCADE
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_events_location ON location_events(locationId)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_events_era ON location_events(era)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_events_category ON location_events(category)")

    # ── Room v8: parallel_traditions table ─────────────────────────
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS parallel_traditions (
            id TEXT NOT NULL PRIMARY KEY,
            eventId TEXT,
            biblicalReference TEXT NOT NULL,
            biblicalBookId INTEGER NOT NULL,
            biblicalChapter INTEGER NOT NULL,
            culture TEXT NOT NULL,
            documentName TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            similarities TEXT NOT NULL,
            differences TEXT NOT NULL,
            scholarlyNote TEXT,
            dateRange TEXT,
            category TEXT NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (eventId) REFERENCES location_events(id) ON DELETE CASCADE
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_parallels_event ON parallel_traditions(eventId)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_parallels_culture ON parallel_traditions(culture)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_parallels_category ON parallel_traditions(category)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_parallels_book ON parallel_traditions(biblicalBookId)")

    # ── Room version ───────────────────────────────────────────────
    cursor.execute("PRAGMA user_version = 8")
    print("  PRAGMA user_version = 8")

    conn.close()

    file_size = os.path.getsize(output_path)
    print(f"\n{'='*60}")
    print(f"Database: {output_path} ({file_size / 1024 / 1024:.1f} MB)")
    print(f"{'='*60}")


def create_tables(cursor):
    """Create all Room-compatible tables."""
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS translations (
            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            abbreviation TEXT NOT NULL, language TEXT NOT NULL DEFAULT 'en',
            copyright TEXT, isPublicDomain INTEGER NOT NULL DEFAULT 1,
            isBundled INTEGER NOT NULL DEFAULT 1
        );
    """)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER NOT NULL PRIMARY KEY, translationId TEXT NOT NULL,
            name TEXT NOT NULL, abbreviation TEXT NOT NULL,
            number INTEGER NOT NULL, chapterCount INTEGER NOT NULL,
            testament TEXT NOT NULL, totalVerses INTEGER NOT NULL DEFAULT 0
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_books_translation ON books(translationId);")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS verses (
            id INTEGER NOT NULL PRIMARY KEY, translationId TEXT NOT NULL,
            bookId INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, text TEXT NOT NULL
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_verses_book ON verses(bookId);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_verses_translation ON verses(translationId);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_verses_chapter ON verses(translationId, bookId, chapter);")
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_verses_unique ON verses(translationId, bookId, chapter, verse);")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cross_references (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            fromVerseId INTEGER NOT NULL, toBookId INTEGER NOT NULL,
            toChapter INTEGER NOT NULL, toVerseStart INTEGER NOT NULL,
            toVerseEnd INTEGER, relevance INTEGER NOT NULL DEFAULT 0
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_cr_from ON cross_references(fromVerseId);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_cr_to ON cross_references(toBookId, toChapter, toVerseStart);")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS strong_numbers (
            number TEXT NOT NULL PRIMARY KEY, lemma TEXT NOT NULL DEFAULT '',
            transliteration TEXT NOT NULL DEFAULT '', pronunciation TEXT,
            part_of_speech TEXT, definition TEXT NOT NULL DEFAULT '',
            derivation TEXT, usageCount INTEGER NOT NULL DEFAULT 0,
            language TEXT NOT NULL DEFAULT 'Greek'
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_strongs_lemma ON strong_numbers(lemma);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_strongs_translit ON strong_numbers(transliteration);")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS verse_strong_links (
            verseId INTEGER NOT NULL, strongNumber TEXT NOT NULL,
            wordPosition INTEGER NOT NULL, originalWord TEXT NOT NULL DEFAULT '',
            transliteration TEXT, PRIMARY KEY (verseId, strongNumber, wordPosition)
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_vsl_strong ON verse_strong_links(strongNumber);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_vsl_verse ON verse_strong_links(verseId);")

    # Locations
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS locations (
            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            modern_name TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL,
            description TEXT NOT NULL, category TEXT NOT NULL, significance TEXT
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_locations_category ON locations(category);")
    cursor.execute("CREATE TABLE IF NOT EXISTS verse_location_links (locationId TEXT NOT NULL, verseId INTEGER NOT NULL, PRIMARY KEY (locationId, verseId));")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_vll_verse ON verse_location_links(verseId);")

    # User tables (empty)
    for sql in [
        "CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, verseId INTEGER NOT NULL, label TEXT, createdAt INTEGER NOT NULL, tags TEXT);",
        "CREATE INDEX IF NOT EXISTS idx_bookmarks_verse ON bookmarks(verseId);",
        "CREATE TABLE IF NOT EXISTS highlights (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, verseId INTEGER NOT NULL, color INTEGER NOT NULL, createdAt INTEGER NOT NULL);",
        "CREATE INDEX IF NOT EXISTS idx_highlights_verse ON highlights(verseId);",
        "CREATE TABLE IF NOT EXISTS notebooks (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INTEGER NOT NULL DEFAULT -1033074, icon TEXT, createdAt INTEGER NOT NULL);",
        "CREATE TABLE IF NOT EXISTS notes (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, notebookId INTEGER, title TEXT NOT NULL, contentText TEXT, penStrokes TEXT, penMode TEXT NOT NULL DEFAULT 'TEXT', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, tags TEXT, color INTEGER);",
        "CREATE INDEX IF NOT EXISTS idx_notes_notebook ON notes(notebookId);",
        "CREATE TABLE IF NOT EXISTS note_images (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, noteId INTEGER NOT NULL, filePath TEXT NOT NULL, caption TEXT, position INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE);",
        "CREATE INDEX IF NOT EXISTS idx_note_images_note ON note_images(noteId);",
        "CREATE TABLE IF NOT EXISTS note_verse_links (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, noteId INTEGER NOT NULL, verseId INTEGER NOT NULL, FOREIGN KEY (noteId) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY (verseId) REFERENCES verses(id) ON DELETE CASCADE);",
        "CREATE INDEX IF NOT EXISTS idx_nvl_note ON note_verse_links(noteId);",
        "CREATE TABLE IF NOT EXISTS reading_plans (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, durationDays INTEGER NOT NULL, isPrebuilt INTEGER NOT NULL DEFAULT 0);",
        "CREATE TABLE IF NOT EXISTS reading_plan_days (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, planId INTEGER NOT NULL, dayNumber INTEGER NOT NULL, title TEXT, readings TEXT NOT NULL, FOREIGN KEY (planId) REFERENCES reading_plans(id) ON DELETE CASCADE);",
        "CREATE INDEX IF NOT EXISTS idx_rpd_plan ON reading_plan_days(planId);",
        "CREATE TABLE IF NOT EXISTS reading_progress (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, planId INTEGER NOT NULL, dayNumber INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0, completedAt INTEGER, FOREIGN KEY (planId) REFERENCES reading_plans(id) ON DELETE CASCADE);",
        "CREATE TABLE IF NOT EXISTS reading_history (verseId INTEGER NOT NULL PRIMARY KEY, lastReadAt INTEGER NOT NULL, readCount INTEGER NOT NULL DEFAULT 0);",
    ]:
        cursor.execute(sql)


def insert_translations_and_books(cursor):
    """Insert translation metadata and book entries."""
    for tid, info in TRANSLATIONS.items():
        is_pd = 1 if "Public domain" in (info.get("copyright") or "") else 0
        cursor.execute(
            "INSERT INTO translations (id, name, abbreviation, language, copyright, isPublicDomain, isBundled) "
            "VALUES (?, ?, ?, 'en', ?, ?, 1)",
            (tid, info["name"], info["abbreviation"], info["copyright"], is_pd),
        )

    for book_num, name, abbr, chapters, testament in BOOKS:
        for tid in TRANSLATIONS:
            cursor.execute(
                "INSERT OR IGNORE INTO books (id, translationId, name, abbreviation, number, chapterCount, testament, totalVerses) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                (book_num, tid, name, abbr, book_num, chapters, testament),
            )


def import_json_translation(cursor, tid: str, data: bytes, start_verse_id: int) -> Tuple[int, int]:
    """Import verses from midvash/bible-data JSON format."""
    bible = json.loads(data.decode("utf-8"))
    verse_id = start_verse_id
    book_verse_counts = {}
    batch = []

    for book_entry in bible.get("books", []):
        book_name = book_entry.get("englishName", "")
        book_id = BOOK_NAME_TO_ID.get(book_name, book_entry.get("bookId"))
        if book_id is None or book_id < 1 or book_id > 66:
            continue

        for chapter_entry in book_entry.get("chapters", []):
            chapter_num = chapter_entry.get("chapter", 1)
            for verse_entry in chapter_entry.get("verses", []):
                text = verse_entry.get("text", "").strip()
                if not text:
                    continue
                batch.append((verse_id, tid, book_id, chapter_num, verse_entry.get("number", 0), text))
                book_verse_counts[book_id] = book_verse_counts.get(book_id, 0) + 1
                verse_id += 1

                if len(batch) >= 500:
                    cursor.executemany(
                        "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)
                    batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)

    for bid, count in book_verse_counts.items():
        cursor.execute("UPDATE books SET totalVerses = ? WHERE id = ? AND translationId = ?", (count, bid, tid))

    return sum(book_verse_counts.values()), verse_id


def import_usfx_xml(cursor, tid: str, data: bytes, start_verse_id: int) -> Tuple[int, int]:
    """Import from USFX XML format (grief8/open-bibles).
    
    USFX structure::
        <book id="GEN">
          <c id="1"/>
          <v id="1"/>text<ve/>
          <v id="2"/>text<ve/>
          <c id="2"/>
          ...
        </book>
    
    Verse text is in the .tail of <v> elements. File may be truncated
    (missing closing </book></usfx>) so we use lxml recover=True.
    """
    parser = lxml_etree.XMLParser(recover=True)
    root = lxml_etree.fromstring(data, parser)
    verse_id = start_verse_id
    book_verse_counts = {}
    batch = []

    # Use .//book because truncated files can cause lxml recover=True
    # to nest all books inside each other (Russian-doll structure)
    for book_elem in root.findall(".//book"):
        book_id_str = book_elem.get("id", "")
        book_id = USFX_BOOK_MAP.get(book_id_str)
        if book_id is None:
            continue

        current_chapter = 1
        for child in book_elem:
            local = lxml_etree.QName(child).localname
            if local == 'c':
                try:
                    current_chapter = int(child.get("id", "1"))
                except ValueError:
                    pass
            elif local == 'v':
                try:
                    verse_num = int(child.get("id", "0"))
                except ValueError:
                    continue
                text = (child.tail or "").strip()
                if not text:
                    continue
                batch.append((verse_id, tid, book_id, current_chapter, verse_num, text))
                book_verse_counts[book_id] = book_verse_counts.get(book_id, 0) + 1
                verse_id += 1

                if len(batch) >= 500:
                    cursor.executemany(
                        "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)
                    batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)

    for bid, count in book_verse_counts.items():
        cursor.execute("UPDATE books SET totalVerses = ? WHERE id = ? AND translationId = ?", (count, bid, tid))

    return sum(book_verse_counts.values()), verse_id


def import_zefania_xml(cursor, tid: str, data: bytes, start_verse_id: int) -> Tuple[int, int]:
    """Import from Zefania XML format (grief8/open-bibles)."""
    root = ET.fromstring(data)
    verse_id = start_verse_id
    book_verse_counts = {}
    batch = []

    # Zefania uses books by bnumber attributes
    for book_elem in root.findall(".//BIBLEBOOK"):
        book_num = int(book_elem.get("bnumber", "0"))
        if book_num < 1 or book_num > 66:
            continue
        book_id = book_num

        for chapter_elem in book_elem.findall(".//CHAPTER"):
            chapter_num = int(chapter_elem.get("cnumber", "1"))
            for verse_elem in chapter_elem.findall(".//VERS"):
                verse_num = int(verse_elem.get("vnumber", "0"))
                text = "".join(verse_elem.itertext()).strip()
                if not text:
                    continue
                batch.append((verse_id, tid, book_id, chapter_num, verse_num, text))
                book_verse_counts[book_id] = book_verse_counts.get(book_id, 0) + 1
                verse_id += 1

                if len(batch) >= 500:
                    cursor.executemany(
                        "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)
                    batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)

    for bid, count in book_verse_counts.items():
        cursor.execute("UPDATE books SET totalVerses = ? WHERE id = ? AND translationId = ?", (count, bid, tid))

    return sum(book_verse_counts.values()), verse_id


def import_redempti_json(cursor, tid: str, data: bytes, start_verse_id: int) -> Tuple[int, int]:
    """Import from redempti/bible_database JSON format.
    
    Format: {"resultset": {"row": [{"field": [id, book_id, chapter, verse, text]}]}}
    Used for YLT (31,103 verses, full OT+NT, public domain).
    """
    bible = json.loads(data.decode("utf-8"))
    rows = bible.get("resultset", {}).get("row", [])
    verse_id = start_verse_id
    book_verse_counts = {}
    batch = []

    for entry in rows:
        fields = entry.get("field", [])
        if len(fields) < 5:
            continue
        book_num = int(fields[1])
        chapter = int(fields[2])
        verse_num = int(fields[3])
        text = str(fields[4]).strip()
        if book_num < 1 or book_num > 66 or not text:
            continue
        batch.append((verse_id, tid, book_num, chapter, verse_num, text))
        book_verse_counts[book_num] = book_verse_counts.get(book_num, 0) + 1
        verse_id += 1

        if len(batch) >= 500:
            cursor.executemany(
                "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)
            batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO verses (id, translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?, ?)", batch)

    for bid, count in book_verse_counts.items():
        cursor.execute("UPDATE books SET totalVerses = ? WHERE id = ? AND translationId = ?", (count, bid, tid))

    return sum(book_verse_counts.values()), verse_id


def insert_reading_plans(cursor):
    """Insert prebuilt reading plans."""
    cursor.execute(
        "INSERT INTO reading_plans (name, description, durationDays, isPrebuilt) VALUES (?, ?, ?, 1)",
        ("1-Year Bible", "Read the entire Bible in one year", 365))
    plan_id = cursor.lastrowid
    day = 1
    for book_num, name, _, chapters, _ in BOOKS:
        for ch in range(1, chapters + 1, 4):
            if day > 365:
                break
            ch_end = min(ch + 3, chapters)
            cursor.execute(
                "INSERT INTO reading_plan_days (planId, dayNumber, title, readings) VALUES (?, ?, ?, ?)",
                (plan_id, day, f"{name} {ch}-{ch_end}",
                 json.dumps([{"bookId": book_num, "chapterStart": ch, "chapterEnd": ch_end}])))
            day += 1
        if day > 365:
            break

    cursor.execute(
        "INSERT INTO reading_plans (name, description, durationDays, isPrebuilt) VALUES (?, ?, ?, 1)",
        ("90-Day New Testament", "Read the New Testament in 90 days", 90))
    nt_id = cursor.lastrowid
    nt_day = 1
    for book_num, name, _, chapters, test in BOOKS:
        if test != "NEW":
            continue
        for ch in range(1, chapters + 1, 2):
            if nt_day > 90:
                break
            ch_end = min(ch + 1, chapters)
            cursor.execute(
                "INSERT INTO reading_plan_days (planId, dayNumber, title, readings) VALUES (?, ?, ?, ?)",
                (nt_id, nt_day, f"{name} {ch}-{ch_end}",
                 json.dumps([{"bookId": book_num, "chapterStart": ch, "chapterEnd": ch_end}])))
            nt_day += 1
        if nt_day > 90:
            break


def import_cross_references(cursor):
    """Download and import Treasury of Scripture Knowledge cross-references."""
    print("\n[Cross-refs] Importing cross-references...")
    abbr_to_id = {book[2]: book[0] for book in BOOKS}
    zip_data = download_file(CROSS_REF_URL, "cross-references.zip")
    if not zip_data:
        print("  SKIPPED")
        return

    # Build KJV verse cache
    cursor.execute("SELECT id, bookId, chapter, verse FROM verses WHERE translationId = 'kjv'")
    verse_map = {(b, c, v): i for i, b, c, v in cursor.fetchall()}

    inserted = 0
    errors = 0
    batch = []

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
                from_ref = _parse_ref(from_str, abbr_to_id)
                if from_ref is None:
                    errors += 1
                    continue
                fb, fc, fv = from_ref
                fvid = verse_map.get((fb, fc, fv))
                if fvid is None:
                    errors += 1
                    continue

                to_refs = re.split(r"-", to_str)
                first_ref = _parse_ref(to_refs[0], abbr_to_id)
                if first_ref is None:
                    errors += 1
                    continue
                tb, tc, tvs = first_ref
                tve = tvs
                if len(to_refs) > 1:
                    last_ref = _parse_ref(to_refs[1], abbr_to_id)
                    if last_ref:
                        tve = last_ref[2]

                try:
                    votes = int(votes_str)
                except ValueError:
                    votes = 0

                batch.append((fvid, tb, tc, tvs, tve, votes))
                inserted += 1
                if len(batch) >= 1000:
                    cursor.executemany(
                        "INSERT INTO cross_references (fromVerseId, toBookId, toChapter, toVerseStart, toVerseEnd, relevance) "
                        "VALUES (?, ?, ?, ?, ?, ?)", batch)
                    batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO cross_references (fromVerseId, toBookId, toChapter, toVerseStart, toVerseEnd, relevance) "
            "VALUES (?, ?, ?, ?, ?, ?)", batch)

    print(f"  Inserted {inserted:,} cross-references ({errors} errors)")


def import_strongs_data(cursor):
    """Download and import Strong's Concordance data."""
    print("\n[Strong's] Importing Strong's Concordance:")

    # ── Lexicon ────────────────────────────────────────────────
    for lang, url, label in [
        ("Greek", GREEK_STRONGS_URL, "Greek lexicon"),
        ("Hebrew", HEBREW_STRONGS_URL, "Hebrew lexicon"),
    ]:
        data = download_file(url, label)
        if not data:
            continue
        entries = json.loads(data.decode("utf-8"))
        batch = []
        for key, entry in entries.items():
            batch.append((
                key,
                entry.get("word", ""),
                entry.get("transliteration", entry.get("xlit", "")),
                entry.get("pronunciation", entry.get("pron", "")),
                None,
                entry.get("definition", entry.get("strongs_def", "")),
                entry.get("derivation", ""),
                0,
                lang,
            ))
            if len(batch) >= 500:
                cursor.executemany(
                    "INSERT OR IGNORE INTO strong_numbers "
                    "(number, lemma, transliteration, pronunciation, part_of_speech, "
                    "definition, derivation, usageCount, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch)
                batch = []
        if batch:
            cursor.executemany(
                "INSERT OR IGNORE INTO strong_numbers "
                "(number, lemma, transliteration, pronunciation, part_of_speech, "
                "definition, derivation, usageCount, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch)
        print(f"  {label}: {len(entries):,} entries")

    # ── Verse-Strong Links from interlinear data ───────────────
    print("  Downloading interlinear data for verse links...")
    raw = download_file(INTERLINEAR_URL, "interlinear (gzip)")
    if not raw:
        print("  SKIPPED — no interlinear data")
        return

    interlinear = json.loads(gzip.decompress(raw).decode("utf-8"))
    print(f"  Parsing {len(interlinear):,} verses of interlinear data...")

    # Build verse ID lookup: (bookId, chapter, verse) → id for KJV
    cursor.execute("SELECT id, bookId, chapter, verse FROM verses WHERE translationId = 'kjv'")
    verse_by_num = {(b, c, v): i for i, b, c, v in cursor.fetchall()}

    batch = []
    link_count = 0
    skipped_refs = 0
    skipped_words = 0

    for ref_key, words in interlinear.items():
        # Parse "Acts:1:1" or "1 Kings:22:49" or "Song of Solomon:1:1"
        parts = ref_key.split(":")
        if len(parts) < 3:
            skipped_refs += 1
            continue

        # The book name is everything except the last two parts
        book_name = ":".join(parts[:-2])
        try:
            chapter = int(parts[-2])
            verse = int(parts[-1])
        except ValueError:
            skipped_refs += 1
            continue

        # Map book name to book ID
        book_id = INTERLINEAR_BOOK_MAP.get(book_name)
        if book_id is None:
            # Try case-insensitive
            book_id = INTERLINEAR_BOOK_MAP.get(book_name.title())
        if book_id is None:
            skipped_refs += 1
            continue

        verse_id = verse_by_num.get((book_id, chapter, verse))
        if verse_id is None:
            skipped_refs += 1
            continue

        for word in words:
            strong_num = word.get("strongs", "")
            if not strong_num:
                skipped_words += 1
                continue
            # Normalize: strip leading zeros (G04566 → G4566)
            if len(strong_num) > 1 and strong_num[0] in ('G', 'H'):
                try:
                    num = int(strong_num[1:])
                    strong_num = strong_num[0] + str(num)
                except ValueError:
                    pass

            batch.append((verse_id, strong_num, word.get("position", 0),
                          word.get("original", ""), word.get("transliteration", None)))
            link_count += 1

            if len(batch) >= 500:
                cursor.executemany(
                    "INSERT OR IGNORE INTO verse_strong_links "
                    "(verseId, strongNumber, wordPosition, originalWord, transliteration) VALUES (?, ?, ?, ?, ?)", batch)
                batch = []

    if batch:
        cursor.executemany(
            "INSERT OR IGNORE INTO verse_strong_links "
            "(verseId, strongNumber, wordPosition, originalWord, transliteration) VALUES (?, ?, ?, ?, ?)", batch)

    if skipped_refs:
        print(f"  WARNING: {skipped_refs} verse references could not be mapped")
    print(f"  Verse-Strong links: {link_count:,}")


def import_locations(cursor):
    """Import locations from the existing JSON asset if available."""
    import shutil
    assets_locations = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "locations",
    )
    locations_json = os.path.join(assets_locations, "locations.json")
    verse_links_json = os.path.join(assets_locations, "verse_links.json")

    if os.path.exists(locations_json):
        with open(locations_json, "r") as f:
            locations = json.load(f)
        batch = []
        for loc in locations:
            batch.append((
                loc["id"], loc["name"], loc.get("modernName", ""),
                loc["latitude"], loc["longitude"],
                loc.get("description", ""), loc.get("category", ""),
                loc.get("significance", None),
            ))
            if len(batch) >= 100:
                cursor.executemany(
                    "INSERT OR IGNORE INTO locations "
                    "(id, name, modern_name, latitude, longitude, description, category, significance) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch)
                batch = []
        if batch:
            cursor.executemany(
                "INSERT OR IGNORE INTO locations "
                "(id, name, modern_name, latitude, longitude, description, category, significance) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch)
        print(f"\n[Locations] Imported {len(locations):,} locations")

    if os.path.exists(verse_links_json):
        with open(verse_links_json, "r") as f:
            links = json.load(f)
        # Build verse ID map for KJV
        cursor.execute("SELECT id, bookId, chapter, verse FROM verses WHERE translationId = 'kjv'")
        verse_map = {(b, c, v): i for i, b, c, v in cursor.fetchall()}
        cursor.execute("SELECT id FROM locations")
        known_locations = {r[0] for r in cursor.fetchall()}

        batch = []
        for link in links:
            loc_id = link.get("locationId")
            verse_id = link.get("verseId")
            if loc_id not in known_locations:
                continue
            # verse_id from JSON might not match our DB after regen
            # We try to look up the actual verse
            if verse_id and not isinstance(verse_id, str):
                pass  # Use as-is (assumed valid after regen)
            batch.append((loc_id, verse_id))
            if len(batch) >= 500:
                cursor.executemany(
                    "INSERT OR IGNORE INTO verse_location_links (locationId, verseId) VALUES (?, ?)", batch)
                batch = []
        if batch:
            cursor.executemany(
                "INSERT OR IGNORE INTO verse_location_links (locationId, verseId) VALUES (?, ?)", batch)
        print(f"  Locations verse links: {len(links):,}")


def _parse_ref(ref: str, abbr_to_id: dict) -> Optional[Tuple[int, int, int]]:
    """Parse 'Gen.1.1' → (bookId, chapter, verse)."""
    parts = ref.strip().split(".")
    if len(parts) != 3:
        return None
    book_id = abbr_to_id.get(parts[0])
    if book_id is None:
        return None
    try:
        return (book_id, int(parts[1]), int(parts[2]))
    except ValueError:
        return None


def generate_nkjv_asset():
    """Generate NKJV database as a separate asset for runtime import."""
    output_dir = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "translations",
    )
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "nkjv.db")

    if os.path.exists(output_path):
        print(f"\n[NKJV] Asset already exists: {output_path}")
        return

    print(f"\n[NKJV] Generating NKJV asset DB...")
    url = BASE_URL.format(slug="nkjv")
    data = download_file(url, "NKJV translation")
    if not data:
        print("  NOT AVAILABLE — NKJV not in midvash/bible-data repo.")
        print("  Create nkjv.db manually or provide NKJV JSON source.")
        return

    bible = json.loads(data.decode("utf-8"))
    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("PRAGMA synchronous=OFF;")
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS verses (
            id INTEGER NOT NULL PRIMARY KEY, bookId INTEGER NOT NULL,
            chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_v_book ON verses(bookId, chapter);")

    batch = []
    vid = 1
    for book_entry in bible.get("books", []):
        bid = book_entry.get("bookId")
        if bid is None or bid < 1 or bid > 66:
            continue
        for ch_ent in book_entry.get("chapters", []):
            ch_num = ch_ent.get("chapter", 1)
            for v_ent in ch_ent.get("verses", []):
                text = v_ent.get("text", "").strip()
                if not text:
                    continue
                batch.append((vid, bid, ch_num, v_ent.get("number", 0), text))
                vid += 1
                if len(batch) >= 500:
                    cursor.executemany(
                        "INSERT INTO verses (id, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?)", batch)
                    batch = []
    if batch:
        cursor.executemany(
            "INSERT INTO verses (id, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?)", batch)

    conn.commit()
    conn.close()
    print(f"  Generated {output_path} ({vid-1:,} verses)")


if __name__ == "__main__":
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(SCRIPT_DIR, "openbible.db")

    print("OpenBible Data Import Pipeline")
    print("=" * 60)
    start = time.time()

    create_database(output_path)
    generate_nkjv_asset()

    # Copy to assets
    assets_db = os.path.join(
        SCRIPT_DIR, "..", "app", "src", "main", "assets", "databases", "openbible.db")
    os.makedirs(os.path.dirname(assets_db), exist_ok=True)
    import shutil
    shutil.copy2(output_path, assets_db)
    print(f"\nCopied to: {assets_db}")

    elapsed = time.time() - start
    print(f"Total: {elapsed:.1f}s")
