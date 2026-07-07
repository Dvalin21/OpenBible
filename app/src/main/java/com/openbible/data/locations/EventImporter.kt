package com.openbible.data.locations

import android.content.Context
import com.openbible.data.db.dao.LocationDao
import com.openbible.data.db.entity.LocationEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports biblical location events from JSON assets into Room.
 *
 * Runs once on first launch. Skips if data already present.
 *
 * Expected asset path:
 *   assets/locations/events.json — array of LocationEventEntity entries
 */
@Singleton
class EventImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao
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
                android.util.Log.e(TAG, "Event import failed", e)
                false
            }
        }
    }

    private suspend fun doImport() {
        if (locationDao.eventCount() > 0) {
            android.util.Log.i(TAG, "Event data already present, skipping")
            return
        }

        val json = loadAsset("locations/events.json") ?: return
        val events = parseEvents(JSONArray(json))
        if (events.isNotEmpty()) {
            events.chunked(100).forEach { batch ->
                locationDao.insertAllEvents(batch)
            }
            android.util.Log.i(TAG, "Imported ${events.size} location events")
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

    private fun parseEvents(arr: JSONArray): List<LocationEventEntity> {
        val result = mutableListOf<LocationEventEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                LocationEventEntity(
                    id = obj.getString("id"),
                    locationId = obj.getString("locationId"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    reference = obj.getString("reference"),
                    bookId = obj.getInt("bookId"),
                    chapter = obj.getInt("chapter"),
                    category = obj.getString("category"),
                    era = obj.getString("era"),
                    sortOrder = obj.optInt("sortOrder", 0)
                )
            )
        }
        return result
    }

    companion object {
        private const val TAG = "EventImporter"
        private const val PREFS_NAME = "events_import"
        private const val PREFS_KEY_IMPORTED = "imported"
    }
}
