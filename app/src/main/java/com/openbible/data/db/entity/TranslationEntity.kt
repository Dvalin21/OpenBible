package com.openbible.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Bible translation (e.g., KJV, WEB, ASV).
 */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey
    val id: String,                 // "kjv", "web", "asv"
    val name: String,               // "King James Version"
    val abbreviation: String,       // "KJV"
    val language: String,           // "en"
    val copyright: String?,         // copyright notice (null = public domain)
    val isPublicDomain: Boolean,
    val isBundled: Boolean           // shipped with the APK
)
