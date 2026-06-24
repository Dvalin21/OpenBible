package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE verseId = :verseId")
    suspend fun getHistoryForVerse(verseId: Long): ReadingHistoryEntity?

    @Query("""
        SELECT MAX(lastReadAt) FROM reading_history 
        WHERE verseId IN (
            SELECT id FROM verses WHERE translationId = :translationId AND bookId = :bookId
        )
    """)
    suspend fun getLastReadTime(translationId: String, bookId: Int): Long?

    @Query("""
        SELECT verseId FROM reading_history 
        WHERE verseId IN (
            SELECT id FROM verses WHERE translationId = :translationId AND bookId = :bookId
        )
        ORDER BY lastReadAt DESC LIMIT 1
    """)
    suspend fun getLastReadVerseId(translationId: String, bookId: Int): Long?

    /** Most recent reading entry with full verse + book info. */
    @Query("""
        SELECT rh.verseId, rh.lastReadAt, rh.readCount,
               v.translationId, v.bookId, v.chapter, v.verse AS verseNumber, v.text,
               b.abbreviation AS bookAbbreviation
        FROM reading_history rh
        INNER JOIN verses v ON v.id = rh.verseId
        INNER JOIN books b ON b.id = v.bookId
        ORDER BY rh.lastReadAt DESC
        LIMIT 1
    """)
    fun getMostRecentReading(): Flow<ReadingHistoryWithVerse?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ReadingHistoryEntity)
}
