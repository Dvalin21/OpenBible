package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openbible.data.model.HighlightColor

/**
 * A highlighted verse with a color category.
 * One highlight per verse per color — simple, no conflicts.
 * Indexed by verseId for fast load of all highlights on a chapter.
 */
@Entity(
    tableName = "highlights",
    indices = [Index("verseId"), Index("color")]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val verseId: Long,
    val color: Int,                 // HighlightColor.ordinal
    val createdAt: Long
)
