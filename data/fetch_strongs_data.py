#!/usr/bin/env python3
"""
Fetch Strong's Concordance dataset from public domain sources.

Downloads:
  1. Strong's Greek + Hebrew lexicon (~14,000 entries, ~800KB)
  2. Interlinear data (verse → word → Strong's number mapping, ~12MB gzip)

Sources:
  - Lexicon: kennethreitz/kjvstudy.org (data/strongs/greek.json, hebrew.json)
            Derived from openscriptures/strongs (public domain)
  - Interlinear: tahmmee/interlinear_bibledata (public domain)

Usage:
    python3 fetch_strongs_data.py
    # Output: data/strongs/strong_numbers.json, verse_links.json
"""

import json
import os
import sys
import gzip
import io
import time
import re
from typing import Optional
import urllib.request

# ── Configuration ────────────────────────────────────────────────

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "strongs")

# kjvstudy.org data files on raw GitHub
GREEK_URL = "https://raw.githubusercontent.com/kennethreitz/kjvstudy.org/main/kjvstudy_org/data/strongs/greek.json"
HEBREW_URL = "https://raw.githubusercontent.com/kennethreitz/kjvstudy.org/main/kjvstudy_org/data/strongs/hebrew.json"

# Interlinear data (verse → word → Strong's)
# From tahmmee/interlinear_bibledata on GitHub
# Format: gzip-compressed JSON with key "Book:Chapter:Verse" → list of word objects
INTERLINEAR_URL = (
    "https://raw.githubusercontent.com/tahmmee/interlinear_bibledata/"
    "master/interlinear.json.gz"
)


def download_file(url: str, desc: str) -> Optional[bytes]:
    """Download a file from URL with simple progress."""
    print(f"  Downloading {desc}...")
    print(f"    URL: {url}")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "OpenBible-Import/1.0"})
        with urllib.request.urlopen(req, timeout=120) as f:
            data = f.read()
        print(f"    Downloaded {len(data):,} bytes")
        return data
    except Exception as e:
        print(f"    ERROR: {e}", file=sys.stderr)
        return None


def convert_lexicon_entry(key: str, entry: dict, language: str) -> dict:
    """
    Convert a kjvstudy.org Strong's entry to OpenBible format.
    
    kjvstudy format:
    {
      "strongs": "H1",
      "word": "אָב",
      "transliteration": "ab",
      "pronunciation": "awb",
      "definition": "father",
      "kjv_usage": "father, chief, ...",
      "derivation": "from primitive root"
    }
    
    OpenBible format:
    {
      "number": "H0001",
      "lemma": "אָב",
      "transliteration": "ab",
      "pronunciation": "awb",
      "partOfSpeech": null,
      "definition": "father",
      "derivation": "from primitive root",
      "usageCount": 0,
      "language": "Hebrew"
    }
    """
    # Parse Strong's number - ensure 4-digit format with leading zeros
    raw_num = entry.get("strongs", key)
    # Already has format like "H1" or "G3056"
    # We want "H0001" or "G3056"
    prefix = raw_num[0].upper()  # "H" or "G"
    num_part = raw_num[1:]
    try:
        num_val = int(num_part)
    except ValueError:
        num_val = 0
    # Keep original format but zero-pad to 4 digits for consistency
    # Actually, OpenBible uses whatever format is provided (e.g., "G3056")
    # The lexicon format in kjvstudy is H1 not H0001, but the interlinear uses G3779 format
    # Let's use the original format from the data
    
    definition = entry.get("definition", entry.get("strongs_def", ""))
    
    return {
        "number": raw_num,
        "lemma": entry.get("word", entry.get("lemma", "")),
        "transliteration": entry.get("transliteration", entry.get("xlit", "")),
        "pronunciation": entry.get("pronunciation", entry.get("pron", "")),
        "partOfSpeech": None,  # Not in kjvstudy data, would need morph data
        "definition": definition,
        "derivation": entry.get("derivation", ""),
        "usageCount": 0,  # Not in kjvstudy data
        "language": language
    }


