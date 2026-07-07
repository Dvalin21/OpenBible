package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A parallel tradition from another culture that shares themes, events,
 * or motifs with a biblical account.
 *
 * Each row compares one biblical event (optionally linked to a location_event)
 * with a story from another culture's mythology, literature, or historical records.
 *
 * Examples:
 *   - Noah's Flood ←→ Gilgamesh Flood (Mesopotamian)
 *   - Creation ←→ Enuma Elish (Babylonian)
 *   - Moses in basket ←→ Sargon of Akkad birth legend (Akkadian)
 */
@Entity(
    tableName = "parallel_traditions",
    foreignKeys = [
        ForeignKey(
            entity = LocationEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("eventId"),
        Index("culture"),
        Index("category"),
        Index("biblicalBookId")
    ]
)
data class ParallelTraditionEntity(
    @PrimaryKey
    val id: String,                              // e.g. "noah_flood_gilgamesh"

    /** FK → location_events.id. Nullable — not every biblical event has a location event entry yet. */
    val eventId: String?,

    /** Human-readable Bible reference (e.g. "Genesis 6-9"). */
    val biblicalReference: String,

    /** Book ID for Bible navigation (1-66). */
    val biblicalBookId: Int,

    /** Chapter for Bible navigation. */
    val biblicalChapter: Int,

    /** Source culture (e.g. "Mesopotamian", "Egyptian", "Greek", "Ugaritic"). */
    val culture: String,

    /** The document or source name (e.g. "Epic of Gilgamesh", "Enuma Elish"). */
    val documentName: String,

    /** Short headline for the comparison (e.g. "The Flood: Noah and Gilgamesh"). */
    val title: String,

    /** Narrative description of the parallel — what happens in the other tradition. */
    val description: String,

    /** Key similarities between the biblical and parallel account (markdown-like text). */
    val similarities: String,

    /** Key differences between the two accounts. */
    val differences: String,

    /** Scholarly context, consensus level, or notable academic perspectives. */
    val scholarlyNote: String?,

    /** Approximate dating of the parallel source (e.g. "c. 2100-1200 BC"). */
    val dateRange: String?,

    /** Category of parallel (e.g. "flood", "creation", "birth", "law", "resurrection"). */
    val category: String,

    /** Display ordering within an event or category. */
    val sortOrder: Int = 0
)
