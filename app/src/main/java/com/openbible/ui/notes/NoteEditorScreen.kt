package com.openbible.ui.notes

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openbible.data.db.entity.NoteAudioEntity
import com.openbible.data.db.entity.NotebookEntity
import com.openbible.data.model.PenMode
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Full note editor with text, unified drawing canvas, and pen controls. */
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAdvanced by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }

    // ── Audio memo recorder / player state ──
    var isRecording by remember { mutableStateOf(false) }
    var recordingPath by remember { mutableStateOf<String?>(null) }
    var recordStartedAt by remember { mutableStateOf(0L) }
    var playingAudioId by remember { mutableStateOf<Long?>(null) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder.value?.release() }
            runCatching { player.value?.release() }
        }
    }

    fun startRecording() {
        val dir = File(context.filesDir, "note_audio").apply { mkdirs() }
        val path = File(dir, UUID.randomUUID().toString() + ".m4a").absolutePath
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(path)
            mr.prepare()
            mr.start()
            recorder.value = mr
            recordingPath = path
            recordStartedAt = System.currentTimeMillis()
            isRecording = true
        } catch (e: Exception) {
            runCatching { mr.release() }
            recorder.value = null
            Toast.makeText(context, "Recording unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        val mr = recorder.value ?: return
        val duration = System.currentTimeMillis() - recordStartedAt
        runCatching { mr.stop() }
        runCatching { mr.release() }
        recorder.value = null
        isRecording = false
        val path = recordingPath
        recordingPath = null
        if (path != null && duration > 300) viewModel.addAudio(path, duration)
    }

    fun togglePlay(audio: NoteAudioEntity) {
        if (playingAudioId == audio.id) {
            runCatching { player.value?.release() }
            player.value = null
            playingAudioId = null
            return
        }
        runCatching { player.value?.release() }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(audio.filePath)
            mp.prepare()
            mp.setOnCompletionListener {
                playingAudioId = null
                runCatching { it.release() }
                player.value = null
            }
            mp.start()
            player.value = mp
            playingAudioId = audio.id
        } catch (e: Exception) {
            runCatching { mp.release() }
            Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
        }
    }

    // Insert image: copy picked URI into app storage, then add an ImageElement.
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val dir = File(context.filesDir, "note_images").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString() + ".jpg")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        }.onFailure { return@rememberLauncherForActivityResult }
        val el = ImageElement(
            id = UUID.randomUUID().toString(),
            filePath = dest.absolutePath,
            left = 80f, top = 80f, width = 320f, height = 320f
        )
        viewModel.commitActivePage(state.activePage.copy(elements = state.activePage.elements + el))
    }

    LaunchedEffect(noteId) {
        if (noteId != null) viewModel.loadNote(noteId)
        else if (initialTitle != null || initialLinkedVerseId != null) {
            viewModel.seedNewNote(initialTitle, initialLinkedVerseId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            if (viewModel.save() != null) onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                        IconButton(onClick = { showAdvanced = !showAdvanced }) {
                            Icon(Icons.Default.FormatPaint, contentDescription = "Pen settings")
                        }
                    }
                    IconButton(onClick = { scope.launch { viewModel.save() } }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            NotebookSelector(
                notebooks = state.notebooks,
                selectedId = state.activeNotebookId,
                onSelect = { viewModel.setActiveNotebook(it) },
                onCreateClick = onNotebooksClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = state.title,
                onValueChange = { viewModel.setTitle(it) },
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (state.title.isEmpty()) Text("Note title...", style = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)))
                        inner()
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Tags (chips) ──
            TagEditor(
                tags = state.tags,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag
            )
            Spacer(modifier = Modifier.height(4.dp))

            // ── Audio memos ──
            AudioMemoSection(
                audios = state.audios,
                isRecording = isRecording,
                currentlyPlayingId = playingAudioId,
                onToggleRecord = { if (isRecording) stopRecording() else startRecording() },
                onPlay = ::togglePlay,
                onDelete = viewModel::removeAudio
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Drawing toolbar (INK / BOTH) ──
            if (state.penMode != PenMode.TEXT) {
                ToolRow(
                    activeTool = state.activeTool,
                    shapeType = state.shapeType,
                    onTool = viewModel::setActiveTool,
                    onShape = viewModel::setShapeType,
                    onTemplateClick = { showTemplateMenu = true },
                    onImageClick = { imageLauncher.launch("image/*") },
                    templateMenuExpanded = showTemplateMenu,
                    onDismissTemplate = { showTemplateMenu = false },
                    currentTemplate = state.activePage.template,
                    onPickTemplate = viewModel::setTemplate
                )
                Spacer(modifier = Modifier.height(4.dp))
                PageRow(
                    pageCount = state.pages.size,
                    activeIndex = state.activePageIndex,
                    canDelete = state.canDeletePage,
                    onPrev = { viewModel.gotoPage(state.activePageIndex - 1) },
                    onNext = { viewModel.gotoPage(state.activePageIndex + 1) },
                    onAdd = viewModel::addPage,
                    onDelete = viewModel::deleteActivePage
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Advanced panel: size / color / undo / redo ──
            if (showAdvanced && state.penMode != PenMode.TEXT) {
                ToolSettingsRow(
                    penSize = state.penSize,
                    penColor = state.penColor,
                    penType = state.penType,
                    showColorPicker = showColorPicker,
                    onSizeChange = viewModel::setPenSize,
                    onColorChange = viewModel::setPenColor,
                    onPenTypeChange = viewModel::setPenType,
                    onToggleColorPicker = { showColorPicker = !showColorPicker },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo
                )
                if (showColorPicker) {
                    ColorPicker(currentColor = state.penColor, onColorChanged = viewModel::setPenColor, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Body ──
            when (state.penMode) {
                PenMode.TEXT -> TextEditor(
                    text = state.activePage.text,
                    onTextChange = viewModel::setContentText,
                    modifier = Modifier.weight(1f)
                )
                PenMode.INK -> NoteCanvas(
                    page = state.activePage,
                    activeTool = state.activeTool,
                    shapeType = state.shapeType,
                    penColor = state.penColor,
                    penSize = state.penSize,
                    penType = state.penType,
                    selectedElementId = state.selectedElementId,
                    onPageChanged = viewModel::commitActivePage,
                    onSelectedElementChanged = viewModel::setSelectedElement,
                    modifier = Modifier.weight(1f)
                )
                PenMode.BOTH -> {
                    TextEditor(
                        text = state.activePage.text,
                        onTextChange = viewModel::setContentText,
                        modifier = Modifier.weight(0.45f)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    NoteCanvas(
                        page = state.activePage,
                        activeTool = state.activeTool,
                        shapeType = state.shapeType,
                        penColor = state.penColor,
                        penSize = state.penSize,
                        penType = state.penType,
                        selectedElementId = state.selectedElementId,
                        onPageChanged = viewModel::commitActivePage,
                        onSelectedElementChanged = viewModel::setSelectedElement,
                        modifier = Modifier.weight(0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    activeTool: DrawTool,
    shapeType: ShapeType,
    onTool: (DrawTool) -> Unit,
    onShape: (ShapeType) -> Unit,
    onTemplateClick: () -> Unit,
    onImageClick: () -> Unit,
    templateMenuExpanded: Boolean,
    onDismissTemplate: () -> Unit,
    currentTemplate: PageTemplate,
    onPickTemplate: (PageTemplate) -> Unit
) {
    val tools = listOf(
        Triple(Icons.Default.OpenWith, "Select", DrawTool.SELECT),
        Triple(Icons.Default.Edit, "Pen", DrawTool.PEN),
        Triple(Icons.Default.Brush, "Highlight", DrawTool.HIGHLIGHTER),
        Triple(Icons.Default.CropSquare, "Shape", DrawTool.SHAPE),
        Triple(Icons.Outlined.AutoFixHigh, "Eraser", DrawTool.ERASER)
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((icon, label, tool) in tools) {
            IconButton(
                onClick = { onTool(tool) },
                modifier = Modifier
                    .size(36.dp)
                    .then(if (activeTool == tool) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
            ) {
                Icon(icon, contentDescription = label, tint = if (activeTool == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onTemplateClick) { Icon(Icons.Outlined.GridOn, contentDescription = "Page template") }
        IconButton(onClick = onImageClick) { Icon(Icons.Outlined.Image, contentDescription = "Insert image") }
    }

    // Shape sub-picker
    if (activeTool == DrawTool.SHAPE) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ShapeType.entries.forEach { s ->
                val icon = when (s) {
                    ShapeType.LINE -> Icons.Outlined.Remove
                    ShapeType.RECTANGLE -> Icons.Default.CropSquare
                    ShapeType.OVAL -> Icons.Outlined.RadioButtonUnchecked
                    ShapeType.ARROW -> Icons.Outlined.ArrowForward
                }
                IconButton(
                    onClick = { onShape(s) },
                    modifier = Modifier
                        .size(32.dp)
                        .then(if (shapeType == s) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                ) { Icon(icon, contentDescription = s.name, tint = if (shapeType == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
            }
        }
    }

    DropdownMenu(expanded = templateMenuExpanded, onDismissRequest = onDismissTemplate) {
        PageTemplate.entries.forEach { t ->
            DropdownMenuItem(text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                onPickTemplate(t); onDismissTemplate()
            }, leadingIcon = { if (t == currentTemplate) Icon(Icons.Default.Check, null) })
        }
    }
}

@Composable
private fun PageRow(
    pageCount: Int,
    activeIndex: Int,
    canDelete: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onPrev, enabled = activeIndex > 0) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous page") }
        Text("${activeIndex + 1}/$pageCount", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onNext, enabled = activeIndex < pageCount - 1) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next page") }
        IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add page") }
        IconButton(onClick = onDelete, enabled = canDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete page") }
    }
}

@Composable
private fun ToolSettingsRow(
    penSize: Float,
    penColor: Long,
    penType: PenType,
    showColorPicker: Boolean,
    onSizeChange: (Float) -> Unit,
    onColorChange: (Long) -> Unit,
    onPenTypeChange: (PenType) -> Unit,
    onToggleColorPicker: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // ── Pen type selector (Samsung Notes style) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PenType.entries.forEach { type ->
                val isSelected = type == penType
                val icon = when (type) {
                    PenType.BALLPOINT -> Icons.Default.Edit
                    PenType.FOUNTAIN -> Icons.Default.Create
                    PenType.BRUSH -> Icons.Default.Brush
                    PenType.PENCIL -> Icons.Outlined.ModeEdit
                    PenType.MARKER -> Icons.Default.Highlight
                }
                val label = when (type) {
                    PenType.BALLPOINT -> "Ball"
                    PenType.FOUNTAIN -> "Fountain"
                    PenType.BRUSH -> "Brush"
                    PenType.PENCIL -> "Pencil"
                    PenType.MARKER -> "Marker"
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onPenTypeChange(type) },
                        modifier = Modifier
                            .size(32.dp)
                            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                    ) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ── Quick size presets (Samsung Notes style) ──
        val sizePresets = listOf(2f, 5f, 10f, 20f, 35f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Size", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
            sizePresets.forEach { preset ->
                val isSelected = kotlin.math.abs(penSize - preset) < 1f
                val dotSize = (preset / 40f * 20f).coerceIn(4f, 20f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable { onSizeChange(preset) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotSize.dp)
                            .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                    )
                }
            }
            // Custom slider
            Slider(
                value = penSize, onValueChange = onSizeChange,
                valueRange = 0.5f..40f,
                modifier = Modifier.weight(1f)
            )
            Text("%.1f".format(penSize), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))

        // ── Color / Undo / Redo ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(penColor))
                    .clickable { onToggleColorPicker() }
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRedo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NotebookSelector(
    notebooks: List<NotebookEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreateClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (notebooks.isEmpty()) {
            Text("No notebooks — ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = onCreateClick) { Text("Create one") }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("All") })
                }
                items(notebooks) { nb ->
                    FilterChip(selected = nb.id == selectedId, onClick = { onSelect(nb.id) }, label = { Text(nb.name) })
                }
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
        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().verticalScroll(scrollState),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("Write your thoughts...", style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)))
                inner()
            }
        }
    )
}

@Composable
private fun TagEditor(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        if (tags.isEmpty() && text.isEmpty()) {
            Text("Add tags…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { onRemove(tag) },
                label = { Text("#$tag") },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            textStyle = TextStyle(fontSize = 13.sp),
            modifier = Modifier.width(96.dp).weight(1f, fill = false),
            trailingIcon = {
                IconButton(
                    onClick = { if (text.isNotBlank()) { onAdd(text); text = "" } },
                    modifier = Modifier.size(20.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Add tag", modifier = Modifier.size(16.dp)) }
            }
        )
    }
}

@Composable
private fun AudioMemoSection(
    audios: List<NoteAudioEntity>,
    isRecording: Boolean,
    currentlyPlayingId: Long?,
    onToggleRecord: () -> Unit,
    onPlay: (NoteAudioEntity) -> Unit,
    onDelete: (NoteAudioEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleRecord) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Record audio",
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                if (isRecording) "Recording… tap stop" else "Audio memos",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        audios.forEach { a ->
            val playing = a.id == currentlyPlayingId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                IconButton(onClick = { onPlay(a) }) {
                    Icon(
                        if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Stop" else "Play",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(formatDuration(a.durationMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDelete(a) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete audio", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
