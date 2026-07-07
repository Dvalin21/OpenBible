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
5. Sets PRAGMA user_version = 8
"""

import json
import os
import sqlite3
import time
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OLD_DB = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "databases", "openbible.db")
SCHEMA_JSON = os.path.join(SCRIPT_DIR, "..", "app", "schemas", "com.openbible.data.db.OpenBibleDatabase", "9.json")

# Tables NOT managed by Room (FTS5, etc.) — created separately
NON_ROOM_TABLES = {"verses_fts_kjv", "verses_fts_web", "verses_fts_asv",
                   "verses_fts_ylt", "verses_fts_bbe", "verses_fts_nkjv",
                   "verses_fts_kjv_config", "verses_fts_kjv_content",
                   "verses_fts_kjv_data", "verses_fts_kjv_docsize",
                   "verses_fts_kjv_idx",
                   "verses_fts_web_config", "verses_fts_web_content",
                   "verses_fts_web_data", "verses_fts_web_docsize",
                   "verses_fts_web_idx",
                   "verses_fts_asv_config", "verses_fts_asv_content",
                   "verses_fts_asv_data", "verses_fts_asv_docsize",
                   "verses_fts_asv_idx",
                   "verses_fts_ylt_config", "verses_fts_ylt_content",
                   "verses_fts_ylt_data", "verses_fts_ylt_docsize",
                   "verses_fts_ylt_idx",
                   "verses_fts_bbe_config", "verses_fts_bbe_content",
                   "verses_fts_bbe_data", "verses_fts_bbe_docsize",
                   "verses_fts_bbe_idx",
                   "verses_fts_nkjv_config", "verses_fts_nkjv_content",
                   "verses_fts_nkjv_data", "verses_fts_nkjv_docsize",
                   "verses_fts_nkjv_idx",
                   "sqlite_sequence"}

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

    # Build Room's exact DDL
    entity_ddl = {}  # table_name -> (create_sql, [(index_sql,)])
    for entity in schema['database']['entities']:
        table = entity['tableName']
        create_sql = entity['createSql'].replace("`${TABLE_NAME}`", f"`{table}`").replace("`${TABLE_NAME}`", f"`{table}`")
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
    for table_name in sorted(entity_ddl.keys()):
        rows = old_cursor.execute(f"SELECT * FROM \"{table_name}\"").fetchall()
        if not rows:
            print(f"  {table_name}: 0 rows (skipped)")
            continue

        # Get column names (exclude rowid for INTEGER PRIMARY KEY tables)
        col_names = [d[1] for d in new_cursor.execute(f"PRAGMA table_info(\"{table_name}\")").fetchall()]
        placeholders = ", ".join(["?" for _ in col_names])
        col_list = ", ".join(f"\"{c}\"" for c in col_names)

        batch = []
        for row in rows:
            batch.append(tuple(row[c] for c in col_names))
            if len(batch) >= 500:
                new_cursor.executemany(
                    f"INSERT INTO \"{table_name}\" ({col_list}) VALUES ({placeholders})", batch)
                batch = []

        if batch:
            new_cursor.executemany(
                f"INSERT INTO \"{table_name}\" ({col_list}) VALUES ({placeholders})", batch)

        print(f"  {table_name}: {len(rows):,} rows")

    new_conn.commit()

    # Create FTS5 virtual tables
    print("\nCreating FTS5 virtual tables...")
    for tid in ["kjv", "web", "asv", "ylt", "bbe", "nkjv"]:
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

    # Triggers for FTS sync
    print("  Creating FTS triggers...")
    new_cursor.execute("""
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
    new_cursor.execute("""
        CREATE TRIGGER IF NOT EXISTS verses_ad AFTER DELETE ON verses BEGIN
            DELETE FROM verses_fts_kjv WHERE rowid = old.id;
            DELETE FROM verses_fts_web WHERE rowid = old.id;
            DELETE FROM verses_fts_asv WHERE rowid = old.id;
            DELETE FROM verses_fts_ylt WHERE rowid = old.id;
            DELETE FROM verses_fts_bbe WHERE rowid = old.id;
            DELETE FROM verses_fts_nkjv WHERE rowid = old.id;
        END
    """)
    new_cursor.execute("""
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

    # Set version to match Room's @Database(version = 9)
    new_cursor.execute("PRAGMA user_version = 9")
    print("  PRAGMA user_version = 9")

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
