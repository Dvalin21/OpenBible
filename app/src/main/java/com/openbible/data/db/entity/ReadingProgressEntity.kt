package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks completion progress for a single day within a reading plan.
 * One row per completed day — uncompleted days have no row (sparse tracking).
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = ReadingPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("planId")]
)
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val dayNumber: Int,
    val completed: Boolean,
    val completedAt: Long?          // epoch millis, null if not yet completed
)
