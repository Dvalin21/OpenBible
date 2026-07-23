package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.BookEntity
import com.openbible.data.db.entity.VerseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for import-time write operations.
 * Separated from read-only BibleDao to keep read DAO clean and focused.
 */
@Dao
interface ImportDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVerses(verses: List<VerseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooks(books: List<BookEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM verses WHERE translationId = :translationId")
    suspend fun countVersesForTranslation(translationId: String): Long

    @Query("SELECT * FROM books WHERE translationId = 'kjv' ORDER BY number")
    fun getKjvBooks(): Flow<List<BookEntity>>
}