# OpenBible Data Pipeline

This directory contains the tools to generate the prepopulated Bible
database shipped with the app.

## Prerequisites

```bash
pip3 install requests
```

## Usage

```bash
# Generate the prepopulated database
python3 import_bible.py

# Copy to app assets for Room's createFromAsset()
cp openbible.db ../app/src/main/assets/databases/
```

## Translations

| Abbr | Name | License | Source |
|---|---|---|---|
| KJV | King James Version | Public domain | scrollmapper GitHub |
| WEB | World English Bible | Public domain | scrollmapper GitHub |
| ASV | American Standard Version | Public domain | scrollmapper GitHub |

## Schema

The database schema matches all Room entities in
`com.openbible.data.db.entity.*`. User-data tables (bookmarks, notes,
highlights, etc.) are created empty — they are populated at runtime.

## Adding a New Translation

1. Add the translation info to `TRANSLATIONS` dict in `import_bible.py`
2. Add a CSV/JSON data source URL
3. Re-run the script
4. Re-copy the database to assets
