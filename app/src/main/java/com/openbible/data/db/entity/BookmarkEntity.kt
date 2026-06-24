package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-saved bookmark pointing to a verse.
 * Tags stored as comma-separated string — simple, searchable, no join table needed.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index("verseId"), Index("createdAt")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val verseId: Long,
    val label: String?,             // optional user-friendly label
    val createdAt: Long,            // epoch millis
    val tags: String?               // comma-separated tags, e.g., "favorite,faith"
)
