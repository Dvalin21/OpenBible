package com.openbible.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Retro pixel Bible theme configuration.
 *
 * Activated on devices with screen width >= 600dp (7" tablets and larger).
 * Provides pixel font sizes, border widths, ornamental colors, and page
 * dimensions for the retro Bible book appearance.
 */

// Minimum screen width to enable retro theme (7" tablets in portrait)
const val MIN_TABLET_WIDTH_DP = 600

data class RetroPixelConfig(
    val enabled: Boolean,
    val isTablet: Boolean,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    // Pixel font sizes
    val chapterNumberSize: Dp = 24.dp,
    val verseNumberSize: Dp = 10.dp,
    val bibleTextSize: Dp = 14.dp,
    val dropCapSize: Dp = 36.dp,
    // Page dimensions
    val pagePaddingHorizontal: Dp = 24.dp,
    val pagePaddingVertical: Dp = 32.dp,
    val pageCornerRadius: Dp = 4.dp,
    // Borders
    val borderWidth: Dp = 2.dp,
    val ornamentHeight: Dp = 16.dp,
    // Page edge shadow
    val pageShadowWidth: Dp = 12.dp
)

val LocalRetroPixel = staticCompositionLocalOf { RetroPixelConfig(enabled = false, isTablet = false, screenWidthDp = 0.dp, screenHeightDp = 0.dp) }

/**
 * Determines if the retro pixel Bible theme should be enabled based on
 * the current device configuration.
 *
 * Enabled when:
 * - Screen width is >= 600dp (7" and larger)
 * - OR the user explicitly enables it (future setting)
 */
@Composable
fun rememberRetroPixelConfig(enabled: Boolean = true): RetroPixelConfig {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val screenHeightDp = config.screenHeightDp.dp
    val isTablet = screenWidthDp >= MIN_TABLET_WIDTH_DP.dp
    val orientation = config.orientation

    val retroEnabled = enabled && isTablet

    return RetroPixelConfig(
        enabled = retroEnabled,
        isTablet = isTablet,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        // Adjust padding based on orientation
        pagePaddingHorizontal = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 48.dp else 24.dp,
        pagePaddingVertical = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 40.dp else 32.dp
    )
}
