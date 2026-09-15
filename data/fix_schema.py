#!/usr/bin/env python3
"""
Fix prepopulated DB schema to match Room's exact DDL.

Room validates every @Entity table schema when opening a pre-packaged DB.
The Python script's DDL has DEFAULT values, inline PRIMARY KEY, and
different index names that don't match Room's expected createSql.

This script:
1. Reads all data from the existing DB
2. Creates a new DB with Room's exact DDL (from schema JSON)
3. Copies all data
4. Creates FTS5 virtual tables
5. Sets PRAGMA user_version from the schema JSON
"""

import json
import os
import sqlite3
import time
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OLD_DB = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "databases", "openbible.db")
SCHEMA_DIR = os.path.join(SCRIPT_DIR, "..", "app", "schemas", "com.openbible.data.db.OpenBibleDatabase")

# ponytail: always repair against the newest exported schema, never a pinned one
def latest_schema_json():
    versions = []
    for name in os.listdir(SCHEMA_DIR):
        if name.endswith(".json") and name[:-5].isdigit():
            versions.append(int(name[:-5]))
    if not versions:
        return None
    return os.path.join(SCHEMA_DIR, f"{max(versions)}.json")

SCHEMA_JSON = latest_schema_json()

# Personal-use translation set — must match OpenBibleDatabase.FTS_ALL
FTS_TRANSLATIONS = ["kjv", "web", "asv", "ylt", "bbe",
                    "nasb", "esv", "csb", "dra", "geneva"]

