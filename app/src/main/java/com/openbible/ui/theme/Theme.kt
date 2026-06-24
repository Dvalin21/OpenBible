package com.openbible.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.openbible.data.model.ThemeMode

// ── Color Schemes ───────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightHighlight,
    secondary = LightSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    outline = LightDivider,
    outlineVariant = LightDivider.copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkHighlight,
    secondary = DarkSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    outline = DarkDivider,
    outlineVariant = DarkDivider.copy(alpha = 0.5f)
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaAccent,
    onPrimary = SepiaOnAccent,
    primaryContainer = SepiaHighlight,
    secondary = SepiaSecondary,
    background = SepiaBackground,
    onBackground = SepiaOnBackground,
    surface = SepiaSurface,
    onSurface = SepiaOnSurface,
    outline = SepiaDivider,
    outlineVariant = SepiaDivider.copy(alpha = 0.5f)
)

// ── Theme Composable ────────────────────────────────────────────

/**
 * OpenBible theme with three mode support: Light, Dark, and Sepia.
 *
 * The retro pixel theme is applied separately on the Bible reading
 * screen for devices 7" and larger — it overrides the base theme
 * with pixelated fonts, borders, and parchment backgrounds.
 */
@Composable
fun OpenBibleTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
    }

    // Set status bar colors to match background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                themeMode != ThemeMode.DARK
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenBibleTypography,
        content = content
    )
}