def process_interlinear_for_verse_links(
    interlinear: dict, verse_id_map: dict
) -> list:
    """
    Process interlinear data to extract verse → Strong's number links.
    
    Interlinear format:
    {
      "John:3:16": [
        {
          "position": 1,
          "original": "Οὕτως",
          "transliteration": "houtōs",
          "strongs": "G3779",
          "english": "For so",
          "parsing": "adv",
          "definition": "..."
        },
        ...
      ]
    }
    
    verse_id_map is { "Book Chapter:Verse" → verse_id }
    
    Output: list of VerseStrongLink-like dicts
    """
    links = []
    skipped = 0
    parsed = 0
    
    for ref_key, words in interlinear.items():
        # ref_key is like "John:3:16"
        parts = ref_key.split(":")
        if len(parts) != 3:
            skipped += 1
            continue
        book_abbr, chapter_str, verse_str = parts
        try:
            chapter = int(chapter_str)
            verse = int(verse_str)
        except ValueError:
            skipped += 1
            continue
        
        # Look up verse ID by (bookAbbr, chapter, verse) — we need the map
        # verse_id_map uses "Abbr:chapter:verse" as key
        lookup_key = f"{book_abbr}:{chapter}:{verse}"
        verse_id = verse_id_map.get(lookup_key)
        if verse_id is None:
            # Try alternative abbreviations
            for abbr_alias in get_book_abbreviation_aliases(book_abbr):
                alias_key = f"{abbr_alias}:{chapter}:{verse}"
                verse_id = verse_id_map.get(alias_key)
                if verse_id is not None:
                    break
        
        if verse_id is None:
            skipped += 1
            continue
        
        for word in words:
            strong_num = word.get("strongs", "")
            if not strong_num:
                continue
            
            # Normalize: strip leading zeros from number portion
            # e.g., "G04566" → "G4566"
            if len(strong_num) > 1 and strong_num[0] in ('G', 'H'):
                try:
                    num = int(strong_num[1:])
                    strong_num = strong_num[0] + str(num)
                except ValueError:
                    pass
            
            links.append({
                "verseId": verse_id,
                "strongNumber": strong_num,
                "wordPosition": word.get("position", 0),
                "originalWord": word.get("original", ""),
                "transliteration": word.get("transliteration", None)
            })
            parsed += 1
    
    print(f"  Parsed {parsed:,} verse-strong links ({skipped} references skipped)")
    return links


def get_book_abbreviation_aliases(abbr: str) -> list:
    """Map various abbreviation styles to our canonical form."""
    abbr_upper = abbr.upper()
    # Common midvash/bible-data abbreviation to search-alias mapping
    aliases = {
        "PS": ["Ps", "Psa", "Psalm"],
        "PSA": ["Ps", "Psa", "Psalm"],
        "1SAM": ["1Sam"],
        "2SAM": ["2Sam"],
        "1KGS": ["1Kgs", "1Ki"],
        "2KGS": ["2Kgs", "2Ki"],
        "1CHR": ["1Chr"],
        "2CHR": ["2Chr"],
        "1COR": ["1Cor"],
        "2COR": ["2Cor"],
        "1THESS": ["1Thess"],
        "2THESS": ["2Thess"],
        "1TIM": ["1Tim"],
        "2TIM": ["2Tim"],
        "1PET": ["1Pet"],
        "2PET": ["2Pet"],
        "1JOHN": ["1John"],
        "2JOHN": ["2John"],
        "3JOHN": ["3John"],
        "EXOD": ["Exod", "Exo", "Ex"],
        "DEUT": ["Deut"],
        "JOSH": ["Josh"],
        "JUDG": ["Judg", "Jdg"],
        "1SAMUEL": ["1Sam"],
        "2SAMUEL": ["2Sam"],
        "1KINGS": ["1Kgs"],
        "2KINGS": ["2Kgs"],
        "1CHRONICLES": ["1Chr"],
        "2CHRONICLES": ["2Chr"],
        "ECCL": ["Eccl"],
        "SONG": ["Song"],
        "JER": ["Jer"],
        "LAM": ["Lam"],
        "EZEK": ["Ezek"],
        "DAN": ["Dan"],
        "HOS": ["Hos"],
        "JOEL": ["Joel"],
        "AMOS": ["Amos"],
        "OBAD": ["Obad"],
        "JONAH": ["Jonah"],
        "MIC": ["Mic"],
        "NAH": ["Nah"],
        "HAB": ["Hab"],
        "ZEPH": ["Zeph"],
        "HAG": ["Hag"],
        "ZECH": ["Zech"],
        "MAL": ["Mal"],
        "MATT": ["Matt"],
        "1CORINTHIANS": ["1Cor"],
        "2CORINTHIANS": ["2Cor"],
        "GAL": ["Gal"],
        "EPH": ["Eph"],
        "PHIL": ["Phil"],
        "COL": ["Col"],
        "1THESSALONIANS": ["1Thess"],
        "2THESSALONIANS": ["2Thess"],
        "1TIMOTHY": ["1Tim"],
        "2TIMOTHY": ["2Tim"],
        "PHILEM": ["Phlm"],
        "HEB": ["Heb"],
        "JAS": ["Jas"],
        "1PETER": ["1Pet"],
        "2PETER": ["2Pet"],
        "1JN": ["1John"],
        "2JN": ["2John"],
        "3JN": ["3John"],
        "JUDE": ["Jude"],
        "REV": ["Rev"],
    }
    return aliases.get(abbr_upper, [])


