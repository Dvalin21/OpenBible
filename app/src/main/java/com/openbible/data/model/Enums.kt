package com.openbible.data.model

/**
 * Core enums for OpenBible.
 * Defined as top-level sealed/enum classes — no special cases, no magic values.
 */
enum class Testament {
    OLD,
    NEW
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SEPIA,
    AUTO_TIME;

    /**
     * Resolve AUTO_TIME to the effective theme based on current hour.
     * 6:00–16:59 → LIGHT, 17:00–19:59 → SEPIA, 20:00–5:59 → DARK.
     */
    fun resolve(hour: Int): ThemeMode = when (this) {
        AUTO_TIME -> when (hour) {
            in 6..16 -> LIGHT
            in 17..19 -> SEPIA
            else -> DARK
        }
        else -> this
    }
}

enum class PenMode {
    /** Plain text only — keyboard input */
    TEXT,
    /** Handwriting / pen strokes only */
    INK,
    /** Mixed text and ink blocks in the same note */
    BOTH
}

enum class HighlightColor(val argb: Long) {
    YELLOW(0xFFFFEB3B),
    GREEN(0xFF4CAF50),
    BLUE(0xFF2196F3),
    PINK(0xFFE91E63),
    ORANGE(0xFFFF9800);

    companion object {
        fun fromOrdinal(ordinal: Int): HighlightColor =
            entries.getOrElse(ordinal) { YELLOW }
    }
}
