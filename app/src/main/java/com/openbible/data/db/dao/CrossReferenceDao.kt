package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Query

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
    /** First 100 chars of the target verse text (requested translation, KJV fallback). */
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

    /** Cross-references for KJV-anchored verse IDs, with target snippets in
     *  [translationId] (KJV fallback). Empty list in → empty list out. */
    @Query("""
        SELECT cr.fromVerseId, cr.toBookId, cr.toChapter, cr.toVerseStart,
               cr.toVerseEnd, cr.relevance,
               b.abbreviation AS toBookAbbreviation,
               COALESCE(v.text, vk.text, '') AS toVerseSnippet
        FROM cross_references cr
        INNER JOIN books b ON b.id = cr.toBookId AND b.translationId = :translationId
        LEFT JOIN verses v ON v.translationId = :translationId
            AND v.bookId = cr.toBookId
            AND v.chapter = cr.toChapter
            AND v.verse = cr.toVerseStart
        LEFT JOIN verses vk ON vk.translationId = 'kjv'
            AND vk.bookId = cr.toBookId
            AND vk.chapter = cr.toChapter
            AND vk.verse = cr.toVerseStart
        WHERE cr.fromVerseId IN (:verseIds)
        ORDER BY cr.fromVerseId, cr.relevance DESC
    """)
    suspend fun getCrossReferencesOnceInternal(verseIds: List<Long>, translationId: String): List<CrossReferenceDisplay>

    suspend fun getCrossReferencesOnce(
        verseIds: List<Long>,
        translationId: String = "kjv"
    ): List<CrossReferenceDisplay> =
        if (verseIds.isEmpty()) emptyList()
        else getCrossReferencesOnceInternal(verseIds, translationId)

    /** Count cross-references in the DB. */
    @Query("SELECT COUNT(*) FROM cross_references")
    suspend fun count(): Int
}
