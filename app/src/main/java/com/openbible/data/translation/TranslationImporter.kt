package com.openbible.data.translation

import android.content.Context
import com.openbible.data.db.dao.ImportDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports additional translation verse data from SQLite files in assets/translations/.
 *
 * Each file must match the existing `verses` table schema:
 *   translationId TEXT NOT NULL,
 *   bookId INTEGER NOT NULL,
 *   chapter INTEGER NOT NULL,
 *   verse INTEGER NOT NULL,
 *   text TEXT NOT NULL
 *
 * Skips import if the translation already has verse data in the database.
 *
 * Ship files as: assets/translations/bbe.db, assets/translations/nkjv.db
 * Create them with: sqlite3 bbe.db "CREATE TABLE verses (...); INSERT ..."
 */
@Singleton
class TranslationImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importDao: ImportDao
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Attempts to import verse data for each translation file found in assets/translations/.
     * Called once per translation (tracks completion in SharedPreferences).
     */
    suspend fun importMissing() {
        val dir = "translations"
        val files: List<String>
        try {
            files = context.assets.list(dir)?.filter { it.endsWith(".db") }.orEmpty()
        } catch (_: Exception) {
            return // no translation files shipped yet
        }
        for (file in files) {
            if (prefs.getBoolean("imported_$file", false)) continue
            val translationId = file.removeSuffix(".db")
            // Check if verses already exist for this translation
            val existing = importDao.countVersesForTranslation(translationId)
            if (existing > 0) {
                prefs.edit().putBoolean("imported_$file", true).apply()
                continue
            }
            doImport(translationId, file)
            prefs.edit().putBoolean("imported_$file", true).apply()
        }
    }

    private suspend fun doImport(translationId: String, assetFile: String) {
        // Open the asset DB file
        val assetBytes: ByteArray
        try {
            assetBytes = context.assets.open("translations/$assetFile").use { it.readBytes() }
        } catch (_: Exception) {
            android.util.Log.w(TAG, "Cannot open translation asset: $assetFile")
            return
        }
        // Write to temp file so SQLite can open it
        val tempFile = java.io.File(context.cacheDir, assetFile)
        tempFile.writeBytes(assetBytes)

        val importDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            tempFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        importDb.use { source ->
            val cursor = source.rawQuery(
                "SELECT bookId, chapter, verse, text FROM verses ORDER BY bookId, chapter, verse", null
            )
            val bookVerseCounts = mutableMapOf<Int, Int>()
            cursor.use { c ->
                val verses = mutableListOf<com.openbible.data.db.entity.VerseEntity>()
                while (c.moveToNext()) {
                    val bookId = c.getInt(0)
                    verses.add(com.openbible.data.db.entity.VerseEntity(
                        id = 0, // auto-generated
                        translationId = translationId,
                        bookId = bookId,
                        chapter = c.getInt(1),
                        verse = c.getInt(2),
                        text = c.getString(3)
                    ))
                    bookVerseCounts[bookId] = bookVerseCounts.getOrDefault(bookId, 0) + 1
                }
                importDao.insertVerses(verses)
            }
            seedBooks(translationId, bookVerseCounts)
        }
        tempFile.delete()
    }

    /**
     * Ensure a `books` row exists for every book imported. With the composite
     * (translationId, id) primary key each translation owns its own book list;
     * a runtime-imported translation such as NKJV otherwise has verses but no
     * book rows and the UI cannot show its books. Copy canonical book metadata
     * from an already-present translation (names/chapter counts are
     * translation-independent) and `INSERT OR IGNORE` so existing rows are left
     * untouched.
     */
    private suspend fun seedBooks(
        translationId: String,
        bookVerseCounts: Map<Int, Int>
    ) {
        if (bookVerseCounts.isEmpty()) return
        val kjvBooks = importDao.getKjvBooks().firstOrNull() ?: return
        val booksToInsert = mutableListOf<com.openbible.data.db.entity.BookEntity>()
        for ((bookId, count) in bookVerseCounts) {
            kjvBooks.firstOrNull { it.id == bookId }?.let { meta ->
                booksToInsert.add(com.openbible.data.db.entity.BookEntity(
                    translationId = translationId,
                    id = bookId,
                    name = meta.name,
                    abbreviation = meta.abbreviation,
                    number = meta.number,
                    chapterCount = meta.chapterCount,
                    testament = meta.testament,
                    totalVerses = count
                ))
            }
        }
        if (booksToInsert.isNotEmpty()) {
            importDao.insertBooks(booksToInsert)
            android.util.Log.i(TAG, "Seeded ${booksToInsert.size} books for $translationId")
        }
    }

    companion object {
        private const val TAG = "TranslationImporter"
        private const val PREFS_NAME = "translation_import"
    }
}