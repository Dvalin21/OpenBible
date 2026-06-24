package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cross-reference from one verse to target book/chapter/verse(s).
 *
 * Source: Treasury of Scripture Knowledge (public domain).
 * Imported via openbible.info cross-reference dataset.
 */
@Entity(
    tableName = "cross_references",
    indices = [
        Index("fromVerseId"),
        Index("toBookId", "toChapter", "toVerseStart")
    ]
)
data class CrossReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromVerseId: Long,
    val toBookId: Int,
    val toChapter: Int,
    val toVerseStart: Int,
    val toVerseEnd: Int?,
    val relevance: Int = 0
)
