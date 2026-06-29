package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.openbible.data.db.entity.BookEntity
import com.openbible.data.db.entity.TranslationEntity
import com.openbible.data.db.entity.VerseEntity
import kotlinx.coroutines.flow.Flow

/** Number of chapters in a book. */
data class BookChapterCount(
    val bookId: Int,
    val chapterCount: Int
)

/**
 * DAO for read-only Bible text queries.
 * All Bible text is prepopulated — no insert/update/delete operations.
 */
@Dao
interface BibleDao {

    // -- Translations --

    @Query("SELECT * FROM translations WHERE isBundled = 1 ORDER BY id")
    fun getBundledTranslations(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations ORDER BY id")
    fun getAllTranslations(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE id = :translationId")
    suspend fun getTranslation(translationId: String): TranslationEntity?

    // -- Books --

    @Query("SELECT * FROM books WHERE translationId = :translationId ORDER BY number")
    fun getBooks(translationId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: Int): BookEntity?

    @Query("SELECT * FROM books WHERE translationId = :translationId AND abbreviation = :abbr")
    suspend fun getBookByAbbreviation(translationId: String, abbr: String): BookEntity?

    // -- Chapters --

    @Query("SELECT DISTINCT chapter FROM verses WHERE translationId = :translationId AND bookId = :bookId ORDER BY chapter")
    fun getChapters(translationId: String, bookId: Int): Flow<List<Int>>

    // -- Verses --

    @Query("SELECT * FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter ORDER BY verse")
    fun getVerses(translationId: String, bookId: Int, chapter: Int): Flow<List<VerseEntity>>

    @Query("SELECT * FROM verses WHERE id = :verseId")
    suspend fun getVerse(verseId: Long): VerseEntity?

    @Query("SELECT * FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun getVerseByRef(translationId: String, bookId: Int, chapter: Int, verse: Int): VerseEntity?

    // -- Search --

    @Query("""
        SELECT * FROM verses 
        WHERE translationId = :translationId 
          AND text LIKE '%' || :query || '%' 
        ORDER BY bookId, chapter, verse 
        LIMIT :limit
    """)
    suspend fun searchVerses(
        translationId: String,
        query: String,
        limit: Int = 100
    ): List<VerseEntity>

    @Query("""
        SELECT v.* FROM verses v
        WHERE v.translationId = :translationId 
          AND v.text LIKE '%' || :query || '%'
          AND v.bookId = :bookId
        ORDER BY v.chapter, v.verse 
        LIMIT :limit
    """)
    suspend fun searchVersesInBook(
        translationId: String,
        bookId: Int,
        query: String,
        limit: Int = 50
    ): List<VerseEntity>

    @Query("""
        SELECT v.id AS verseId, v.translationId, v.bookId,
               b.abbreviation AS bookAbbreviation,
               v.chapter, v.verse, v.text
        FROM verses v
        INNER JOIN books b ON b.id = v.bookId
        WHERE v.translationId = :translationId
          AND v.text LIKE '%' || :query || '%'
        ORDER BY b.number, v.chapter, v.verse
        LIMIT :limit
    """)
    suspend fun searchVersesWithBook(
        translationId: String,
        query: String,
        limit: Int = 100
    ): List<SearchResult>

    @Query("""
        SELECT v.id AS verseId, v.translationId, v.bookId,
               b.abbreviation AS bookAbbreviation,
               v.chapter, v.verse, v.text
        FROM verses v
        INNER JOIN books b ON b.id = v.bookId
        WHERE v.text LIKE '%' || :query || '%'
        ORDER BY v.translationId, b.number, v.chapter, v.verse
        LIMIT :limit
    """)
    suspend fun searchAllTranslations(
        query: String,
        limit: Int = 100
    ): List<SearchResult>

    // -- Batch lookups --

    @Query("SELECT * FROM verses WHERE id IN (:verseIds)")
    suspend fun getVersesByIds(verseIds: List<Long>): List<VerseEntity>

    @Query("SELECT * FROM books WHERE translationId = :translationId AND id IN (:bookIds)")
    suspend fun getBooksByIds(translationId: String, bookIds: List<Int>): List<BookEntity>

    // -- Random verse (for daily verse notification) --

    @Query("SELECT * FROM verses WHERE translationId = :translationId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomVerse(translationId: String): VerseEntity?

    // -- Chapter counts per book (for reading plan seeding) --

    @Query("SELECT bookId, MAX(chapter) AS chapterCount FROM verses WHERE translationId = :translationId GROUP BY bookId ORDER BY bookId")
    suspend fun getBookChapterCounts(translationId: String): List<BookChapterCount>

    // -- Cross-references (inline) --

    @Query("""
        SELECT * FROM verses 
        WHERE translationId = :translationId 
          AND bookId = :bookId 
          AND chapter = :chapter 
          AND verse IN (:verses)
        ORDER BY verse
    """)
    suspend fun getVersesByRefs(
        translationId: String,
        bookId: Int,
        chapter: Int,
        verses: List<Int>
    ): List<VerseEntity>
}