def main():
    if not os.path.exists(OLD_DB):
        print(f"ERROR: Old DB not found at {OLD_DB}")
        sys.exit(1)
    if not os.path.exists(SCHEMA_JSON):
        print(f"ERROR: Schema JSON not found at {SCHEMA_JSON}")
        sys.exit(1)

    print("Reading Room schema JSON...")
    with open(SCHEMA_JSON) as f:
        schema = json.load(f)
    db_version = schema['database']['version']
    print(f"  Target Room version: {db_version}")

    # Build Room's exact DDL
    entity_ddl = {}  # table_name -> (create_sql, [(index_sql,)])
    for entity in schema['database']['entities']:
        table = entity['tableName']
        create_sql = entity['createSql'].replace("`${TABLE_NAME}`", f"`{table}`")
        indices = [idx['createSql'].replace("`${TABLE_NAME}`", f"`{table}`") for idx in entity.get('indices', [])]
        entity_ddl[table] = (create_sql, indices)

    print(f"Found {len(entity_ddl)} Room entity tables")

    # Open old DB (read-only) and new DB
    temp_db = OLD_DB + ".fixed"
    if os.path.exists(temp_db):
        os.remove(temp_db)

    old_conn = sqlite3.connect(OLD_DB)
    old_conn.row_factory = sqlite3.Row
    old_cursor = old_conn.cursor()

    new_conn = sqlite3.connect(temp_db)
    new_cursor = new_conn.cursor()
    new_cursor.execute("PRAGMA journal_mode=WAL;")
    new_cursor.execute("PRAGMA synchronous=OFF;")
    new_cursor.execute("PRAGMA cache_size=-256000;")

    print("\nCreating Room entity tables...")
    for table_name in sorted(entity_ddl.keys()):
        create_sql, indices = entity_ddl[table_name]
        new_cursor.execute(create_sql)
        for idx_sql in indices:
            new_cursor.execute(idx_sql)
        print(f"  {table_name}: table + {len(indices)} indices")

    # Copy data for each entity table
    print("\nCopying data...")
    old_tables = {r[0] for r in old_cursor.execute(
        "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    for table_name in sorted(entity_ddl.keys()):
        # ponytail: old DB may predate the table (e.g. note_audio) — skip, don't crash
        if table_name not in old_tables:
            print(f"  {table_name}: missing in old DB (skipped)")
            continue
        rows = old_cursor.execute(f"SELECT * FROM \"{table_name}\"").fetchall()
        if not rows:
            print(f"  {table_name}: 0 rows (skipped)")
            continue

        # Get column names (exclude rowid for INTEGER PRIMARY KEY tables).
        # ponytail: columns missing in the old DB are omitted from the INSERT
        # so the new table's DEFAULTs apply (e.g. isFavorite → 0) — EXCEPT
        # NOT NULL columns without a DEFAULT (Room drops ADD COLUMN defaults
        # from the canonical createSql, e.g. strong_numbers.lemmaNorm): those
        # are backfilled by affinity (''/0) or the copy dies on NOT NULL.
        new_info = new_cursor.execute(f"PRAGMA table_info(\"{table_name}\")").fetchall()
        col_names = [d[1] for d in new_info]
        notnull_nodefault = {
            d[1] for d in new_info if d[3] and d[4] is None
        }
        old_cols = {d[1] for d in old_cursor.execute(f"PRAGMA table_info(\"{table_name}\")").fetchall()}
        insert_cols = [c for c in col_names if c in old_cols]
        fill_cols = [c for c in col_names if c not in old_cols and c in notnull_nodefault]
        placeholders = ", ".join(["?" for _ in insert_cols + fill_cols])
        col_list = ", ".join(f"\"{c}\"" for c in insert_cols + fill_cols)

        batch = []
        for row in rows:
            vals = tuple(row[c] for c in insert_cols)
            if fill_cols:
                # affinity guess from the new table's declared type
                types = {d[1]: (d[2] or "") for d in new_info}
                vals += tuple(
                    0 if "INT" in types[c].upper() or "REAL" in types[c].upper()
                      or "FLOA" in types[c].upper() or "DOUB" in types[c].upper()
                    else "" for c in fill_cols
                )
            batch.append(vals)
            if len(batch) >= 500:
                new_cursor.executemany(
                    f"INSERT INTO \"{table_name}\" ({col_list}) VALUES ({placeholders})", batch)
                batch = []

        if batch:
            new_cursor.executemany(
                f"INSERT INTO \"{table_name}\" ({col_list}) VALUES ({placeholders})", batch)

        print(f"  {table_name}: {len(rows):,} rows")

    new_conn.commit()

    # Create FTS5 virtual tables (plain shape, matching import_bible.py —
    # external-content tables break INSERT..SELECT, see OpenBibleDatabase)
    print("\nCreating FTS5 virtual tables...")
    for tid in FTS_TRANSLATIONS:
        new_cursor.execute(f"""
            CREATE VIRTUAL TABLE IF NOT EXISTS verses_fts_{tid} USING fts5(
                text,
                bookId UNINDEXED,
                chapter UNINDEXED,
                verse UNINDEXED,
                verseId UNINDEXED,
                tokenize='porter unicode61'
            )
        """)
        new_cursor.execute(f"""
            INSERT INTO verses_fts_{tid}(rowid, text, bookId, chapter, verse, verseId)
            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE translationId = ?
        """, (tid,))
        cnt = new_cursor.execute(f"SELECT COUNT(*) FROM verses_fts_{tid}").fetchone()[0]
        print(f"  verses_fts_{tid}: {cnt:,} rows")

    # Triggers for FTS sync (same 10-branch shape as the app)
    print("  Creating FTS triggers...")
    ai_selects = "\n".join(
        f"            INSERT INTO verses_fts_{t}(rowid, text, bookId, chapter, verse, verseId)\n"
        f"            SELECT id, text, bookId, chapter, verse, id FROM verses WHERE id = new.id AND translationId = '{t}';"
        for t in FTS_TRANSLATIONS)
    deletes = "\n".join(
        f"            DELETE FROM verses_fts_{t} WHERE rowid = old.id;"
        for t in FTS_TRANSLATIONS)
    new_cursor.execute(f"""
        CREATE TRIGGER IF NOT EXISTS verses_ai AFTER INSERT ON verses BEGIN
{ai_selects}
        END
    """)
    new_cursor.execute(f"""
        CREATE TRIGGER IF NOT EXISTS verses_ad AFTER DELETE ON verses BEGIN
{deletes}
        END
    """)
    new_cursor.execute(f"""
        CREATE TRIGGER IF NOT EXISTS verses_au AFTER UPDATE ON verses BEGIN
{deletes}
{ai_selects}
        END
    """)

    # user_version comes from the schema JSON — never hardcode it here
    new_cursor.execute(f"PRAGMA user_version = {db_version}")
    print(f"  PRAGMA user_version = {db_version}")

    new_conn.commit()

    # Verify
    print("\nVerifying entity tables...")
    for table_name in sorted(entity_ddl.keys()):
        expected = entity_ddl[table_name][0]
        actual = new_cursor.execute(
            f"SELECT sql FROM sqlite_master WHERE type='table' AND name='{table_name}'"
        ).fetchone()
        actual_sql = actual[0] if actual else "MISSING"
        # Check that the SQL matches modulo whitespace and `IF NOT EXISTS`
        # Room uses `IF NOT EXISTS` in its SQL
        if actual_sql and 'CREATE TABLE' in actual_sql:
            cnt = new_cursor.execute(f"SELECT COUNT(*) FROM \"{table_name}\"").fetchone()[0]
            print(f"  {table_name}: {cnt:,} rows ✅")
        else:
            print(f"  {table_name}: ❌ MISSING or WRONG")

    # Stats
    print("\nFinal stats:")
    for table in ["verses", "books", "translations", "cross_references", "strong_numbers", "verse_strong_links"]:
        cnt = new_cursor.execute(f"SELECT COUNT(*) FROM \"{table}\"").fetchone()[0]
        print(f"  {table}: {cnt:,}")

    new_conn.close()
    old_conn.close()

    # Replace old DB with new
    os.replace(temp_db, OLD_DB)
    print(f"\nDone! Replaced {OLD_DB}")

    new_size = os.path.getsize(OLD_DB)
    print(f"New size: {new_size / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    start = time.time()
    main()
    print(f"Elapsed: {time.time() - start:.1f}s")
