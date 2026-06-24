package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single verse of scripture.
 *
 * Unique constraint: (translationId, bookId, chapter, verse) ensures no
 * duplicate verses across translations. Indexed for fast chapter queries
 * and search across translation/book boundaries.
 *
 * This is the most-frequently-accessed table — indexes are critical.
 */
@Entity(
    tableName = "verses",
    indices = [
        Index("bookId"),
        Index("translationId"),
        Index("translationId", "bookId", "chapter"),
        Index("translationId", "bookId", "chapter", "verse", unique = true)
    ]
)
data class VerseEntity(
    @PrimaryKey
    val id: Long,
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)
