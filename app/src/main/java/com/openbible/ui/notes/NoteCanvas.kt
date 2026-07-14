package com.openbible.ui.notes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.UUID

/**
 * Unified note canvas.
 *
 * One renderer draws every element type (ink, highlighter, shapes, images)
 * with a single shared transform, and one pointer loop handles all five
 * tools. No per-type special cases — the tool just changes what a gesture
 * produces or how it edits the element list.
 *
 * In-progress gestures (pen/shape/erase/select-drag) are held in local
 * state and committed to [onPageChanged] exactly once, on pointer-up, so
 * each completed action is a single undo step.
 */
@Composable
fun NoteCanvas(
    page: NotePage,
    activeTool: DrawTool,
    shapeType: ShapeType,
    penColor: Long,
    penSize: Float,
    selectedElementId: String?,
    onPageChanged: (NotePage) -> Unit,
    onSelectedElementChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local mirror of elements; mutated live during drags, committed on up.
    var elements by remember(page) { mutableStateOf(page.elements) }
    LaunchedEffect(page.elements) { elements = page.elements }

    // In-progress draft state
    var penPoints by remember(activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember(activeTool) { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember(activeTool) { mutableStateOf<Offset?>(null) }
    var eraserPath by remember(activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Selection / drag state
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOrigTransform by remember { mutableStateOf(ElementTransform()) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var resizing by remember { mutableStateOf(false) }
    var resizeStartDist by remember { mutableStateOf(0f) }
    var resizeStartScale by remember { mutableStateOf(1f) }
    var dirty by remember { mutableStateOf(false) }

    // Preload image bitmaps (small counts; synchronous decode is acceptable here)
    val bitmaps = remember(elements) {
        elements.filterIsInstance<ImageElement>().associate { el ->
            el.filePath to runCatching { BitmapFactory.decodeFile(el.filePath)?.asImageBitmap() }.getOrNull()
        }
    }

    fun commit(newElements: List<NoteElement>) {
        elements = newElements
        onPageChanged(page.copy(elements = newElements))
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(activeTool, penColor, penSize, page) {
                awaitPointerEventScope {
                    canvasSize = size
                    while (true) {
                        val event = awaitPointerEvent()
                        canvasSize = size
                        val touch = event.changes.firstOrNull { it.type == PointerType.Stylus }
                            ?: event.changes.firstOrNull { it.type == PointerType.Touch }
                            ?: continue

                        when (activeTool) {
                            DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                                val isHl = activeTool == DrawTool.HIGHLIGHTER
                                when {
                                    touch.changedToDown() -> {
                                        penPoints = listOf(touch.position)
                                        onSelectedElementChanged(null)
                                    }
                                    touch.changedToUp() -> {
                                        if (penPoints.size > 1) {
                                            val stroke = InkElement(
                                                id = UUID.randomUUID().toString(),
                                                points = penPoints,
                                                color = penColor,
                                                width = if (isHl) maxOf(penSize, 14f) else penSize * touch.pressure.coerceIn(0.25f, 1f),
                                                highlighter = isHl
                                            )
                                            commit(elements + stroke)
                                        }
                                        penPoints = emptyList()
                                    }
                                    touch.positionChange() != Offset.Zero -> {
                                        val last = penPoints.lastOrNull()
                                        if (last == null || (touch.position - last).getDistance() > 1f) {
                                            penPoints = penPoints + touch.position
                                        }
                                        touch.consume()
                                    }
                                }
                            }

                            DrawTool.SHAPE -> {
                                when {
                                    touch.changedToDown() -> {
                                        shapeStart = touch.position
                                        shapeEnd = touch.position
                                        onSelectedElementChanged(null)
                                    }
                                    touch.changedToUp() -> {
                                        val s = shapeStart; val e = shapeEnd
                                        if (s != null && e != null && (s - e).getDistance() > 3f) {
                                            val shape = ShapeElement(
                                                id = UUID.randomUUID().toString(),
                                                shape = shapeType,
                                                p1 = s, p2 = e,
                                                color = penColor,
                                                width = penSize
                                            )
                                            commit(elements + shape)
                                        }
                                        shapeStart = null; shapeEnd = null
                                    }
                                    touch.positionChange() != Offset.Zero -> {
                                        shapeEnd = touch.position
                                        touch.consume()
                                    }
                                }
                            }

                            DrawTool.ERASER -> {
                                when {
                                    touch.changedToDown() -> eraserPath = listOf(touch.position)
                                    touch.changedToUp() -> {
                                        if (eraserPath.isNotEmpty()) {
                                            val threshold = (penSize * 3f).coerceAtLeast(10f)
                                            val remaining = elements.filterNot { el ->
                                                val b = el.transformedBounds()
                                                eraserPath.any { ep ->
                                                    ep.x in (b.left - threshold)..(b.right + threshold) &&
                                                    ep.y in (b.top - threshold)..(b.bottom + threshold)
                                                }
                                            }
                                            commit(remaining)
                                        }
                                        eraserPath = emptyList()
                                    }
                                    touch.positionChange() != Offset.Zero -> {
                                        eraserPath = eraserPath + touch.position
                                        touch.consume()
                                    }
                                }
                            }

                            DrawTool.SELECT -> {
                                when {
                                    touch.changedToDown() -> {
                                        // Resize handle of currently selected element?
                                        val sel = elements.firstOrNull { it.id == selectedElementId }
                                        if (sel != null && nearResizeHandle(sel, touch.position)) {
                                            resizing = true
                                            val b = sel.transformedBounds()
                                            val c = b.center
                                            resizeStartDist = (touch.position - c).getDistance().coerceAtLeast(1f)
                                            resizeStartScale = sel.transform.scale
                                        } else {
                                            // Hit-test topmost element
                                            val hit = elements.asReversed().firstOrNull { pointInElement(it, touch.position) }
                                            if (hit != null) {
                                                onSelectedElementChanged(hit.id)
                                                draggingId = hit.id
                                                dragOrigTransform = hit.transform
                                                dragStart = touch.position
                                            } else {
                                                onSelectedElementChanged(null)
                                                draggingId = null
                                            }
                                        }
                                    }
                                    touch.changedToUp() -> {
                                        resizing = false
                                        draggingId = null
                                        if (dirty) commit(elements)
                                        dirty = false
                                    }
                                    touch.positionChange() != Offset.Zero -> {
                                        val id = draggingId ?: selectedElementId
                                        if (id != null) {
                                            val idx = elements.indexOfFirst { it.id == id }
                                            if (idx >= 0) {
                                                val el = elements[idx]
                                                val newTf = if (resizing) {
                                                    val b = el.transformedBounds()
                                                    val cScreen = b.center
                                                    val dist = (touch.position - cScreen).getDistance().coerceAtLeast(1f)
                                                    val s2 = (resizeStartScale * dist / resizeStartDist).coerceIn(0.2f, 8f)
                                                    val centerOrig = el.boundsOriginal().center
                                                    val tx = cScreen.x - centerOrig.x * s2
                                                    val ty = cScreen.y - centerOrig.y * s2
                                                    el.transform.copy(scale = s2, tx = tx, ty = ty)
                                                } else {
                                                    val delta = touch.position - dragStart
                                                    el.transform.copy(
                                                        tx = dragOrigTransform.tx + delta.x,
                                                        ty = dragOrigTransform.ty + delta.y
                                                    )
                                                }
                                                elements = elements.toMutableList().also { it[idx] = el.withTransform(newTf) }
                                                dirty = true
                                            }
                                        }
                                        touch.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        drawTemplate(this, page.template, page.coverColor, size)
        // Highlighters first (under ink), then ink, shapes, images.
        elements.filterIsInstance<InkElement>().filter { it.highlighter }.forEach { drawInk(this, it, true) }
        elements.filterIsInstance<InkElement>().filter { !it.highlighter }.forEach { drawInk(this, it, false) }
        elements.filterIsInstance<ShapeElement>().forEach { drawShape(this, it) }
        elements.filterIsInstance<ImageElement>().forEach { drawImage(this, it, bitmaps[it.filePath]) }

        // Draft pen stroke
        if (penPoints.size > 1) {
            drawPathFromPoints(this, penPoints, Color(penColor), if (activeTool == DrawTool.HIGHLIGHTER) maxOf(penSize, 14f) else penSize, activeTool == DrawTool.HIGHLIGHTER)
        }
        // Draft shape preview
        if (activeTool == DrawTool.SHAPE && shapeStart != null && shapeEnd != null) {
            drawShapePreview(this, shapeType, shapeStart!!, shapeEnd!!, Color(penColor), penSize)
        }
        // Eraser path
        if (eraserPath.size > 1) {
            drawPathFromPoints(this, eraserPath, Color(0x40000000), penSize * 3f, false)
        }
        // Selection overlay
        selectedElementId?.let { id -> elements.firstOrNull { it.id == id }?.let { drawSelection(this, it.transformedBounds()) } }
    }
}

/* ── Geometry helpers ──────────────────────────────────────────────────── */

fun NoteElement.transformedBounds(): Rect {
    val b = boundsOriginal()
    val xs = listOf(b.left, b.right).map { it * transform.scale + transform.tx }
    val ys = listOf(b.top, b.bottom).map { it * transform.scale + transform.ty }
    return Rect(xs.min(), ys.min(), xs.max(), ys.max())
}

private fun pointInElement(el: NoteElement, p: Offset): Boolean {
    val b = el.transformedBounds()
    val pad = if (el is ImageElement) 8f else maxOf((el as? InkElement)?.width ?: 12f, 12f)
    return p.x in (b.left - pad)..(b.right + pad) && p.y in (b.top - pad)..(b.bottom + pad)
}

private fun nearResizeHandle(el: NoteElement, p: Offset): Boolean {
    val b = el.transformedBounds()
    val handle = Offset(b.right, b.bottom)
    return (p - handle).getDistance() <= 24f
}

/* ── Drawing helpers ───────────────────────────────────────────────────── */

private fun drawTemplate(scope: DrawScope, template: PageTemplate, cover: Long?, size: Size) {
    scope.drawRect(cover?.let { Color(it) } ?: Color.White)
    val line = Color(0xFFCFD8DC)
    val step = 40f
    when (template) {
        PageTemplate.BLANK -> {}
        PageTemplate.RULED -> {
            var y = step
            while (y < size.height) { scope.drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += step }
        }
        PageTemplate.GRID -> {
            var y = step
            while (y < size.height) { scope.drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += step }
            var x = step
            while (x < size.width) { scope.drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f); x += step }
        }
        PageTemplate.DOTTED -> {
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) { scope.drawCircle(line, radius = 1.5f, center = Offset(x, y)); x += step }
                y += step
            }
        }
    }
}

private fun drawInk(scope: DrawScope, el: InkElement, highlight: Boolean) {
    if (el.points.size < 2) return
    val pts = el.points.map { it.apply(el.transform) }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    scope.drawPath(
        path = path,
        color = Color(el.color).let { if (highlight) it.copy(alpha = 0.4f) else it },
        style = Stroke(width = el.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun drawPathFromPoints(scope: DrawScope, points: List<Offset>, color: Color, width: Float, highlight: Boolean) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    scope.drawPath(
        path = path,
        color = if (highlight) color.copy(alpha = 0.4f) else color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun drawShape(scope: DrawScope, el: ShapeElement) {
    val p1 = el.p1.apply(el.transform)
    val p2 = el.p2.apply(el.transform)
    val style = Stroke(width = el.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val color = Color(el.color)
    val topLeft = Offset(minOf(p1.x, p2.x), minOf(p1.y, p2.y))
    val size = Size(kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y))
    when (el.shape) {
        ShapeType.LINE -> scope.drawLine(color, p1, p2, strokeWidth = el.width, cap = StrokeCap.Round)
        ShapeType.RECTANGLE -> scope.drawRect(color, topLeft = topLeft, size = size, style = style)
        ShapeType.OVAL -> scope.drawOval(color, topLeft = topLeft, size = size, style = style)
        ShapeType.ARROW -> {
            scope.drawLine(color, p1, p2, strokeWidth = el.width, cap = StrokeCap.Round)
            val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
            val len = 16f
            val a1 = angle + kotlin.math.PI * 0.85
            val a2 = angle - kotlin.math.PI * 0.85
            scope.drawLine(color, p2, Offset(p2.x + len * kotlin.math.cos(a1).toFloat(), p2.y + len * kotlin.math.sin(a1).toFloat()), strokeWidth = el.width, cap = StrokeCap.Round)
            scope.drawLine(color, p2, Offset(p2.x + len * kotlin.math.cos(a2).toFloat(), p2.y + len * kotlin.math.sin(a2).toFloat()), strokeWidth = el.width, cap = StrokeCap.Round)
        }
    }
}

private fun drawShapePreview(scope: DrawScope, shape: ShapeType, p1: Offset, p2: Offset, color: Color, width: Float) {
    val style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val topLeft = Offset(minOf(p1.x, p2.x), minOf(p1.y, p2.y))
    val size = Size(kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y))
    when (shape) {
        ShapeType.LINE -> scope.drawLine(color, p1, p2, strokeWidth = width, cap = StrokeCap.Round)
        ShapeType.RECTANGLE -> scope.drawRect(color, topLeft = topLeft, size = size, style = style)
        ShapeType.OVAL -> scope.drawOval(color, topLeft = topLeft, size = size, style = style)
        ShapeType.ARROW -> {
            scope.drawLine(color, p1, p2, strokeWidth = width, cap = StrokeCap.Round)
            val angle = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
            val len = 16f
            val a1 = angle + kotlin.math.PI * 0.85
            val a2 = angle - kotlin.math.PI * 0.85
            scope.drawLine(color, p2, Offset(p2.x + len * kotlin.math.cos(a1).toFloat(), p2.y + len * kotlin.math.sin(a1).toFloat()), strokeWidth = width, cap = StrokeCap.Round)
            scope.drawLine(color, p2, Offset(p2.x + len * kotlin.math.cos(a2).toFloat(), p2.y + len * kotlin.math.sin(a2).toFloat()), strokeWidth = width, cap = StrokeCap.Round)
        }
    }
}

private fun drawImage(scope: DrawScope, el: ImageElement, bitmap: ImageBitmap?) {
    if (bitmap == null) {
        scope.drawRect(Color(0xFFE0E0E0), topLeft = Offset(el.left, el.top).apply(el.transform),
            size = Size(el.width * el.transform.scale, el.height * el.transform.scale), style = Stroke(width = 1f))
        return
    }
    scope.drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            (el.left * el.transform.scale + el.transform.tx).toInt(),
            (el.top * el.transform.scale + el.transform.ty).toInt()
        ),
        dstSize = IntSize((el.width * el.transform.scale).toInt(), (el.height * el.transform.scale).toInt())
    )
}

private fun drawSelection(scope: DrawScope, b: Rect) {
    scope.drawRect(
        color = Color(0xFF2196F3),
        topLeft = Offset(b.left, b.top),
        size = Size(b.width, b.height),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    )
    scope.drawCircle(Color(0xFF2196F3), radius = 10f, center = Offset(b.right, b.bottom))
}
