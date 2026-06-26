package com.openbible.data.translation

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Attempts to import verse data for each translation file found in assets/translations/.
     * Called once per translation (tracks completion in SharedPreferences).
     */
    suspend fun importMissing(db: SupportSQLiteDatabase) {
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
            val cursor = db.query("SELECT COUNT(*) FROM verses WHERE translationId = ?", arrayOf(translationId))
            cursor.moveToFirst()
            val existing = cursor.getLong(0)
            cursor.close()
            if (existing > 0) {
                prefs.edit().putBoolean("imported_$file", true).apply()
                continue
            }
            doImport(db, translationId, file)
            prefs.edit().putBoolean("imported_$file", true).apply()
        }
    }

    private fun doImport(db: SupportSQLiteDatabase, translationId: String, assetFile: String) {
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
            val cursor = source.rawQuery("SELECT bookId, chapter, verse, text FROM verses ORDER BY bookId, chapter, verse", null)
            cursor.use { c ->
                db.beginTransaction()
                try {
                    val values = ContentValues()
                    while (c.moveToNext()) {
                        values.clear()
                        values.put("translationId", translationId)
                        values.put("bookId", c.getInt(0))
                        values.put("chapter", c.getInt(1))
                        values.put("verse", c.getInt(2))
                        values.put("text", c.getString(3))
                        db.insert("verses", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    db.setTransactionSuccessful()
                    android.util.Log.i(TAG, "Imported $translationId from $assetFile")
                } finally {
                    db.endTransaction()
                }
            }
        }
        tempFile.delete()
    }

    companion object {
        private const val TAG = "TranslationImporter"
        private const val PREFS_NAME = "translation_import"
    }
}
