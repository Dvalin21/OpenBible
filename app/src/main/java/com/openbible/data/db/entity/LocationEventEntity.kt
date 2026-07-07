package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A notable biblical event that occurred at a specific location.
 *
 * Each row describes one event (battle, miracle, prophecy, ministry act, etc.)
 * with a narrative description and Bible reference for navigation.
 *
 * Tapping the event opens the Bible reader to the reference passage.
 */
@Entity(
    tableName = "location_events",
    indices = [
        Index("locationId"),
        Index("era"),
        Index("category")
    ]
)
data class LocationEventEntity(
    @PrimaryKey
    val id: String,                          // e.g. "jerusalem_david_conquers"
    val locationId: String,                  // FK → locations.id
    val title: String,                       // "David Conquers Jerusalem"
    val description: String,                 // Narrative summary of the event
    val reference: String,                   // Human-readable "2 Samuel 5:6-10"
    val bookId: Int,                         // For "Read in Bible" navigation
    val chapter: Int,                        // For "Read in Bible" navigation
    val category: String,                    // "battle", "miracle", "ministry", "prophecy", "judgment", "birth", "death", "covenant", "revelation", "exile"
    val era: String,                         // "patriarchs", "exodus", "conquest", "judges", "monarchy", "divided", "exile", "restoration", "gospels", "acts", "revelation"
    val sortOrder: Int = 0                   // Display ordering within a location
)
