package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single day within a reading plan, containing one or more readings.
 * Readings stored as JSON array of {bookId, chapterStart, chapterEnd} objects.
 *
 * Example: "[{\"bookId\":1,\"chapterStart\":1,\"chapterEnd\":3}]"
 *
 * JSON is simple, queryable via LIKE in emergencies, and avoids another join table.
 * The reading plan's day count is bounded (max 365), so each plan has at most 365 rows.
 */
@Entity(
    tableName = "reading_plan_days",
    foreignKeys = [ForeignKey(
        entity = ReadingPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("planId")]
)
data class ReadingPlanDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val dayNumber: Int,
    val title: String?,             // optional day title (e.g., "The Creation")
    val readings: String            // JSON array of reading specs
)
