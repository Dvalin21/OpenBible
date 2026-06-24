package com.openbible.data.db.dao

/**
 * Joined result: reading_history + verses + books.
 * Used for the HomeScreen "Continue Reading" resume card.
 */
data class ReadingHistoryWithVerse(
    val verseId: Long,
    val lastReadAt: Long,
    val readCount: Int,
    /** From verses table. */
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verseNumber: Int,
    val text: String,
    /** From books table. */
    val bookAbbreviation: String
) {
    /** e.g. "Gen 1" */
    val citation: String get() = "$bookAbbreviation $chapter"
}
