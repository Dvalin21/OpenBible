package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.openbible.data.db.entity.BookEntity
import com.openbible.data.db.entity.TranslationEntity
import com.openbible.data.db.entity.VerseEntity
import kotlinx.coroutines.flow.Flow

/** Number of chapters in a book. */
data class BookChapterCount(
    val bookId: Int,
    val chapterCount: Int
)

/** Translations with FTS5 tables and shipped verse data. */
private val FTS_TRANSLATIONS = listOf("kjv", "web", "asv", "ylt", "bbe")

/**
 * DAO for read-only Bible text queries.
 * All Bible text is prepopulated — no insert/update/delete operations.
 */
@Dao
interface BibleDao {

    // -- Translations --

    @Query("SELECT * FROM translations WHERE isBundled = 1 AND id IN (SELECT DISTINCT translationId FROM books) ORDER BY id")
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

    @Query("SELECT id FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter ORDER BY verse LIMIT 1")
    suspend fun getFirstVerseId(translationId: String, bookId: Int, chapter: Int): Long?

    // -- Search (FTS5) --

    /** Raw query support for FTS5 searches returning [VerseEntity]. */
    @RawQuery(observedEntities = [VerseEntity::class])
    suspend fun searchFtsRaw(query: SupportSQLiteQuery): List<VerseEntity>

    /** Raw query support for FTS5 searches returning [SearchResult] with book abbreviations. */
    @RawQuery(observedEntities = [VerseEntity::class])
    suspend fun searchFtsWithBookRaw(query: SupportSQLiteQuery): List<SearchResult>

    /** Search verses in a single translation using FTS5 full-text search. */
    suspend fun searchVerses(
        translationId: String,
        query: String,
        limit: Int = 100
    ): List<VerseEntity> {
        if (translationId !in FTS_TRANSLATIONS) return emptyList()
        val q = sanitizeFtsQuery(query)
        if (q.isEmpty()) return emptyList()
        val sql = """
            SELECT v.* FROM verses v
            INNER JOIN verses_fts_${translationId} fts ON v.id = fts.rowid
            WHERE fts MATCH ?
            ORDER BY fts.rank
            LIMIT ?
        """.trimIndent()
        return searchFtsRaw(SimpleSQLiteQuery(sql, arrayOf(q, limit)))
    }

    /** Search verses in a single book using FTS5. */
    suspend fun searchVersesInBook(
        translationId: String,
        bookId: Int,
        query: String,
        limit: Int = 50
    ): List<VerseEntity> {
        if (translationId !in FTS_TRANSLATIONS) return emptyList()
        val q = sanitizeFtsQuery(query)
        if (q.isEmpty()) return emptyList()
        val sql = """
            SELECT v.* FROM verses v
            INNER JOIN verses_fts_${translationId} fts ON v.id = fts.rowid
            WHERE fts MATCH ? AND v.bookId = ?
            ORDER BY fts.rank
            LIMIT ?
        """.trimIndent()
        return searchFtsRaw(SimpleSQLiteQuery(sql, arrayOf(q, bookId, limit)))
    }

    /** Search with book abbreviation and verse info using FTS5. */
    suspend fun searchVersesWithBook(
        translationId: String,
        query: String,
        limit: Int = 100
    ): List<SearchResult> {
        if (translationId !in FTS_TRANSLATIONS) return emptyList()
        val q = sanitizeFtsQuery(query)
        if (q.isEmpty()) return emptyList()
        val sql = """
            SELECT v.id AS verseId, v.translationId, v.bookId,
                   b.abbreviation AS bookAbbreviation,
                   v.chapter, v.verse, v.text
            FROM verses v
            INNER JOIN books b ON b.id = v.bookId AND b.translationId = v.translationId
            INNER JOIN verses_fts_${translationId} fts ON v.id = fts.rowid
            WHERE fts MATCH ?
            ORDER BY fts.rank
            LIMIT ?
        """.trimIndent()
        return searchFtsWithBookRaw(SimpleSQLiteQuery(sql, arrayOf(q, limit)))
    }

    /** Search all bundled translations using FTS5 via UNION ALL. */
    suspend fun searchAllTranslations(
        query: String,
        limit: Int = 100
    ): List<SearchResult> {
        val q = sanitizeFtsQuery(query)
        if (q.isEmpty()) return emptyList()
        val parts = FTS_TRANSLATIONS.mapIndexed { i, t ->
            val param = "?"  // each translation gets its own ? bind
            """
            SELECT v.id AS verseId, v.translationId, v.bookId,
                   b.abbreviation AS bookAbbreviation,
                   v.chapter, v.verse, v.text
            FROM verses v
            INNER JOIN books b ON b.id = v.bookId AND b.translationId = v.translationId
            INNER JOIN verses_fts_${t} fts ON v.id = fts.rowid
            WHERE fts MATCH ?
            """.trimIndent()
        }
        val sql = """
            SELECT * FROM (${parts.joinToString("\nUNION ALL\n")})
            ORDER BY translationId, bookId, chapter, verse
            LIMIT ?
        """.trimIndent()
        // bind the query arg for each UNION branch + limit at end
        val args = arrayOfNulls<Any>(FTS_TRANSLATIONS.size + 1)
        for (i in FTS_TRANSLATIONS.indices) args[i] = q
        args[FTS_TRANSLATIONS.size] = limit
        return searchFtsWithBookRaw(SimpleSQLiteQuery(sql, args))
    }

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

    companion object {
        /** Strip FTS5 special characters from a user query string.
         *  FTS5 MATCH syntax uses: " * ( ) + - | ^ ! ~ < > = [ ] { }
         *  We remove these so the input becomes space-separated words (FTS5 implicit AND). */
        fun sanitizeFtsQuery(query: String): String {
            return query.replace(Regex("""['"*()+\-|^!<>=~\[\]{}]"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
