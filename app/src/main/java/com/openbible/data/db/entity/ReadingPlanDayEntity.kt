package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single day within a reading plan, containing one or more readings.
 * Readings stored as JSON array of {bookId, chapter} objects (one row per
 * chapter; a day with N chapters holds N objects).
 *
 * Example: "[{\"bookId\":1,\"chapter\":1},{\"bookId\":1,\"chapter\":2}]"
 *
 * (Older docs mention {bookId, chapterStart, chapterEnd}; the reader accepts
 * both shapes, but the seeder only ever writes {bookId, chapter}.)
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
