package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openbible.data.model.PenMode

/**
 * A user note (sermon, bible study, personal reflection).
 *
 * Supports three pen modes: plain text, ink strokes, or both.
 * Pen strokes stored as serialized JSON (SVG-compatible path data).
 * Verse links stored in a separate join table (NoteVerseLinkEntity).
 *
 * Indexed by notebookId for fast notebook listing, and by updatedAt
 * for "recent notes" queries.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index("notebookId"),
        Index("updatedAt"),
        Index("createdAt")
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notebookId: Long?,          // null = uncategorized
    val title: String,
    val contentText: String?,       // plain text content
    val penStrokes: String?,        // serialized ink strokes as JSON array
    val penMode: PenMode,           // TEXT, INK, or BOTH
    val createdAt: Long,
    val updatedAt: Long,
    val tags: String?,              // comma-separated
    val color: Int?,                // accent color for note card (ARGB)
    val pagesJson: String? = null, // unified multi-page model (v11+); supersedes penStrokes
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false // starred notes (v12+)
)
