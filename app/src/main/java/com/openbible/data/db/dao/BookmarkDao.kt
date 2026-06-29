package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE verseId = :verseId")
    fun getBookmarksForVerse(verseId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE verseId = :verseId LIMIT 1")
    suspend fun getBookmarkForVerse(verseId: Long): BookmarkEntity?

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE verseId = :verseId")
    fun isVerseBookmarked(verseId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE verseId = :verseId")
    suspend fun deleteByVerseId(verseId: Long)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun getCount(): Flow<Int>

    @Query("SELECT * FROM bookmarks WHERE verseId IN (:verseIds)")
    fun observeBookmarksForVerses(verseIds: List<Long>): Flow<List<BookmarkEntity>>

    // -- Joined queries for UI display --

    /**
     * Returns all bookmarks with verse/book reference info.
     * Joins bookmarks → verses → books for display in the bookmarks list.
     */
    @Query("""
         SELECT b.id, b.verseId, b.label, b.createdAt, b.tags,
               v.translationId, v.bookId, v.chapter, v.verse AS verseNumber, v.text,
               bk.name AS bookName, bk.abbreviation AS bookAbbreviation
        FROM bookmarks b
        JOIN verses v ON b.verseId = v.id
        JOIN books bk ON v.translationId = bk.translationId AND v.bookId = bk.id
        ORDER BY b.createdAt DESC
    """)
    fun getBookmarksWithVerse(): Flow<List<BookmarkWithVerse>>
}
