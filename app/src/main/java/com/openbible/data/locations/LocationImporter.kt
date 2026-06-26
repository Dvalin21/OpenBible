package com.openbible.data.locations

import android.content.Context
import com.openbible.data.db.dao.LocationDao
import com.openbible.data.db.entity.BibleLocationEntity
import com.openbible.data.db.entity.VerseLocationLinkEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports Bible geography data from JSON assets into Room.
 *
 * Runs once on first launch. Skips if data already present.
 *
 * Expected asset paths:
 *   assets/locations/locations.json           — array of BibleLocationEntity entries
 *   assets/locations/verse_links.json         — array of VerseLocationLinkEntity entries
 */
@Singleton
class LocationImporter @Inject constructor(
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
                android.util.Log.e(TAG, "Location import failed", e)
                false
            }
        }
    }

    private suspend fun doImport() {
        if (locationDao.locationCount() > 0) {
            android.util.Log.i(TAG, "Location data already present, skipping")
            return
        }

        val locationsJson = loadAsset("locations/locations.json")
        if (locationsJson != null) {
            val locations = parseLocations(JSONArray(locationsJson))
            if (locations.isNotEmpty()) {
                locationDao.insertAllLocations(locations)
                android.util.Log.i(TAG, "Imported ${locations.size} locations")
            }
        }

        val linksJson = loadAsset("locations/verse_links.json")
        if (linksJson != null) {
            val links = parseVerseLinks(JSONArray(linksJson))
            if (links.isNotEmpty()) {
                links.chunked(500).forEach { batch ->
                    locationDao.insertAllVerseLinks(batch)
                }
                android.util.Log.i(TAG, "Imported ${links.size} verse-location links")
            }
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

    private fun parseLocations(arr: JSONArray): List<BibleLocationEntity> {
        val result = mutableListOf<BibleLocationEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                BibleLocationEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    modernName = if (obj.has("modernName")) obj.getString("modernName") else null,
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    description = obj.getString("description"),
                    category = obj.getString("category"),
                    significance = if (obj.has("significance")) obj.getString("significance") else null
                )
            )
        }
        return result
    }

    private fun parseVerseLinks(arr: JSONArray): List<VerseLocationLinkEntity> {
        val result = mutableListOf<VerseLocationLinkEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                VerseLocationLinkEntity(
                    locationId = obj.getString("locationId"),
                    verseId = obj.getLong("verseId")
                )
            )
        }
        return result
    }

    companion object {
        private const val TAG = "LocationImporter"
        private const val PREFS_NAME = "locations_import"
        private const val PREFS_KEY_IMPORTED = "imported"
    }
}
