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
    SEPIA
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
