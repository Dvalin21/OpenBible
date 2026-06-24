package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An image attached to a note.
 * Images are stored as local files; the database tracks the path and display order.
 * Cascading delete: removing a note removes all its images.
 */
@Entity(
    tableName = "note_images",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val filePath: String,           // content URI or local file path
    val caption: String?,
    val position: Int               // display order within the note
)
