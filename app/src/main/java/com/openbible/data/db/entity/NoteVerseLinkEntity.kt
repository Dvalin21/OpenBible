package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Many-to-many join table linking notes to verses.
 * Created automatically when a user types a verse reference (e.g., "John 3:16")
 * in a note — the text is detected by regex and linked to the verse.
 *
 * Cascading deletes: removing either the note or the verse removes the link.
 */
@Entity(
    tableName = "note_verse_links",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VerseEntity::class,
            parentColumns = ["id"],
            childColumns = ["verseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("verseId")]
)
data class NoteVerseLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val verseId: Long
)
