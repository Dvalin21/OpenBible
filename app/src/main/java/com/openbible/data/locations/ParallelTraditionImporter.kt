package com.openbible.data.locations

import android.content.Context
import com.openbible.data.db.dao.ParallelTraditionDao
import com.openbible.data.db.entity.ParallelTraditionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports cross-cultural parallel traditions from JSON assets into Room.
 *
 * Runs once on first launch. Skips if data already present.
 *
 * Expected asset path:
 *   assets/locations/parallel_traditions.json
 */
@Singleton
class ParallelTraditionImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parallelTraditionDao: ParallelTraditionDao
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns true if import was completed (now or previously). */
    suspend fun importIfNeeded(): Boolean {
        if (prefs.getBoolean(PREFS_KEY_IMPORTED, false)) return true
        return withContext(Dispatchers.IO) {
            try {
                doImport()
                prefs.edit().putBoolean(PREFS_KEY_IMPORTED, true).apply()
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Parallel tradition import failed", e)
                false
            }
        }
    }

    private suspend fun doImport() {
        if (parallelTraditionDao.count() > 0) {
            android.util.Log.i(TAG, "Parallel tradition data already present, skipping")
            return
        }

        val json = loadAsset("locations/parallel_traditions.json") ?: return
        val entries = parseEntries(JSONArray(json))
        if (entries.isNotEmpty()) {
            entries.chunked(100).forEach { batch ->
                parallelTraditionDao.insertAll(batch)
            }
            android.util.Log.i(TAG, "Imported ${entries.size} parallel traditions")
        }
    }

    private fun loadAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Asset not found: $path")
            null
        }
    }

    private fun parseEntries(arr: JSONArray): List<ParallelTraditionEntity> {
        val result = mutableListOf<ParallelTraditionEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                ParallelTraditionEntity(
                    id = obj.getString("id"),
                    // ponytail: absent/empty eventId → null (optString returns "" when missing)
                    eventId = obj.optString("eventId").takeIf { it.isNotEmpty() },
                    biblicalReference = obj.getString("biblicalReference"),
                    biblicalBookId = obj.getInt("biblicalBookId"),
                    biblicalChapter = obj.getInt("biblicalChapter"),
                    culture = obj.getString("culture"),
                    documentName = obj.getString("documentName"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    similarities = obj.getString("similarities"),
                    differences = obj.getString("differences"),
                    scholarlyNote = obj.optString("scholarlyNote", null),
                    dateRange = obj.optString("dateRange", null),
                    category = obj.getString("category"),
                    sortOrder = obj.optInt("sortOrder", 0)
                )
            )
        }
        return result
    }

    companion object {
        private const val TAG = "ParallelTraditionImporter"
        private const val PREFS_NAME = "parallel_traditions_import"
        private const val PREFS_KEY_IMPORTED = "imported"
    }
}
