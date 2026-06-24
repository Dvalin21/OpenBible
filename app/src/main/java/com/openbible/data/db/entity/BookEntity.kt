package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openbible.data.model.Testament

/**
 * A book of the Bible within a specific translation.
 *
 * Composite primary key: translationId + number (canonical order 1-66).
 * Indexed by translationId for per-translation book listings.
 */
@Entity(
    tableName = "books",
    indices = [Index("translationId")]
)
data class BookEntity(
    @PrimaryKey
    val id: Int,                    // composite: translationId + number
    val translationId: String,
    val name: String,               // "Genesis"
    val abbreviation: String,       // "Gen"
    val number: Int,                // canonical order (1-66)
    val chapterCount: Int,
    val testament: Testament,       // OLD or NEW
    val totalVerses: Int
)
