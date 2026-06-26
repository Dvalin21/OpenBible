package com.openbible.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A biblical location with geographic coordinates.
 *
 * Each row represents one place mentioned in the Bible
 * (city, region, mountain, river, sea, wilderness, etc.)
 * with its modern-day name and coordinates for mapping.
 */
@Entity(
    tableName = "locations",
    indices = [
        Index("category"),
        Index("modern_name")
    ]
)
data class BibleLocationEntity(
    @PrimaryKey
    val id: String,                          // e.g. "jerusalem", "mount_sinai"
    val name: String,                        // Biblical name (e.g. "Jerusalem")
    @ColumnInfo(name = "modern_name")
    val modernName: String?,                 // Modern name if different (e.g. "Al-Quds")
    val latitude: Double,
    val longitude: Double,
    val description: String,                 // Brief description / significance
    val category: String,                    // "city", "region", "mountain", "river", "sea", "wilderness"
    val significance: String?                // e.g. "Capital of Israel", "Birthplace of Jesus"
)
