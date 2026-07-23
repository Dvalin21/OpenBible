package com.openbible.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light Mode ──────────────────────────────────────────────────

val LightBackground = Color(0xFFF5F0E8)      // warm paper/cream
val LightOnBackground = Color(0xFF1A1A2E)     // near-black
val LightSurface = Color(0xFFFFF8F0)          // slightly lighter cream
val LightOnSurface = Color(0xFF1A1A2E)
val LightAccent = Color(0xFF8B4513)           // saddle brown
val LightOnAccent = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFFA0A0A0)        // muted gray
val LightHighlight = Color(0xFFE8D5B7)        // soft gold
val LightLink = Color(0xFF2E5E8E)             // muted blue
val LightDivider = Color(0xFFD4C9B0)          // subtle warm gray

// ── Dark Mode ───────────────────────────────────────────────────

val DarkBackground = Color(0xFF1C1C1E)        // deep neutral
val DarkOnBackground = Color(0xFFE5E0D8)      // warm off-white
val DarkSurface = Color(0xFF2C2C2E)           // cards, menus
val DarkOnSurface = Color(0xFFE5E0D8)
val DarkAccent = Color(0xFFC9954E)            // warm gold
val DarkOnAccent = Color(0xFF1C1C1E)
val DarkSecondary = Color(0xFF8E8E93)         // muted on dark
val DarkHighlight = Color(0xFF3D3525)         // muted gold highlight
val DarkLink = Color(0xFF6AA0D6)              // readable blue on dark
val DarkDivider = Color(0xFF38383A)

// ── Sepia Mode ──────────────────────────────────────────────────

val SepiaBackground = Color(0xFFE8DCC8)       // aged paper
val SepiaOnBackground = Color(0xFF3B3228)     // dark brown
val SepiaSurface = Color(0xFFF0E6D4)
val SepiaOnSurface = Color(0xFF3B3228)
val SepiaAccent = Color(0xFFA0522D)           // sienna
val SepiaOnAccent = Color(0xFFFFFFFF)
val SepiaSecondary = Color(0xFF8B7D6B)
val SepiaHighlight = Color(0xFFD4C4A0)
val SepiaLink = Color(0xFF5B3E2B)
val SepiaDivider = Color(0xFFC8B898)

// ── Highlight Colors ────────────────────────────────────────────

object HighlightColors {
    val Yellow = Color(0xFFFFEB3B).copy(alpha = 0.35f)
    val Green = Color(0xFF4CAF50).copy(alpha = 0.35f)
    val Blue = Color(0xFF2196F3).copy(alpha = 0.30f)
    val Pink = Color(0xFFE91E63).copy(alpha = 0.30f)
    val Orange = Color(0xFFFF9800).copy(alpha = 0.35f)
}
