package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Join result for displaying a cross-reference in the Bible reader.
 */
data class CrossReferenceDisplay(
    val fromVerseId: Long,
    val toBookId: Int,
    val toChapter: Int,
    val toVerseStart: Int,
    val toVerseEnd: Int?,
    val relevance: Int,
    /** From books table. */
    val toBookAbbreviation: String,
    /** First 100 chars of the target verse text (KJV). */
    val toVerseSnippet: String
) {
    /** e.g. "Matt 5:7–9" or "Matt 5:7" */
    val citation: String get() {
        val verseEnd = toVerseEnd
        return if (verseEnd != null && verseEnd != toVerseStart) {
            "$toBookAbbreviation $toChapter:$toVerseStart\u2013$verseEnd"
        } else {
            "$toBookAbbreviation $toChapter:$toVerseStart"
        }
    }
}

@Dao
interface CrossReferenceDao {

    /** Get all cross-references originating from the given KJV verse IDs. */
    @Query("""
        SELECT cr.fromVerseId, cr.toBookId, cr.toChapter, cr.toVerseStart,
               cr.toVerseEnd, cr.relevance,
               b.abbreviation AS toBookAbbreviation,
               COALESCE(v.text, '') AS toVerseSnippet
        FROM cross_references cr
        INNER JOIN books b ON b.id = cr.toBookId
        LEFT JOIN verses v ON v.translationId = 'kjv'
            AND v.bookId = cr.toBookId
            AND v.chapter = cr.toChapter
            AND v.verse = cr.toVerseStart
        WHERE cr.fromVerseId IN (:verseIds)
        ORDER BY cr.fromVerseId, cr.relevance DESC
    """)
    fun getCrossReferences(verseIds: List<Long>): Flow<List<CrossReferenceDisplay>>

    /** Same query as suspend (for non-reactive use). */
    @Query("""
        SELECT cr.fromVerseId, cr.toBookId, cr.toChapter, cr.toVerseStart,
               cr.toVerseEnd, cr.relevance,
               b.abbreviation AS toBookAbbreviation,
               COALESCE(v.text, '') AS toVerseSnippet
        FROM cross_references cr
        INNER JOIN books b ON b.id = cr.toBookId
        LEFT JOIN verses v ON v.translationId = 'kjv'
            AND v.bookId = cr.toBookId
            AND v.chapter = cr.toChapter
            AND v.verse = cr.toVerseStart
        WHERE cr.fromVerseId IN (:verseIds)
        ORDER BY cr.fromVerseId, cr.relevance DESC
    """)
    suspend fun getCrossReferencesOnce(verseIds: List<Long>): List<CrossReferenceDisplay>

    /** Count cross-references in the DB. */
    @Query("SELECT COUNT(*) FROM cross_references")
    suspend fun count(): Int
}
