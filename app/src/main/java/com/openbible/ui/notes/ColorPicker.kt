package com.openbible.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/**
 * Convert HSV to ARGB Long (0xAARRGGBB).
 * h: 0-360, s: 0-1, v: 0-1.
 */
private fun hsvToArgb(h: Float, s: Float, v: Float): Long {
    val c = v * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return ((255L shl 24) or
            ((r1 + m) * 255f).toInt().toLong().coerceIn(0, 255) shl 16 or
            ((g1 + m) * 255f).toInt().toLong().coerceIn(0, 255) shl 8 or
            ((b1 + m) * 255f).toInt().toLong().coerceIn(0, 255))
}

/** Extract hue (0-360), saturation (0-1), value (0-1) from ARGB Long. */
private fun argbToHsv(color: Long): FloatArray {
    val r = ((color shr 16) and 0xFF).toFloat() / 255f
    val g = ((color shr 8) and 0xFF).toFloat() / 255f
    val b = (color and 0xFF).toFloat() / 255f
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> ((g - b) / d) % 6f
        mx == g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return floatArrayOf(h * 60f, if (mx == 0f) 0f else d / mx, mx)
}

/**
 * 2D HSV saturation-brightness picker + hue slider + recent colors + palette.
 *
 * Based on current [hue] (0-360), displays a 2D surface where X=saturation
 * and Y=brightness. The hue slider below lets user change hue.
 * Recent colors are kept in-memory session only.
 */
@Composable
fun ColorPicker(
    currentColor: Long,
    onColorChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(currentColor) { argbToHsv(currentColor) }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    // Session recent colors (ponytail: in-memory only, no persistence)
    val recentColors = remember { mutableStateListOf<Long>() }

    // Sync when color changes externally
    LaunchedEffect(currentColor) {
        val c = argbToHsv(currentColor)
        hue = c[0]; saturation = c[1]; value = c[2]
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 2D Saturation-Brightness picker ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            saturation = (pos.x / w).coerceIn(0f, 1f)
                            value = 1f - (pos.y / h).coerceIn(0f, 1f)
                            onColorChanged(hsvToArgb(hue, saturation, value))
                            if (change.positionChange() != Offset.Zero) change.consume()
                        }
                    }
                }
        ) {
            // Draw HSV surface
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Layer 1: pure hue fill
                drawRect(Color(hsvToArgb(hue, 1f, 1f)))
                // Layer 2: white → transparent (saturation)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White,
                        1f to Color.Transparent
                    )
                )
                // Layer 3: transparent → black (value)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black
                    )
                )
                // Indicator circle
                val cx = saturation * w
                val cy = (1f - value) * h
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Hue slider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            hue = ((change.position.x / size.width.toFloat()) * 360f)
                                .coerceIn(0f, 359f)
                            onColorChanged(hsvToArgb(hue, saturation, value))
                            if (change.positionChange() != Offset.Zero) change.consume()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Hue spectrum gradient
                val hueColors = (0..12).map { i ->
                    Color(hsvToArgb((i / 12f) * 360f, 1f, 1f))
                }
                drawRect(
                    brush = Brush.horizontalGradient(hueColors)
                )
                // Indicator
                val hx = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(hx, size.height / 2f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(hx, size.height / 2f),
                    style = Stroke(width = 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Recent colors + preset palette ──
        val presetColors = listOf(
            0xFF000000, 0xFFFFFFFF, 0xFFB71C1C, 0xFF2E7D32,
            0xFF1565C0, 0xFFE65100, 0xFF6A1B9A, 0xFF00838F,
            0xFFF44336, 0xFF4CAF50, 0xFF2196F3, 0xFFFF9800,
            0xFF9C27B0, 0xFF00BCD4, 0xFF795548, 0xFF607D8B
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Recent", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.width(48.dp))
            recentColors.take(6).forEach { color ->
                ColorSwatch(color = color, onClick = {
                    val c = argbToHsv(color)
                    hue = c[0]; saturation = c[1]; value = c[2]
                    onColorChanged(color)
                })
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Palette", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.width(48.dp))
            presetColors.forEach { color ->
                ColorSwatch(color = color, onClick = {
                    val c = argbToHsv(color)
                    hue = c[0]; saturation = c[1]; value = c[2]
                    onColorChanged(color)
                    if (color !in recentColors) recentColors.add(0, color)
                })
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .padding(1.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick)
    )
    Spacer(modifier = Modifier.width(3.dp))
}
