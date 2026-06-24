package com.openbible.data.db.dao

/**
 * Search result: verse + book abbreviation, for grouped display.
 * Joins verses + books tables.
 */
data class SearchResult(
    val verseId: Long,
    val translationId: String,
    val bookId: Int,
    val bookAbbreviation: String,
    val chapter: Int,
    val verse: Int,
    val text: String
) {
    /** e.g. "Gen 1:1" */
    val citation: String get() = "$bookAbbreviation $chapter:$verse"
}
