#!/usr/bin/env python3
"""
Generate assets/translations/nkjv.db for OpenBible's runtime TranslationImporter.

The app imports extra translations from SQLite files in
app/src/main/assets/translations/. Each file must expose a `verses` table:

    id INTEGER, bookId INTEGER, chapter INTEGER, verse INTEGER, text TEXT

and be named "<translationId>.db" (e.g. nkjv.db -> translationId "nkjv").
TranslationImporter copies those rows into the main DB and seeds the matching
`books` rows from the kjv metadata already in the prepop database.

This script converts a source JSON into that schema. Supported formats:
  - midvash: {"books":[{"englishName"/"bookId","chapters":[{"chapter","verses":[{"number","text"}]}]}]}
  - jburson: [{"r":"nkjv:Genesis:1:1","t":"...*pn ..."}, ...]   (USFM markers stripped)

Book name -> canonical id (1-66) is read from the prepop database
(data/openbible.db, kjv rows) so it stays in sync with the app's schema.

Usage:
    python3 generate_nkjv_asset.py <source.json | source_url> [translation_id]
"""
import sqlite3
import sys
import os
import json
import re

MARKER_RE = re.compile(r"\*[a-zA-Z]")  # jburson USFM feature markers: *p *n *s ...
TAG_RE = re.compile(r"<[^>]+>")        # any residual xml-like tags


def clean(text: str) -> str:
    if not text:
        return ""
    return TAG_RE.sub("", MARKER_RE.sub("", text)).strip()


def load_book_maps():
    """name/abbr -> canonical bookId (1-66), read from the prepop DB."""
    db_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "openbible.db")
    name_map, abbr_map = {}, {}
    if os.path.exists(db_path):
        con = sqlite3.connect(db_path)
        for bid, name, abbr in con.execute(
            "SELECT id, name, abbreviation FROM books WHERE translationId = 'kjv'"
        ):
            name_map[name] = bid
            abbr_map[abbr] = bid
        con.close()
    return name_map, abbr_map


def book_id(name: str, name_map, abbr_map):
    n = (name or "").strip()
    if n in name_map:
        return name_map[n]
    if n in abbr_map:
        return abbr_map[n]
    # OSIS-style uppercase (GEN, 1SA, etc.)
    if n.upper() in abbr_map:
        return abbr_map[n.upper()]
    return None


def load_source(src: str):
    if src.startswith("http://") or src.startswith("https://"):
        import urllib.request
        req = urllib.request.Request(src, headers={"User-Agent": "OpenBible/1.0"})
        return json.loads(urllib.request.urlopen(req, timeout=120).read())
    with open(src, "r", encoding="utf-8") as f:
        return json.load(f)


def parse_verses(data, name_map, abbr_map):
    verses = []
    vid = 1

    # midvash format
    if isinstance(data, dict) and "books" in data:
        for b in data["books"]:
            bid = b.get("bookId") or book_id(
                b.get("englishName") or b.get("name", ""), name_map, abbr_map
            )
            if not bid or not (1 <= bid <= 66):
                continue
            for ch in b.get("chapters", []):
                cn = ch.get("chapter", 1)
                for v in ch.get("verses", []):
                    txt = clean(v.get("text", ""))
                    if not txt:
                        continue
                    verses.append((vid, bid, cn, v.get("number", 0), txt))
                    vid += 1
        return verses

    # jburson format: list of {"r": "nkjv:Book:chapter:verse", "t": text}
    if isinstance(data, list):
        for item in data:
            ref = item.get("r", "")
            parts = ref.split(":")
            if len(parts) < 4:
                continue
            try:
                cn, vn = int(parts[2]), int(parts[3])
            except ValueError:
                continue
            bid = book_id(parts[1], name_map, abbr_map)
            if not bid or not (1 <= bid <= 66):
                continue
            txt = clean(item.get("t", ""))
            if not txt:
                continue
            verses.append((vid, bid, cn, vn, txt))
            vid += 1
        return verses

    raise SystemExit("Unsupported source format (need midvash or jburson JSON)")


def write_db(verses, tid):
    out_dir = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "translations",
    )
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"{tid}.db")
    if os.path.exists(out):
        os.remove(out)
    con = sqlite3.connect(out)
    con.execute("PRAGMA journal_mode=WAL;")
    con.execute(
        """CREATE TABLE IF NOT EXISTS verses (
               id INTEGER NOT NULL PRIMARY KEY,
               bookId INTEGER NOT NULL,
               chapter INTEGER NOT NULL,
               verse INTEGER NOT NULL,
               text TEXT NOT NULL)"""
    )
    con.execute("CREATE INDEX IF NOT EXISTS idx_v_book ON verses(bookId, chapter);")
    con.executemany(
        "INSERT INTO verses (id, bookId, chapter, verse, text) VALUES (?,?,?,?,?)", verses
    )
    con.commit()
    con.close()
    print(f"Wrote {out}: {len(verses)} verses for '{tid}'")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    src = sys.argv[1]
    tid = sys.argv[2] if len(sys.argv) > 2 else "nkjv"
    name_map, abbr_map = load_book_maps()
    if not name_map:
        sys.exit("Could not read book maps from data/openbible.db")
    data = load_source(src)
    verses = parse_verses(data, name_map, abbr_map)
    if not verses:
        sys.exit(f"No verses parsed from {src}")
    write_db(verses, tid)
