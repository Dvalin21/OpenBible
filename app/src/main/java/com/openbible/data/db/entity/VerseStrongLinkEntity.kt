package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Maps Strong's numbers to the verses where they appear.
 *
 * Each row links one Strong's number to one verse at a specific word position.
 * [wordPosition] is the 0-based index of the word in the verse that this
 * Strong's number applies to.
 *
 * Composite index covers the three query patterns:
 * - "which words in verse X have Strong's numbers?"
 * - "which verses contain Strong's number Y?"
 * - "where in the verse is Strong's number Y?"
 */
@Entity(
    tableName = "verse_strong_links",
    primaryKeys = ["verseId", "strongNumber", "wordPosition"],
    indices = [
        Index("strongNumber"),
        Index("verseId")
    ]
)
data class VerseStrongLinkEntity(
    val verseId: Long,
    val strongNumber: String,           // e.g. "G3056"
    val wordPosition: Int,              // 0-based index in the verse text
    val originalWord: String,           // the specific inflected form at this position
    val transliteration: String?        // romanization of the inflected form
)
