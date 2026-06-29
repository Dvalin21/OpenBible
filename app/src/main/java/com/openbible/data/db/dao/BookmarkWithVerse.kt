package com.openbible.data.db.dao

/**
 * Join result: a bookmark with its verse reference info.
 *
 * Room maps this from the SQL JOIN between bookmarks, verses, and books.
 * Used in [BookmarkDao.getBookmarksWithVerse] for the bookmarks list display.
 */
data class BookmarkWithVerse(
    val id: Long,
    val verseId: Long,
    val label: String?,
    val createdAt: Long,
    val tags: String?,
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verseNumber: Int,
    val bookName: String,
    val bookAbbreviation: String,
    val text: String
) {
    /** Formatted citation like "John 3:16". */
    val citation: String get() = "$bookAbbreviation $chapter:$verseNumber"

    /** Full reference like "John 3:16 (KJV)". */
    val fullReference: String get() = "$citation (${translationId.uppercase()})"
}
