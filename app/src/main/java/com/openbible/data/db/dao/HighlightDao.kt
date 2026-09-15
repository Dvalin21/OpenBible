package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE verseId = :verseId")
    fun getHighlightsForVerse(verseId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE verseId IN (:verseIds)")
    suspend fun getHighlightsForVersesInternal(verseIds: List<Long>): List<HighlightEntity>

    suspend fun getHighlightsForVerses(verseIds: List<Long>): List<HighlightEntity> =
        if (verseIds.isEmpty()) emptyList() else getHighlightsForVersesInternal(verseIds)

    @Query("SELECT * FROM highlights WHERE verseId IN (:verseIds)")
    fun observeHighlightsForVersesInternal(verseIds: List<Long>): Flow<List<HighlightEntity>>

    fun observeHighlightsForVerses(verseIds: List<Long>): Flow<List<HighlightEntity>> =
        if (verseIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else observeHighlightsForVersesInternal(verseIds)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity): Long

    @Delete
    suspend fun delete(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE verseId = :verseId AND color = :color")
    suspend fun deleteByVerseAndColor(verseId: Long, color: Int)

    @Query("DELETE FROM highlights WHERE verseId = :verseId")
    suspend fun deleteAllForVerse(verseId: Long)
}
