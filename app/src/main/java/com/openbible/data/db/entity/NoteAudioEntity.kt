package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An audio memo attached to a note (voice recording).
 * Files are stored in app-private storage; the row tracks the path + duration.
 * Cascading delete: removing the parent note removes its audio rows.
 */
@Entity(
    tableName = "note_audio",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteAudioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val filePath: String,           // app-private .m4a path
    @androidx.room.ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0,
    val createdAt: Long
)
