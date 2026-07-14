package com.openbible.ui.notes

import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject

/* ──────────────────────────────────────────────────────────────────────────
 * Unified note content model.
 *
 * One note = an ordered list of pages. Each page has text, a background
 * template, and a list of elements (ink/highlighter, shapes, images).
 * Every element carries an ElementTransform so the lasso/select tool can
 * move and resize it uniformly — no per-type special cases in the canvas.
 * ponytail: coordinates stored in absolute canvas px; notes are tied to the
 * canvas size they were drawn on. Normalise on load if cross-device matters.
 * ──────────────────────────────────────────────────────────────────────── */

enum class DrawTool { SELECT, PEN, HIGHLIGHTER, SHAPE, ERASER }
enum class ShapeType { LINE, RECTANGLE, OVAL, ARROW }
enum class PageTemplate { BLANK, RULED, GRID, DOTTED }

data class ElementTransform(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scale: Float = 1f
)

/** Apply this transform to a point (linear: p*scale + t). */
fun Offset.apply(tf: ElementTransform): Offset =
    Offset(x * tf.scale + tf.tx, y * tf.scale + tf.ty)

sealed interface NoteElement {
    val id: String
    val transform: ElementTransform
    fun withTransform(t: ElementTransform): NoteElement
    /** Original (untransformed) bounding box, for hit-testing + handle placement. */
    fun boundsOriginal(): androidx.compose.ui.geometry.Rect
}

data class InkElement(
    override val id: String,
    val points: List<Offset>,
    val color: Long,
    val width: Float,
    val highlighter: Boolean,
    override val transform: ElementTransform = ElementTransform()
) : NoteElement {
    override fun withTransform(t: ElementTransform) = copy(transform = t)
    override fun boundsOriginal(): androidx.compose.ui.geometry.Rect {
        if (points.isEmpty()) return androidx.compose.ui.geometry.Rect.Zero
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
    }
}

data class ShapeElement(
    override val id: String,
    val shape: ShapeType,
    val p1: Offset,
    val p2: Offset,
    val color: Long,
    val width: Float,
    override val transform: ElementTransform = ElementTransform()
) : NoteElement {
    override fun withTransform(t: ElementTransform) = copy(transform = t)
    override fun boundsOriginal(): androidx.compose.ui.geometry.Rect {
        val minX = minOf(p1.x, p2.x); val minY = minOf(p1.y, p2.y)
        val maxX = maxOf(p1.x, p2.x); val maxY = maxOf(p1.y, p2.y)
        return androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
    }
}

data class ImageElement(
    override val id: String,
    val filePath: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    override val transform: ElementTransform = ElementTransform()
) : NoteElement {
    override fun withTransform(t: ElementTransform) = copy(transform = t)
    override fun boundsOriginal(): androidx.compose.ui.geometry.Rect =
        androidx.compose.ui.geometry.Rect(left, top, left + width, top + height)
}

data class NotePage(
    val text: String = "",
    val template: PageTemplate = PageTemplate.BLANK,
    val coverColor: Long? = null,
    val elements: List<NoteElement> = emptyList()
)

/* ── Legacy InkStroke (kept for tests + migration of old notes) ───────────── */

/**
 * A single continuous pen stroke (from touch-down to touch-up).
 * Carried over from the original DrawingCanvas; used to migrate legacy
 * NoteEntity.penStrokes into InkElements on load.
 */
