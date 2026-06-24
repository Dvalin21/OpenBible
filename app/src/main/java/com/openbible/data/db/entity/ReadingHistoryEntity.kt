package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reading history for resume functionality.
 * One row per verse, tracking last read time and total read count.
 * Updated on each chapter open — the most recent verse in the chapter
 * becomes the resume point.
 */
@Entity(
    tableName = "reading_history",
    indices = [Index("verseId"), Index("lastReadAt")]
)
data class ReadingHistoryEntity(
    @PrimaryKey
    val verseId: Long,              // one row per verse — upsert on read
    val lastReadAt: Long,
    val readCount: Int
)