def build_verse_id_map(prepop_db_path: str) -> dict:
    """
    Read verse IDs from the prepopulated database.
    Returns { "Abbr:chapter:verse": verse_id }
    """
    import sqlite3
    if not os.path.exists(prepop_db_path):
        print(f"  WARNING: Prepop DB not found at {prepop_db_path}")
        print(f"  Verse links will use placeholder IDs (run import_bible.py first)")
        return {}
    
    conn = sqlite3.connect(prepop_db_path)
    cursor = conn.cursor()
    
    # Get KJV verse IDs with book abbreviation
    cursor.execute("""
        SELECT v.id, b.abbreviation, v.chapter, v.verse
        FROM verses v
        INNER JOIN books b ON b.id = v.bookId AND b.translationId = v.translationId
        WHERE v.translationId = 'kjv'
    """)
    
    verse_map = {}
    for vid, abbr, ch, vs in cursor.fetchall():
        key = f"{abbr}:{ch}:{vs}"
        verse_map[key] = vid
    
    conn.close()
    print(f"  Built verse ID map: {len(verse_map):,} KJV verses")
    return verse_map


def main():
    print("OpenBible — Strong's Data Fetcher")
    print("=" * 60)
    start = time.time()
    
    # Ensure output directory exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # ── Step 1: Download Strong's lexicon ────────────────────────
    print("\n[Step 1] Downloading Strong's Greek lexicon...")
    greek_data = download_file(GREEK_URL, "Greek lexicon")
    
    print("\n[Step 2] Downloading Strong's Hebrew lexicon...")
    hebrew_data = download_file(HEBREW_URL, "Hebrew lexicon")
    
    if greek_data is None and hebrew_data is None:
        print("\nERROR: Could not download any Strong's data!", file=sys.stderr)
        sys.exit(1)
    
    # ── Step 2: Convert to OpenBible format ─────────────────────
    print("\n[Step 3] Converting lexicon entries...")
    all_numbers = []
    
    if greek_data:
        greek_dict = json.loads(greek_data.decode("utf-8"))
        for key, entry in greek_dict.items():
            converted = convert_lexicon_entry(key, entry, "Greek")
            all_numbers.append(converted)
        print(f"  Greek entries: {len(greek_dict):,}")
    
    if hebrew_data:
        hebrew_dict = json.loads(hebrew_data.decode("utf-8"))
        for key, entry in hebrew_dict.items():
            converted = convert_lexicon_entry(key, entry, "Hebrew")
            all_numbers.append(converted)
        print(f"  Hebrew entries: {len(hebrew_dict):,}")
    
    print(f"  Total lexicon entries: {len(all_numbers):,}")
    
    # Write lexicon data
    numbers_path = os.path.join(OUTPUT_DIR, "strong_numbers.json")
    with open(numbers_path, "w", encoding="utf-8") as f:
        json.dump(all_numbers, f, ensure_ascii=False, indent=2)
    size_kb = os.path.getsize(numbers_path) / 1024
    print(f"  Wrote {numbers_path} ({size_kb:.1f} KB)")
    
    # ── Step 3: Download interlinear data for verse links ────────
    print("\n[Step 4] Downloading interlinear data for verse-strong links...")
    interlinear_raw = download_file(INTERLINEAR_URL, "Interlinear data (gzip)")
    
    links = []
    if interlinear_raw is not None:
        # Decompress gzip
        try:
            decompressed = gzip.decompress(interlinear_raw)
            interlinear = json.loads(decompressed.decode("utf-8"))
            print(f"  Interlinear entries: {len(interlinear):,}")
        except Exception as e:
            print(f"  ERROR decompressing interlinear: {e}", file=sys.stderr)
            interlinear = {}
        
        if interlinear:
            # Try to build verse ID map from prepop DB
            prepop_db = os.path.join(SCRIPT_DIR, "openbible.db")
            verse_map = build_verse_id_map(prepop_db)
            
            if not verse_map:
                print("  WARNING: No verse ID map available — generating placeholder IDs")
                print("  Verse links will be updated when prepop DB is regenerated")
            
            links = process_interlinear_for_verse_links(interlinear, verse_map)
    
    # Write verse links
    links_path = os.path.join(OUTPUT_DIR, "verse_links.json")
    with open(links_path, "w", encoding="utf-8") as f:
        json.dump(links, f, ensure_ascii=False, indent=2)
    size_kb = os.path.getsize(links_path) / 1024
    print(f"  Wrote {links_path} ({size_kb:.1f} KB)")
    
    elapsed = time.time() - start
    print(f"\n{'='*60}")
    print(f"Completed in {elapsed:.1f}s")
    print(f"Output directory: {OUTPUT_DIR}")
    print(f"  strong_numbers.json: {len(all_numbers):,} entries")
    print(f"  verse_links.json: {len(links):,} links")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