data class InkStroke(
    val points: List<Offset>,
    val color: Long,
    val width: Float,
    val isEraser: Boolean = false
) {
    fun toJson(canvasWidth: Float, canvasHeight: Float): JSONObject = JSONObject().apply {
        put("color", color)
        put("width", width.toDouble())
        put("eraser", isEraser)
        val hasSize = canvasWidth > 0f && canvasHeight > 0f
        if (hasSize) {
            put("cw", canvasWidth.toDouble())
            put("ch", canvasHeight.toDouble())
        }
        val pts = JSONArray()
        points.forEach { pts.put(JSONArray().apply {
            if (hasSize) {
                put((it.x / canvasWidth).toDouble())
                put((it.y / canvasHeight).toDouble())
            } else {
                put(it.x.toDouble())
                put(it.y.toDouble())
            }
        }) }
        put("points", pts)
    }

    companion object {
        fun fromJson(obj: JSONObject): InkStroke {
            val cw = obj.optDouble("cw", 0.0)
            val ch = obj.optDouble("ch", 0.0)
            val hasCanvasSize = cw > 0.0 && ch > 0.0
            val pts = mutableListOf<Offset>()
            val arr = obj.getJSONArray("points")
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                val x = p.getDouble(0).toFloat()
                val y = p.getDouble(1).toFloat()
                pts.add(if (hasCanvasSize) Offset(x * cw.toFloat(), y * ch.toFloat()) else Offset(x, y))
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

fun strokesToJson(strokes: List<InkStroke>, canvasWidth: Float = 0f, canvasHeight: Float = 0f): String {
    val arr = JSONArray()
    strokes.forEach { arr.put(it.toJson(canvasWidth, canvasHeight)) }
    return arr.toString()
}

fun strokesFromJson(json: String?): List<InkStroke> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { InkStroke.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

/** Convert a legacy InkStroke list into InkElements (eraser strokes dropped). */
fun legacyStrokesToElements(strokes: List<InkStroke>): List<NoteElement> =
    strokes.filter { !it.isEraser }.map {
        InkElement(
            id = java.util.UUID.randomUUID().toString(),
            points = it.points,
            color = it.color,
            width = it.width,
            highlighter = false
        )
    }

/* ── Pages JSON (de)serialization ─────────────────────────────────────────── */

private fun tfToJson(tf: ElementTransform): JSONArray =
    JSONArray().apply { put(tf.tx.toDouble()); put(tf.ty.toDouble()); put(tf.scale.toDouble()) }

private fun tfFromJson(arr: JSONArray?): ElementTransform {
    if (arr == null || arr.length() < 3) return ElementTransform()
    return ElementTransform(
        tx = arr.getDouble(0).toFloat(),
        ty = arr.getDouble(1).toFloat(),
        scale = arr.getDouble(2).toFloat().coerceAtLeast(0.05f)
    )
}

private fun elementToJson(e: NoteElement): JSONObject = when (e) {
    is InkElement -> JSONObject().apply {
        put("t", "ink"); put("id", e.id); put("c", e.color)
        put("w", e.width.toDouble()); put("hl", e.highlighter)
        val pts = JSONArray()
        e.points.forEach { p -> pts.put(JSONArray().apply { put(p.x.toDouble()); put(p.y.toDouble()) }) }
        put("pts", pts); put("tf", tfToJson(e.transform))
    }
    is ShapeElement -> JSONObject().apply {
        put("t", "shape"); put("id", e.id); put("s", e.shape.name)
        put("c", e.color); put("w", e.width.toDouble())
        put("p1", JSONArray().apply { put(e.p1.x.toDouble()); put(e.p1.y.toDouble()) })
        put("p2", JSONArray().apply { put(e.p2.x.toDouble()); put(e.p2.y.toDouble()) })
        put("tf", tfToJson(e.transform))
    }
    is ImageElement -> JSONObject().apply {
        put("t", "img"); put("id", e.id); put("path", e.filePath)
        put("l", e.left.toDouble()); put("tp", e.top.toDouble())
        put("w", e.width.toDouble()); put("h", e.height.toDouble())
        put("tf", tfToJson(e.transform))
    }
}

private fun elementFromJson(obj: JSONObject): NoteElement? = try {
    when (obj.getString("t")) {
        "ink" -> {
            val pts = mutableListOf<Offset>()
            val arr = obj.getJSONArray("pts")
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                pts.add(Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()))
            }
            InkElement(
                id = obj.getString("id"),
                points = pts,
                color = obj.optLong("c", 0xFF000000),
                width = obj.optDouble("w", 2.0).toFloat(),
                highlighter = obj.optBoolean("hl", false),
                transform = tfFromJson(obj.optJSONArray("tf"))
            )
        }
        "shape" -> {
            val p1 = obj.getJSONArray("p1"); val p2 = obj.getJSONArray("p2")
            ShapeElement(
                id = obj.getString("id"),
                shape = ShapeType.valueOf(obj.optString("s", "LINE")),
                p1 = Offset(p1.getDouble(0).toFloat(), p1.getDouble(1).toFloat()),
                p2 = Offset(p2.getDouble(0).toFloat(), p2.getDouble(1).toFloat()),
                color = obj.optLong("c", 0xFF000000),
                width = obj.optDouble("w", 2.0).toFloat(),
                transform = tfFromJson(obj.optJSONArray("tf"))
            )
        }
        "img" -> {
            ImageElement(
                id = obj.getString("id"),
                filePath = obj.getString("path"),
                left = obj.optDouble("l", 0.0).toFloat(),
                top = obj.optDouble("tp", 0.0).toFloat(),
                width = obj.optDouble("w", 100.0).toFloat(),
                height = obj.optDouble("h", 100.0).toFloat(),
                transform = tfFromJson(obj.optJSONArray("tf"))
            )
        }
        else -> null
    }
} catch (_: Exception) { null }

fun pagesToJson(pages: List<NotePage>): String {
    val arr = JSONArray()
    for (p in pages) {
        val obj = JSONObject()
        obj.put("text", p.text)
        obj.put("template", p.template.name)
        obj.put("cover", p.coverColor ?: JSONObject.NULL)
        val els = JSONArray()
        p.elements.forEach { els.put(elementToJson(it)) }
        obj.put("elements", els)
        arr.put(obj)
    }
    return arr.toString()
}

fun pagesFromJson(json: String?): List<NotePage> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { pageFromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

private fun pageFromJson(obj: JSONObject): NotePage? = try {
    val els = mutableListOf<NoteElement>()
    val arr = obj.getJSONArray("elements")
    for (i in 0 until arr.length()) {
        elementFromJson(arr.getJSONObject(i))?.let { els.add(it) }
    }
    NotePage(
        text = obj.optString("text", ""),
        template = try { PageTemplate.valueOf(obj.optString("template", "BLANK")) } catch (_: Exception) { PageTemplate.BLANK },
        coverColor = if (obj.has("cover") && !obj.isNull("cover")) obj.getLong("cover") else null,
        elements = els
    )
} catch (_: Exception) { null }
