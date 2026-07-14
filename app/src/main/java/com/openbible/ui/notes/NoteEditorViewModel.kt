package com.openbible.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.data.db.entity.NoteAudioEntity
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.NotebookEntity
import com.openbible.data.model.PenMode
import com.openbible.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class NoteEditorState(
    val note: NoteEntity? = null,
    val title: String = "",
    val penMode: PenMode = PenMode.TEXT,
    val linkedVerseIds: List<Long> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val activeNotebookId: Long? = null,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    // ── Tags (chips) ──
    val tags: List<String> = emptyList(),
    // ── Favorites / star ──
    val isFavorite: Boolean = false,
    // ── Audio memos ──
    val audios: List<NoteAudioEntity> = emptyList(),
    // ── Unified page model (single source of truth) ──
    val pages: List<NotePage> = listOf(NotePage()),
    val activePageIndex: Int = 0,
    // ── Tool state ──
    val activeTool: DrawTool = DrawTool.PEN,
    val shapeType: ShapeType = ShapeType.LINE,
    val selectedElementId: String? = null,
    // ── Pen settings ──
    val penSize: Float = 3f,
    val penColor: Long = 0xFF000000,
    val isEraser: Boolean = false
) {
    val activePage: NotePage get() = pages.getOrNull(activePageIndex) ?: NotePage()
    val canDeletePage: Boolean get() = pages.size > 1
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    // ponytail: undo/redo as stacks of full pages-JSON snapshots.
    // One snapshot per committed edit (stroke, shape, move, template, page add/del).
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    init {
        viewModelScope.launch {
            noteRepository.getAllNotebooks().collect { notebooks ->
                _state.update { it.copy(notebooks = notebooks) }
            }
        }
    }

    /** Load an existing note for editing; migrate legacy fields into the page model. */
    fun loadNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getNote(noteId) ?: return@launch
            val verses = noteRepository.getLinkedVerseIds(noteId)
            val pages = if (!note.pagesJson.isNullOrBlank()) {
                pagesFromJson(note.pagesJson).ifEmpty { migrateLegacy(note) }
            } else {
                migrateLegacy(note)
            }
            _state.value = NoteEditorState(
                note = note,
                title = note.title,
                penMode = note.penMode,
                linkedVerseIds = verses,
                activeNotebookId = note.notebookId,
                isNew = false,
                tags = note.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                isFavorite = note.isFavorite,
                audios = noteRepository.getAudiosForNote(note.id).first(),
                pages = pages,
                activePageIndex = 0
            )
        }
    }

    /** Build page 0 from legacy contentText + penStrokes. */
    private fun migrateLegacy(note: NoteEntity): List<NotePage> =
        listOf(
            NotePage(
                text = note.contentText ?: "",
                elements = legacyStrokesToElements(strokesFromJson(note.penStrokes))
            )
        )

    /** Seed a fresh note pre-linked to a passage (Study Mode). */
    fun seedNewNote(title: String?, verseId: Long?) {
        _state.update {
            it.copy(
                note = null,
                title = title ?: "",
                linkedVerseIds = if (verseId != null) listOf(verseId) else emptyList(),
                isNew = true,
                pages = listOf(NotePage()),
                activePageIndex = 0
            )
        }
    }

    // ── Text / pen mode ──

    fun setTitle(title: String) = _state.update { it.copy(title = title) }

    fun setContentText(text: String) {
        val i = _state.value.activePageIndex
        val pages = _state.value.pages.toMutableList()
        if (i in pages.indices) pages[i] = pages[i].copy(text = text)
        _state.update { it.copy(pages = pages) }
    }

    fun setPenMode(mode: PenMode) = _state.update { it.copy(penMode = mode) }
    fun setPenSize(size: Float) = _state.update { it.copy(penSize = size.coerceIn(0.5f, 40f)) }
    fun setPenColor(color: Long) = _state.update { it.copy(penColor = color, isEraser = false, activeTool = if (_state.value.activeTool == DrawTool.ERASER) DrawTool.PEN else _state.value.activeTool) }

    fun toggleEraser() {
        val erasing = _state.value.activeTool == DrawTool.ERASER
        _state.update {
            it.copy(
                activeTool = if (erasing) DrawTool.PEN else DrawTool.ERASER,
                isEraser = !erasing
            )
        }
    }

    // ── Tool state ──

    fun setActiveTool(tool: DrawTool) = _state.update { it.copy(activeTool = tool, selectedElementId = null) }
    fun setShapeType(s: ShapeType) = _state.update { it.copy(shapeType = s, activeTool = DrawTool.SHAPE) }
    fun setSelectedElement(id: String?) = _state.update { it.copy(selectedElementId = id) }

    // ── Page editing (called by the canvas on commit) ──

    /** Replace the active page (one undo step). */
    fun commitActivePage(page: NotePage) {
        pushUndo()
        val i = _state.value.activePageIndex
        val pages = _state.value.pages.toMutableList()
        if (i in pages.indices) pages[i] = page
        _state.update { it.copy(pages = pages) }
    }

    fun setTemplate(template: PageTemplate) {
        pushUndo()
        val i = _state.value.activePageIndex
        val pages = _state.value.pages.toMutableList()
        if (i in pages.indices) pages[i] = pages[i].copy(template = template)
        _state.update { it.copy(pages = pages) }
    }

    // ── Multi-page ──

    fun addPage() {
        pushUndo()
        val pages = _state.value.pages + NotePage()
        _state.update { it.copy(pages = pages, activePageIndex = pages.lastIndex) }
    }

    fun deleteActivePage() {
        if (_state.value.pages.size <= 1) return
        pushUndo()
        val i = _state.value.activePageIndex
        val pages = _state.value.pages.toMutableList().also { it.removeAt(i) }
        _state.update { it.copy(pages = pages, activePageIndex = i.coerceAtMost(pages.lastIndex)) }
    }

    fun gotoPage(index: Int) {
        if (index in _state.value.pages.indices) {
            _state.update { it.copy(activePageIndex = index, selectedElementId = null) }
        }
    }

    // ── Notebook / verse links ──

    fun setActiveNotebook(notebookId: Long?) = _state.update { it.copy(activeNotebookId = notebookId) }

    // ── Tags ──

    fun addTag(raw: String) {
        val t = raw.trim().removePrefix("#").trim()
        if (t.isNotEmpty() && t !in _state.value.tags) {
            _state.update { it.copy(tags = it.tags + t) }
        }
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    // ── Favorites / star ──

    fun toggleFavorite() {
        val next = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = next) }
        // Persist immediately for existing notes; new notes persist on first save.
        _state.value.note?.let { note ->
            viewModelScope.launch { noteRepository.updateNote(note.copy(isFavorite = next)) }
        }
    }

    // ── Audio memos ──

    /** Ensure the note has a row; returns its id (null if save failed). */
    private suspend fun ensureSavedId(): Long? {
        val s = _state.value
        return if (s.isNew) save() else s.note?.id
    }

    fun addAudio(filePath: String, durationMs: Long) {
        viewModelScope.launch {
            val noteId = ensureSavedId() ?: return@launch
            noteRepository.insertAudio(
                NoteAudioEntity(
                    noteId = noteId,
                    filePath = filePath,
                    durationMs = durationMs,
                    createdAt = System.currentTimeMillis()
                )
            )
            _state.update { it.copy(audios = noteRepository.getAudiosForNote(noteId).first()) }
        }
    }

    fun removeAudio(audio: NoteAudioEntity) {
        viewModelScope.launch {
            noteRepository.deleteAudio(audio)
            runCatching { java.io.File(audio.filePath).delete() }
            _state.value.note?.id?.let { id ->
                _state.update { it.copy(audios = noteRepository.getAudiosForNote(id).first()) }
            }
        }
    }

    fun linkVerse(verseId: Long) {
        val current = _state.value.linkedVerseIds
        if (verseId !in current) _state.update { it.copy(linkedVerseIds = current + verseId) }
    }

    fun unlinkVerse(verseId: Long) = _state.update { it.copy(linkedVerseIds = it.linkedVerseIds - verseId) }

    // ── Undo / redo ──

    private fun pushUndo() {
        undoStack.add(pagesToJson(_state.value.pages))
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(pagesToJson(_state.value.pages))
        val restored = pagesFromJson(undoStack.removeLast())
        if (restored.isNotEmpty()) {
            val i = _state.value.activePageIndex.coerceAtMost(restored.lastIndex)
            _state.update { it.copy(pages = restored, activePageIndex = i, selectedElementId = null) }
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(pagesToJson(_state.value.pages))
        val restored = pagesFromJson(redoStack.removeLast())
        if (restored.isNotEmpty()) {
            val i = _state.value.activePageIndex.coerceAtMost(restored.lastIndex)
            _state.update { it.copy(pages = restored, activePageIndex = i, selectedElementId = null) }
        }
    }

    // ── Save ──

    /** Save the note and return the note ID. Suspend — call from a coroutine. */
    suspend fun save(): Long? {
        val s = _state.value
        if (s.pages.isEmpty()) return null
        _state.update { it.copy(isSaving = true) }
        return try {
            withContext(Dispatchers.IO) {
                val pagesJson = pagesToJson(s.pages)
                // Mirror page 0 text into contentText for list previews / legacy readers.
                val previewText = s.pages.first().text.takeIf { it.isNotBlank() }
                val noteId: Long?
                if (s.isNew) {
                    noteId = noteRepository.createNote(
                        notebookId = s.activeNotebookId,
                        title = s.title.ifBlank { "Untitled" },
                        contentText = previewText,
                        penStrokes = null,
                        penMode = s.penMode,
                        tags = s.tags.joinToString(","),
                        pagesJson = pagesJson,
                        isFavorite = s.isFavorite
                    )
                } else {
                    s.note?.let { note ->
                        noteRepository.updateNote(
                            note.copy(
                                title = s.title.ifBlank { "Untitled" },
                                contentText = previewText,
                                penStrokes = null,
                                penMode = s.penMode,
                                notebookId = s.activeNotebookId,
                                tags = s.tags.joinToString(","),
                                isFavorite = s.isFavorite,
                                pagesJson = pagesJson
                            )
                        )
                    }
                    noteId = s.note?.id
                }
                noteId?.let { id ->
                    val existing = noteRepository.getLinkedVerseIds(id)
                    for (v in s.linkedVerseIds) if (v !in existing) noteRepository.linkVerse(id, v)
                    for (v in existing) if (v !in s.linkedVerseIds) noteRepository.unlinkVerse(id, v)
                }
                noteId
            }
        } finally {
            _state.update { it.copy(isSaving = false, isNew = false) }
        }
    }

    fun deleteNote() {
        val note = _state.value.note ?: return
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }
}
