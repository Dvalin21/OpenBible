#!/usr/bin/env python3
"""
Import NASB, ESV, CSB translations from jburson/bible-data JSON into the
OpenBible prepopulated SQLite DB.

Source format (per version, e.g. data/csb/csb.json): a flat list of items:
  {"o": <order>, "r": "csb:Genesis:1:1", "t": "verse text with *xx markup"}
  Items with "h" are section headers; refs ending ":0" are chapter headers.

Inline markup (*r *p *s *ln etc.) is stripped. Verses absent from a translation
(v. 0 differences: textual criticism — e.g. Acts 15:34) simply get no row; the
app reads verses by (translationId, bookId, chapter, verse) so missing rows are
handled as gaps.

Usage:
    python3 data/import_translations.py /tmp/bible-data/data
"""

import json
import os
import re
import sqlite3
import sys

# Canonical book order (1-66), matching books.number in the existing DB.
BOOK_ORDER = [
    "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua",
    "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings",
    "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job",
    "Psalm", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah",
    "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel",
    "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah",
    "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
    "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians",
    "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
    "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
    "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John",
    "3 John", "Jude", "Revelation",
]
BOOK_NUM = {name: i + 1 for i, name in enumerate(BOOK_ORDER)}

VERSIONS = {
    "nasb": ("New American Standard Bible", "NASB", 0,
             "Copyright © 1960, 1971, 1977, 1995 by The Lockman Foundation"),
    "esv": ("English Standard Version", "ESV", 0,
            "Copyright © 2001 by Crossway Bibles"),
    "csb": ("Christian Standard Bible", "CSB", 0,
            "Copyright © 2017 by Holman Bible Publishers"),
}

MARKUP = re.compile(r"\*[a-z]+")


def clean_text(t: str) -> str:
    return MARKUP.sub("", t).replace("  ", " ").strip()


def main(data_root: str, db_path: str) -> None:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Next available verse id (ids are shared across translations).
    max_id = cur.execute(
        "SELECT COALESCE(MAX(id), (SELECT MAX(id) FROM verses)) FROM verses"
    ).fetchone()[0]
    next_id = (max_id or 0) + 1

    for ver, (name, abbr, public_domain, copyright_) in VERSIONS.items():
        src = os.path.join(data_root, ver, f"{ver}.json")
        print(f"[{ver}] reading {src}")
        with open(src) as f:
            items = json.load(f)

        cur.execute(
            "INSERT OR REPLACE INTO translations "
            "(id, name, abbreviation, language, copyright, isPublicDomain, isBundled) "
            "VALUES (?, ?, ?, 'en', ?, ?, 1)",
            (ver, name, abbr, copyright_, public_domain),
        )

        # Book metadata copied from KJV rows (same canonical numbering).
        # books schema: id, translationId, name, abbreviation, number,
        # chapterCount, testament, totalVerses
        cur.execute(
            """INSERT OR IGNORE INTO books
               (id, translationId, number, name, abbreviation, chapterCount,
                testament, totalVerses)
               SELECT id, ?, number, name, abbreviation, chapterCount,
                      testament, totalVerses
               FROM books WHERE translationId = 'kjv'""",
            (ver,),
        )

        inserted = 0
        batch = []
        seen = set()
        for item in items:
            if "h" in item or not item.get("t"):
                continue  # header/chapter-title rows
            ref = item["r"]
            parts = ref.split(":")
            if len(parts) != 4 or parts[3] == "0":
                continue
            _, book_name, chapter, verse = parts
            book_id = BOOK_NUM.get(book_name)
            if book_id is None:
                print(f"  WARN unknown book: {ref}")
                continue
            key = (book_id, int(chapter), int(verse))
            if key in seen:
                continue  # duplicate item (e.g. verse split across paragraphs)
            seen.add(key)
            text = clean_text(item["t"])
            if not text:
                continue
            batch.append((next_id, ver, book_id, key[1], key[2], text))
            next_id += 1
            inserted += 1
            if len(batch) >= 5000:
                cur.executemany(
                    "INSERT OR IGNORE INTO verses "
                    "(id, translationId, bookId, chapter, verse, text) "
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    batch,
                )
                batch = []

        if batch:
            cur.executemany(
                "INSERT OR IGNORE INTO verses "
                "(id, translationId, bookId, chapter, verse, text) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                batch,
            )

        # Update chapter counts AND verse totals from actual data. COALESCE:
        # a book with zero imported rows must not NULL a NOT NULL column.
        cur.execute(
            """UPDATE books SET chapterCount = COALESCE(
                (SELECT MAX(chapter) FROM verses WHERE verses.bookId = books.id
                 AND verses.translationId = books.translationId), 0)
                WHERE translationId = ?""",
            (ver,),
        )
        cur.execute(
            """UPDATE books SET totalVerses =
               (SELECT COUNT(*) FROM verses WHERE verses.bookId = books.id
                AND verses.translationId = books.translationId)
               WHERE translationId = ?""",
            (ver,),
        )

        # Build the FTS table for this translation. Matches the standalone
        # (non-content) format of verses_fts_kjv in the shipped DB.
        cur.execute(f"""
            CREATE VIRTUAL TABLE IF NOT EXISTS verses_fts_{ver} USING fts5(
                text, bookId UNINDEXED, chapter UNINDEXED, verse UNINDEXED,
                verseId UNINDEXED, tokenize='porter unicode61'
            )
        """)
        cur.execute(f"DELETE FROM verses_fts_{ver}")
        cur.execute(
            f"""INSERT INTO verses_fts_{ver}
                (rowid, text, bookId, chapter, verse, verseId)
                SELECT id, text, bookId, chapter, verse, id FROM verses
                WHERE translationId = ?""",
            (ver,),
        )
        cnt = cur.execute(
            f"SELECT COUNT(*) FROM verses_fts_{ver}"
        ).fetchone()[0]
        conn.commit()
        print(f"[{ver}] {inserted:,} verses imported, FTS rows: {cnt:,}")

    conn.close()
    print("Done.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
