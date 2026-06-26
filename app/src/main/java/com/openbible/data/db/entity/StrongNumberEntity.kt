package com.openbible.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Strong's Concordance entry.
 *
 * Each row represents one Strong's number (e.g. G3056 for λόγος, H7225 for רֵאשִׁית).
 * The [number] is the canonical identifier combining prefix (G/H) with digits.
 *
 * Indexed by number for fast lookup, and by lemma + transliteration for search.
 */
@Entity(
    tableName = "strong_numbers",
    indices = [
        Index("lemma"),
        Index("transliteration")
    ]
)
data class StrongNumberEntity(
    @PrimaryKey
    val number: String,                 // e.g. "G3056", "H7225"
    val lemma: String,                  // original Greek/Hebrew word
    val transliteration: String,        // romanized pronunciation
    val pronunciation: String?,         // phonetic guide (nullable for uncommon entries)
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String?,          // e.g. "noun", "verb", "preposition"
    val definition: String,             // full definition / gloss
    val derivation: String?,            // etymology / derivation notes
    val usageCount: Int = 0,            // how many times this word appears
    val language: String                // "Greek" or "Hebrew"
)
