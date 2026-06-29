package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.StrongNumberEntity
import com.openbible.data.db.entity.VerseStrongLinkEntity
import kotlinx.coroutines.flow.Flow

/** A verse occurrence with resolved book name, chapter, verse number, and text. */
data class VerseLinkWithReference(
    val verseId: Long,
    val strongNumber: String,
    val wordPosition: Int,
    val originalWord: String,
    val transliteration: String?,
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val abbreviation: String
)

/**
 * DAO for Strong's Concordance data.
 *
 * Two tables: strong_numbers (the lexicon entries) and
 * verse_strong_links (which words in which verses map to which numbers).
 */
@Dao
interface StrongDao {

    // ── Strong's Numbers ─────────────────────────────────────────

    @Query("SELECT * FROM strong_numbers WHERE number = :number")
    suspend fun getStrongNumber(number: String): StrongNumberEntity?

    @Query("SELECT * FROM strong_numbers WHERE number = :number")
    fun getStrongNumberFlow(number: String): Flow<StrongNumberEntity?>

    @Query("""
        SELECT * FROM strong_numbers 
        WHERE number LIKE '%' || :query || '%' 
           OR lemma LIKE '%' || :query || '%' 
           OR transliteration LIKE '%' || :query || '%' 
           OR definition LIKE '%' || :query || '%'
        ORDER BY number
        LIMIT :limit
    """)
    suspend fun searchStrongNumbers(query: String, limit: Int = 50): List<StrongNumberEntity>

    @Query("SELECT * FROM strong_numbers ORDER BY number LIMIT :limit OFFSET :offset")
    suspend fun browseStrongNumbers(limit: Int = 100, offset: Int = 0): List<StrongNumberEntity>

    @Query("SELECT COUNT(*) FROM strong_numbers")
    suspend fun strongNumberCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStrongNumbers(numbers: List<StrongNumberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrongNumber(number: StrongNumberEntity)

    // ── Verse-Strong Links ───────────────────────────────────────

    @Query("""
        SELECT vsl.* FROM verse_strong_links vsl
        WHERE vsl.verseId = :verseId
        ORDER BY vsl.wordPosition
    """)
    suspend fun getLinksForVerse(verseId: Long): List<VerseStrongLinkEntity>

    @Query("""
        SELECT vsl.* FROM verse_strong_links vsl
        WHERE vsl.strongNumber = :strongNumber
        ORDER BY vsl.verseId
        LIMIT :limit
    """)
    suspend fun getVersesForStrongNumber(strongNumber: String, limit: Int = 100): List<VerseStrongLinkEntity>

    @Query("""
        SELECT vsl.verseId, vsl.strongNumber, vsl.wordPosition, vsl.originalWord, vsl.transliteration,
               v.translationId, v.bookId, v.chapter, v.verse, v.text,
               b.abbreviation
        FROM verse_strong_links vsl
        INNER JOIN verses v ON v.id = vsl.verseId
        INNER JOIN books b ON b.id = v.bookId AND b.translationId = v.translationId
        WHERE vsl.strongNumber = :strongNumber
        ORDER BY v.bookId, v.chapter, v.verse
        LIMIT :limit
    """)
    suspend fun getVerseLinksWithReference(strongNumber: String, limit: Int = 100): List<VerseLinkWithReference>

    @Query("""
        SELECT sn.* FROM strong_numbers sn
        INNER JOIN verse_strong_links vsl ON vsl.strongNumber = sn.number
        WHERE vsl.verseId = :verseId
        ORDER BY vsl.wordPosition
    """)
    suspend fun getStrongNumbersForVerse(verseId: Long): List<StrongNumberEntity>

    @Query("SELECT COUNT(*) FROM verse_strong_links")
    suspend fun verseLinkCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllVerseLinks(links: List<VerseStrongLinkEntity>)

    // ── Bulk import helpers ──────────────────────────────────────

    @Query("DELETE FROM strong_numbers")
    suspend fun clearStrongNumbers()

    @Query("DELETE FROM verse_strong_links")
    suspend fun clearVerseLinks()
}
