package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.BibleLocationEntity
import com.openbible.data.db.entity.VerseLocationLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Join result: a verse that mentions a specific location,
 * with resolved book abbreviation, chapter, verse number, and text.
 */
data class LocationVerseLink(
    val locationId: String,
    val verseId: Long,
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val abbreviation: String
)

/**
 * DAO for Bible geography locations.
 */
@Dao
interface LocationDao {

    // ── Locations ─────────────────────────────────────────────

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocation(id: String): BibleLocationEntity?

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationFlow(id: String): Flow<BibleLocationEntity?>

    /** Search by name, modern_name, description, significance. */
    @Query("""
        SELECT * FROM locations
        WHERE name LIKE '%' || :query || '%'
           OR modern_name LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR significance LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchLocations(query: String, limit: Int = 50): List<BibleLocationEntity>

    /** All locations, ordered by name. */
    @Query("SELECT * FROM locations ORDER BY name")
    fun getAllLocationsFlow(): Flow<List<BibleLocationEntity>>

    @Query("SELECT * FROM locations ORDER BY name")
    suspend fun getAllLocations(): List<BibleLocationEntity>

    /** Locations by category (city, region, mountain, etc.). */
    @Query("SELECT * FROM locations WHERE category = :category ORDER BY name")
    suspend fun getLocationsByCategory(category: String): List<BibleLocationEntity>

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun locationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLocations(locations: List<BibleLocationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: BibleLocationEntity)

    @Query("DELETE FROM locations")
    suspend fun clearLocations()

    // ── Verse-Location Links ──────────────────────────────────

    /** Get all locations mentioned in a specific verse. */
    @Query("""
        SELECT l.* FROM locations l
        INNER JOIN verse_location_links vll ON vll.locationId = l.id
        WHERE vll.verseId = :verseId
        ORDER BY l.name
    """)
    suspend fun getLocationsForVerse(verseId: Long): List<BibleLocationEntity>

    /** Get all verses that mention a specific location. */
    @Query("""
        SELECT vll.* FROM verse_location_links vll
        WHERE vll.locationId = :locationId
        ORDER BY vll.verseId
        LIMIT :limit
    """)
    suspend fun getVersesForLocation(locationId: String, limit: Int = 200): List<VerseLocationLinkEntity>

    /** Get all verses that mention a location, with resolved book/verse info (KJV). */
    @Query("""
        SELECT vll.locationId, vll.verseId,
               v.translationId, v.bookId, v.chapter, v.verse, v.text,
               b.abbreviation
        FROM verse_location_links vll
        INNER JOIN verses v ON v.id = vll.verseId
        INNER JOIN books b ON b.id = v.bookId AND b.translationId = v.translationId
        WHERE vll.locationId = :locationId
          AND v.translationId = 'kjv'
        ORDER BY v.bookId, v.chapter, v.verse
        LIMIT :limit
    """)
    suspend fun getLocationVerseLinks(locationId: String, limit: Int = 100): List<LocationVerseLink>

    @Query("SELECT COUNT(*) FROM verse_location_links")
    suspend fun verseLinkCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllVerseLinks(links: List<VerseLocationLinkEntity>)

    @Query("DELETE FROM verse_location_links")
    suspend fun clearVerseLinks()
}
