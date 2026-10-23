Importable translations
======================

Drop a SQLite file here named <translationId>.db (e.g. nkjv.db) to import
a licensed translation at first launch. The file must contain a `verses`
table with columns: translationId, bookId, chapter, verse, text.

NKJV is not bundled because it is copyrighted (Thomas Nelson). To use it,
obtain a licensed copy and create nkjv.db yourself, then rebuild the app.

The importer runs once per file (tracked in SharedPreferences) and skips
translations that already have verse data.