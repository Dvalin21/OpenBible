package com.openbible.ui.notes

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single continuous pen stroke (from touch-down to touch-up).
 * Each stroke has a color, width, and list of points.
 */
data class InkStroke(
    val points: List<Offset>,
    val color: Long,
    val width: Float,
    val isEraser: Boolean = false
) {
    /** Serialize to JSON for persistence. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("color", color)
        put("width", width.toDouble())
        put("eraser", isEraser)
        val pts = JSONArray()
        points.forEach { pts.put(JSONArray().apply { put(it.x.toDouble()); put(it.y.toDouble()) }) }
        put("points", pts)
    }

    companion object {
        /** Deserialize from JSON. */
        fun fromJson(obj: JSONObject): InkStroke {
            val pts = mutableListOf<Offset>()
            val arr = obj.getJSONArray("points")
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                pts.add(Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()))
            }
            return InkStroke(
                points = pts,
                color = obj.optLong("color", 0xFF000000),
                width = obj.optDouble("width", 2.0).toFloat(),
                isEraser = obj.optBoolean("eraser", false)
            )
        }
    }
}

/** Serialize a list of strokes to a JSON string for storage. */
fun strokesToJson(strokes: List<InkStroke>): String {
    val arr = JSONArray()
    strokes.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

/** Deserialize a list of strokes from a JSON string. */
fun strokesFromJson(json: String?): List<InkStroke> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { InkStroke.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

/**
 * Drawing canvas for stylus and finger input.
 *
 * Supports multiple pen sizes, colors, and eraser mode.
 * Strokes are serializable to JSON for persistence in NoteEntity.penStrokes.
 */
@Composable
fun DrawingCanvas(
    strokes: List<InkStroke>,
    onStrokesChanged: (List<InkStroke>) -> Unit,
    penSize: Float,
    penColor: Long,
    isEraser: Boolean,
    modifier: Modifier = Modifier
) {
    // Active stroke being drawn
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var currentColor by remember { mutableStateOf(penColor) }
    var currentWidth by remember { mutableStateOf(penSize) }
    var currentEraser by remember { mutableStateOf(isEraser) }

    // Sync pen settings when they change externally
    LaunchedEffect(penSize) { currentWidth = penSize }
    LaunchedEffect(penColor) { if (!currentEraser) currentColor = penColor }
    LaunchedEffect(isEraser) { currentEraser = isEraser }

    val pathColor = if (currentEraser) Color.White else Color(currentColor)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(penSize, penColor, isEraser) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // Filter for touch/stylus only
                        val touch = event.changes.firstOrNull()?.let { change ->
                            if (change.type == PointerType.Touch || change.type == PointerType.Stylus) change
                            else null
                        } ?: continue

                        when {
                            touch.changedToDown() -> {
                                currentPoints = listOf(touch.position)
                                currentWidth = penSize
                                currentColor = if (isEraser) 0xFFFFFFFF else penColor
                                currentEraser = isEraser
                            }
                            touch.changedToUp() -> {
                                if (currentPoints.size > 1) {
                                    val stroke = InkStroke(
                                        points = currentPoints,
                                        color = currentColor,
                                        width = currentWidth,
                                        isEraser = currentEraser
                                    )
                                    onStrokesChanged(strokes + stroke)
                                }
                                currentPoints = emptyList()
                            }
                            touch.positionChange() != Offset.Zero -> {
                                // Only add point if moved enough (avoid noise)
                                val last = currentPoints.lastOrNull()
                                if (last == null || (touch.position - last).getDistance() > 1f) {
                                    currentPoints = currentPoints + touch.position
                                }
                                touch.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // Draw completed strokes
        for (stroke in strokes) {
            drawStroke(stroke)
        }
        // Draw current in-progress stroke
        if (currentPoints.size > 1) {
            val path = Path().apply {
                moveTo(currentPoints[0].x, currentPoints[0].y)
                for (i in 1 until currentPoints.size) {
                    lineTo(currentPoints[i].x, currentPoints[i].y)
                }
            }
            drawPath(
                path = path,
                color = pathColor,
                style = Stroke(
                    width = currentWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** Draw a single completed stroke onto the canvas. */
private fun DrawScope.drawStroke(stroke: InkStroke) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            lineTo(stroke.points[i].x, stroke.points[i].y)
        }
    }
    val color = if (stroke.isEraser) Color.White else Color(stroke.color)
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = stroke.width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
