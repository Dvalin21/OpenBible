package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.ParallelTraditionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cross-cultural parallel traditions.
 */
@Dao
interface ParallelTraditionDao {

    /** Get all parallels for a specific biblical event (by location_event ID). */
    @Query("""
        SELECT * FROM parallel_traditions
        WHERE eventId = :eventId
        ORDER BY sortOrder, culture
    """)
    suspend fun getParallelsForEvent(eventId: String): List<ParallelTraditionEntity>

    /** Get all parallels for an event as a Flow. */
    @Query("""
        SELECT * FROM parallel_traditions
        WHERE eventId = :eventId
        ORDER BY sortOrder, culture
    """)
    fun getParallelsForEventFlow(eventId: String): Flow<List<ParallelTraditionEntity>>

    /** Get all parallels for a specific Bible passage (book, chapter). */
    @Query("""
        SELECT * FROM parallel_traditions
        WHERE biblicalBookId = :bookId AND biblicalChapter = :chapter
        ORDER BY sortOrder, culture
    """)
    suspend fun getParallelsForPassage(bookId: Int, chapter: Int): List<ParallelTraditionEntity>

    /** Get all parallels from a specific culture. */
    @Query("""
        SELECT * FROM parallel_traditions
        WHERE culture = :culture
        ORDER BY category, sortOrder
    """)
    suspend fun getParallelsByCulture(culture: String): List<ParallelTraditionEntity>

    /** Get all parallels in a specific category (flood, creation, etc.). */
    @Query("""
        SELECT * FROM parallel_traditions
        WHERE category = :category
        ORDER BY culture, sortOrder
    """)
    suspend fun getParallelsByCategory(category: String): List<ParallelTraditionEntity>

    /** All parallels, ordered. */
    @Query("SELECT * FROM parallel_traditions ORDER BY category, culture, sortOrder")
    suspend fun getAllParallels(): List<ParallelTraditionEntity>

    /** All parallels as Flow. */
    @Query("SELECT * FROM parallel_traditions ORDER BY category, culture, sortOrder")
    fun getAllParallelsFlow(): Flow<List<ParallelTraditionEntity>>

    /** Distinct cultures present in the data. */
    @Query("SELECT DISTINCT culture FROM parallel_traditions ORDER BY culture")
    suspend fun getCultures(): List<String>

    /** Distinct categories present in the data. */
    @Query("SELECT DISTINCT category FROM parallel_traditions ORDER BY category")
    suspend fun getCategories(): List<String>

    @Query("SELECT COUNT(*) FROM parallel_traditions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traditions: List<ParallelTraditionEntity>)

    @Query("DELETE FROM parallel_traditions")
    suspend fun clearAll()
}
