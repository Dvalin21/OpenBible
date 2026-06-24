package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reading plan (e.g., "90-Day New Testament", "1-Year Bible").
 * Prebuilt plans ship with the app; user-created plans can be added.
 */
@Entity(tableName = "reading_plans")
data class ReadingPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val durationDays: Int,
    val isPrebuilt: Boolean         // shipped with app vs user-created
)
