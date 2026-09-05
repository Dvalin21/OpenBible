package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openbible.data.db.entity.StrongNumberEntity
import com.openbible.data.db.entity.VerseStrongLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * ASCII-fold + lowercase for accent-insensitive matching.
 * "agápē" → "agape", "ἀγάπη" → "αγαπη", "ʼâhab" → "ahab". Single source
 * for the write path (StrongImporter) and the read path below.
 *
 * NFD + Mn-strip handles accents/breathings/points; the leftovers below are
 * the only non-folding chars in the lexicon (verified by inventory):
 * apostrophe-likes users never type, plus superscript ᵉ/ˢ.
 */
fun normalizeForSearch(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[ʼʻ'’]"), "")
        .replace("ᵉ", "e")
        .replace("ˢ", "s")

/** Escape LIKE wildcards so "100%" searches literally. */
fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * Canonical Strong's number: uppercase letter, no spaces, no leading zeros.
 * "g 026" → "G26", "H157" → "H157". Null when not number-shaped.
 */
fun normalizeStrongNumber(q: String): String? {
    val c = q.replace("\\s".toRegex(), "").uppercase()
    val m = Regex("^([GH])0*(\\d+)$").matchEntire(c) ?: return null
    return m.groupValues[1] + m.groupValues[2]
}

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
        WHERE number = :exact COLLATE NOCASE
           OR number LIKE (:exact || '%') COLLATE NOCASE
           OR number LIKE ('%' || :query || '%') ESCAPE '\' COLLATE NOCASE
           OR lemma LIKE ('%' || :query || '%') ESCAPE '\' COLLATE NOCASE
           OR transliteration LIKE ('%' || :query || '%') ESCAPE '\' COLLATE NOCASE
           OR lemmaNorm LIKE ('%' || :norm || '%') ESCAPE '\'
           OR transliterationNorm LIKE ('%' || :norm || '%') ESCAPE '\'
           OR definition LIKE ('%' || :query || '%') ESCAPE '\' COLLATE NOCASE
        ORDER BY
            CASE WHEN number = :exact COLLATE NOCASE THEN 0
                 WHEN number LIKE (:exact || '%') COLLATE NOCASE THEN 1
                 WHEN lemmaNorm = :norm OR transliterationNorm = :norm THEN 2
                 WHEN lemmaNorm LIKE (:norm || '%') OR transliterationNorm LIKE (:norm || '%') THEN 3
                 ELSE 4 END,
            number
        LIMIT :limit
    """)
    suspend fun searchStrongNumbersInternal(
        query: String,
        norm: String,
        exact: String,
        limit: Int
    ): List<StrongNumberEntity>

    /**
     * Search by number ("G26", "g 026"), word ("love", "agape") or definition.
     * Accent-insensitive via the Norm columns; exact number always ranks first.
     */
    suspend fun searchStrongNumbers(query: String, limit: Int = 50): List<StrongNumberEntity> {
        val exact = escapeLike(normalizeStrongNumber(query) ?: query.trim())
        return searchStrongNumbersInternal(escapeLike(query.trim()), normalizeForSearch(query), exact, limit)
    }

    /** Prefix match on number ("G26" → G26, G260…). Caller passes raw text. */
    @Query("""
        SELECT * FROM strong_numbers
        WHERE number LIKE (:prefix || '%') ESCAPE '\' COLLATE NOCASE
        ORDER BY length(number), number
        LIMIT :limit
    """)
    suspend fun searchStrongNumbersByPrefixRaw(prefix: String, limit: Int): List<StrongNumberEntity>

    suspend fun searchStrongNumbersByPrefix(prefix: String, limit: Int = 20): List<StrongNumberEntity> {
        val norm = normalizeStrongNumber(prefix) ?: prefix.trim()
        return searchStrongNumbersByPrefixRaw(escapeLike(norm), limit)
    }

    @Query("SELECT * FROM strong_numbers ORDER BY number LIMIT :limit OFFSET :offset")
    suspend fun browseStrongNumbers(limit: Int = 100, offset: Int = 0): List<StrongNumberEntity>

    @Query("SELECT COUNT(*) FROM strong_numbers")
    suspend fun strongNumberCount(): Int

    @Query("SELECT COUNT(*) FROM strong_numbers WHERE lemmaNorm = '' OR transliterationNorm = ''")
    suspend fun countMissingNorms(): Int

    @Query("SELECT * FROM strong_numbers WHERE lemmaNorm = '' OR transliterationNorm = '' LIMIT :limit OFFSET :offset")
    suspend fun getMissingNormsBatch(limit: Int, offset: Int): List<StrongNumberEntity>

    @Query("UPDATE strong_numbers SET lemmaNorm = :lemmaNorm, transliterationNorm = :transliterationNorm WHERE number = :number")
    suspend fun updateNorms(number: String, lemmaNorm: String, transliterationNorm: String)

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
