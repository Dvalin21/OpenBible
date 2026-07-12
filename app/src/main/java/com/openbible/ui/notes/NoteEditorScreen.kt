package com.openbible.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openbible.data.model.PenMode
import kotlinx.coroutines.launch

/** Full note editor with text input, drawing canvas, and pen controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long? = null,
    initialTitle: String? = null,
    initialLinkedVerseId: Long? = null,
    onNavigateBack: () -> Unit,
    onNotebooksClick: () -> Unit = {},
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(noteId) {
        if (noteId != null) viewModel.loadNote(noteId)
        else if (initialTitle != null || initialLinkedVerseId != null) {
            viewModel.seedNewNote(initialTitle, initialLinkedVerseId)
        }
    }

    var showPenControls by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val savedId = viewModel.save()
                            if (savedId != null) onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Pen mode toggle
                    IconButton(onClick = {
                        viewModel.setPenMode(
                            when (state.penMode) {
                                PenMode.TEXT -> PenMode.INK
                                PenMode.INK -> PenMode.BOTH
                                PenMode.BOTH -> PenMode.TEXT
                            }
                        )
                    }) {
                        Icon(
                            when (state.penMode) {
                                PenMode.TEXT -> Icons.Outlined.EditNote
                                PenMode.INK -> Icons.Outlined.Draw
                                PenMode.BOTH -> Icons.Outlined.Brush
                            },
                            contentDescription = "Toggle pen mode"
                        )
                    }
                    if (state.penMode != PenMode.TEXT) {
                        IconButton(onClick = { showPenControls = !showPenControls }) {
                            Icon(Icons.Default.FormatPaint, contentDescription = "Pen settings")
                        }
                    }
                    IconButton(onClick = { scope.launch { viewModel.save() } }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    if (!state.isNew) {
                        IconButton(onClick = { 
                            viewModel.deleteNote()
                            onNavigateBack() 
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Notebook selector
            NotebookSelector(
                notebooks = state.notebooks,
                selectedId = state.activeNotebookId,
                onSelect = { viewModel.setActiveNotebook(it) },
                onCreateClick = onNotebooksClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            BasicTextField(
                value = state.title,
                onValueChange = { viewModel.setTitle(it) },
                textStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (state.title.isEmpty()) {
                            Text(
                                "Note title...",
                                style = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pen controls (shown when pen mode is active)
            if (showPenControls && state.penMode != PenMode.TEXT) {
                PenControls(
                    penSize = state.penSize,
                    penColor = state.penColor,
                    isEraser = state.isEraser,
                    showColorPicker = showColorPicker,
                    onSizeChange = { viewModel.setPenSize(it) },
                    onColorChange = { viewModel.setPenColor(it) },
                    onEraserToggle = { viewModel.toggleEraser() },
                    onToggleColorPicker = { showColorPicker = !showColorPicker },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() }
                )
                if (showColorPicker) {
                    ColorPicker(
                        currentColor = state.penColor,
                        onColorChanged = { viewModel.setPenColor(it) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Editor body (text, ink, or both)
            when (state.penMode) {
                PenMode.TEXT -> TextEditor(
                    text = state.contentText,
                    onTextChange = { viewModel.setContentText(it) },
                    modifier = Modifier.weight(1f)
                )
                PenMode.INK -> DrawingCanvas(
                    strokes = strokesFromJson(state.penStrokes),
                    onStrokesChanged = { strokes, cw, ch -> viewModel.setPenStrokes(strokesToJson(strokes, cw, ch)) },
                    penSize = state.penSize,
                    penColor = state.penColor,
                    isEraser = state.isEraser,
                    modifier = Modifier.weight(1f)
                )
                PenMode.BOTH -> {
                    // Split: top half text, bottom half drawing
                    TextEditor(
                        text = state.contentText,
                        onTextChange = { viewModel.setContentText(it) },
                        modifier = Modifier.weight(0.5f)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DrawingCanvas(
                        strokes = strokesFromJson(state.penStrokes),
                        onStrokesChanged = { strokes, cw, ch -> viewModel.setPenStrokes(strokesToJson(strokes, cw, ch)) },
                        penSize = state.penSize,
                        penColor = state.penColor,
                        isEraser = state.isEraser,
                        modifier = Modifier.weight(0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookSelector(
    notebooks: List<com.openbible.data.db.entity.NotebookEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (notebooks.isEmpty()) {
            Text(
                "No notebooks — ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            TextButton(onClick = onCreateClick) { Text("Create one") }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                        label = { Text("All") }
                    )
                }
                items(notebooks) { nb ->
                    FilterChip(
                        selected = nb.id == selectedId,
                        onClick = { onSelect(nb.id) },
                        label = { Text(nb.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PenControls(
    penSize: Float,
    penColor: Long,
    isEraser: Boolean,
    showColorPicker: Boolean = false,
    onSizeChange: (Float) -> Unit,
    onColorChange: (Long) -> Unit,
    onEraserToggle: () -> Unit,
    onToggleColorPicker: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Size slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
            Slider(
                value = penSize,
                onValueChange = onSizeChange,
                valueRange = 0.5f..20f,
                modifier = Modifier.weight(1f)
            )
            Text("%.1f".format(penSize), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
        }

        // Color preview + undo/redo + eraser
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Current color preview — tap to open full picker
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isEraser) MaterialTheme.colorScheme.surfaceVariant else Color(penColor))
                    .border(2.dp,
                        if (showColorPicker) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape)
                    .clickable { onToggleColorPicker() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            // Undo / Redo
            @Suppress("DEPRECATION")
            IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onRedo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onEraserToggle) {
                Icon(
                    if (isEraser) Icons.Default.AutoFixHigh else Icons.Outlined.AutoFixHigh,
                    contentDescription = "Eraser",
                    tint = if (isEraser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 24.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        decorationBox = { innerTextField ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        "Write your thoughts...",
                        style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    )
                }
                innerTextField()
            }
        }
    )
}
