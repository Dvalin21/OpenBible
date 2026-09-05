package com.openbible.data.strongs

import android.content.Context
import com.openbible.data.db.dao.StrongDao
import com.openbible.data.db.entity.StrongNumberEntity
import com.openbible.data.db.entity.VerseStrongLinkEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports Strong's Concordance data from JSON assets into Room.
 *
 * Runs once on first launch after DB migration. Checks SharedPreferences
 * for a flag to avoid re-importing.
 *
 * Expected asset paths:
 *   assets/strongs/strong_numbers.json   — array of StrongNumberEntity entries
 *   assets/strongs/verse_links.json      — array of VerseStrongLinkEntity entries
 */
@Singleton
class StrongImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val strongDao: StrongDao
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns true if import was completed (now or previously). */
    suspend fun importIfNeeded(): Boolean {
        if (prefs.getBoolean(PREFS_KEY_IMPORTED, false)) return true
        return withContext(Dispatchers.IO) {
            try {
                // ponytail: flag only when rows actually landed — missing/empty
                // assets must retry next launch, not poison the flag
                if (doImport()) {
                    prefs.edit().putBoolean(PREFS_KEY_IMPORTED, true).apply()
                    true
                } else false
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Strong's import failed", e)
                false
            }
        }
    }

    private suspend fun doImport(): Boolean {
        // Check if data already present (e.g. from prepopulated DB)
        if (strongDao.strongNumberCount() > 0) {
            android.util.Log.i(TAG, "Strong's data already present, skipping import")
            return true
        }

        var imported = false
        // Import strong numbers
        val numbersJson = loadAsset("strongs/strong_numbers.json")
        if (numbersJson != null) {
            val numbers = parseStrongNumbers(JSONArray(numbersJson))
            if (numbers.isNotEmpty()) {
                numbers.chunked(500).forEach { batch ->
                    strongDao.insertAllStrongNumbers(batch)
                }
                imported = true
                android.util.Log.i(TAG, "Imported ${numbers.size} Strong's numbers")
            }
        }

        // Import verse links
        val linksJson = loadAsset("strongs/verse_links.json")
        if (linksJson != null) {
            val links = parseVerseLinks(JSONArray(linksJson))
            if (links.isNotEmpty()) {
                // Insert in batches to avoid large transactions
                links.chunked(500).forEach { batch ->
                    strongDao.insertAllVerseLinks(batch)
                }
                imported = true
                android.util.Log.i(TAG, "Imported ${links.size} verse-strong links")
            }
        }
        return imported
    }

    private fun loadAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Asset not found: $path")
            null
        }
    }

    private fun parseStrongNumbers(arr: JSONArray): List<StrongNumberEntity> {
        val result = mutableListOf<StrongNumberEntity>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val number = obj.getString("number")
                result.add(
                    StrongNumberEntity(
                        number = number,
                        lemma = obj.optString("lemma", ""),
                        transliteration = obj.optString("transliteration", ""),
                        pronunciation = if (obj.has("pronunciation")) obj.getString("pronunciation") else null,
                        partOfSpeech = if (obj.has("partOfSpeech")) obj.getString("partOfSpeech") else null,
                        definition = obj.optString("definition", ""),
                        derivation = if (obj.has("derivation")) obj.getString("derivation") else null,
                        usageCount = obj.optInt("usageCount", 0),
                        // ponytail: G… = Greek, H… = Hebrew — don't mislabel gaps as Greek
                        language = obj.optString("language", null)
                            ?: if (number.startsWith("H")) "Hebrew" else "Greek"
                    )
                )
            } catch (_: Exception) {
                android.util.Log.w(TAG, "Skipping malformed Strong's row $i")
            }
        }
        return result
    }

    private fun parseVerseLinks(arr: JSONArray): List<VerseStrongLinkEntity> {
        val result = mutableListOf<VerseStrongLinkEntity>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                result.add(
                    VerseStrongLinkEntity(
                        verseId = obj.getLong("verseId"),
                        strongNumber = obj.getString("strongNumber"),
                        wordPosition = obj.getInt("wordPosition"),
                        originalWord = obj.optString("originalWord", ""),
                        transliteration = if (obj.has("transliteration")) obj.getString("transliteration") else null
                    )
                )
            } catch (_: Exception) {
                android.util.Log.w(TAG, "Skipping malformed verse-link row $i")
            }
        }
        return result
    }

    companion object {
        private const val TAG = "StrongImporter"
        private const val PREFS_NAME = "strongs_import"
        private const val PREFS_KEY_IMPORTED = "imported"
    }
}
