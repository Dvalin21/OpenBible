package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Links Bible locations to the verses where they are mentioned.
 *
 * Each row maps one location to one verse that mentions it.
 * Enables queries like "which verses mention Jerusalem?"
 * or "which locations appear in Genesis 1?".
 */
@Entity(
    tableName = "verse_location_links",
    primaryKeys = ["locationId", "verseId"],
    indices = [
        Index("verseId")
    ]
)
data class VerseLocationLinkEntity(
    val locationId: String,   // FK → locations.id
    val verseId: Long         // FK → verses.id
)
