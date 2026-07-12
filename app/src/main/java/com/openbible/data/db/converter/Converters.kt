package com.openbible.data.db.converter

import androidx.room.TypeConverter
import com.openbible.data.model.PenMode
import com.openbible.data.model.Testament

/**
 * Room type converters for enum and complex type serialization.
 *
 * Enums stored as strings (readable in DB inspector, safe against
 * enum reordering). Int storage would be more compact but less
 * debuggable — readability wins for a local-only database.
 */
class Converters {

    @TypeConverter
    fun testamentToString(value: Testament): String = value.name

    @TypeConverter
    fun stringToTestament(value: String): Testament =
        enumValues<Testament>().firstOrNull { it.name == value } ?: Testament.OLD

    @TypeConverter
    fun penModeToString(value: PenMode): String = value.name

    @TypeConverter
    fun stringToPenMode(value: String): PenMode =
        enumValues<PenMode>().firstOrNull { it.name == value } ?: PenMode.TEXT
}
