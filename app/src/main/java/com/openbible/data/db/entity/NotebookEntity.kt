package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A notebook for organizing notes (e.g., "Sermons", "Bible Study", "Devotions").
 * Simple grouping — no nested hierarchy, no folder trees.
 */
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int,                 // accent color (ARGB)
    val icon: String?,              // optional Material icon name
    val createdAt: Long
)
