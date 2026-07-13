package com.openbible.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.openbible.data.model.Testament

/**
 * A book of the Bible within a specific translation.
 *
 * Composite primary key (translationId, id) — one row per book per translation.
 * Indexed by translationId for per-translation book listings.
 */
@Entity(
    tableName = "books",
    primaryKeys = ["translationId", "id"],
    indices = [Index("translationId")]
)
data class BookEntity(
    val translationId: String,
    @ColumnInfo(name = "id")
    val id: Int,                    // canonical book id (1-66), unique within a translation
    val name: String,               // "Genesis"
    val abbreviation: String,       // "Gen"
    val number: Int,                // canonical order (1-66)
    val chapterCount: Int,
    val testament: Testament,       // OLD or NEW
    val totalVerses: Int
)
