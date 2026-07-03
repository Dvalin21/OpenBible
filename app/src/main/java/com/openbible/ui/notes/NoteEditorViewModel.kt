package com.openbible.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val contentText: String = "",
    val penStrokes: String? = null,
    val penMode: PenMode = PenMode.TEXT,
    val linkedVerseIds: List<Long> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val activeNotebookId: Long? = null,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val penSize: Float = 2f,
    val penColor: Long = 0xFF000000,
    val isEraser: Boolean = false
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    // ponytail: undo/redo as simple stacks of strokes JSON strings
    private val undoStack = mutableListOf<String?>()
    private val redoStack = mutableListOf<String?>()

    init {
        viewModelScope.launch {
            noteRepository.getAllNotebooks().collect { notebooks ->
                _state.update { it.copy(notebooks = notebooks) }
            }
        }
    }

    /** Load an existing note for editing. */
    fun loadNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getNote(noteId) ?: return@launch
            val verses = noteRepository.getLinkedVerseIds(noteId)
            _state.value = NoteEditorState(
                note = note,
                title = note.title,
                contentText = note.contentText ?: "",
                penStrokes = note.penStrokes,
                penMode = note.penMode,
                linkedVerseIds = verses,
                activeNotebookId = note.notebookId,
                isNew = false
            )
        }
    }

    fun setTitle(title: String) {
        _state.update { it.copy(title = title) }
    }

    fun setContentText(text: String) {
        _state.update { it.copy(contentText = text) }
    }

    fun setPenMode(mode: PenMode) {
        _state.update { it.copy(penMode = mode) }
    }

    fun setPenSize(size: Float) {
        _state.update { it.copy(penSize = size.coerceIn(0.5f, 20f)) }
    }

    fun setPenColor(color: Long) {
        _state.update { it.copy(penColor = color, isEraser = false) }
    }

    fun toggleEraser() {
        _state.update { it.copy(isEraser = !it.isEraser) }
    }

    fun setPenStrokes(strokes: String?) {
        // Push previous state to undo before changing
        undoStack.add(_state.value.penStrokes)
        redoStack.clear()  // new action invalidates redo
        _state.update { it.copy(penStrokes = strokes) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(_state.value.penStrokes)
        _state.update { it.copy(penStrokes = undoStack.removeLast()) }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(_state.value.penStrokes)
        _state.update { it.copy(penStrokes = redoStack.removeLast()) }
    }

    fun setActiveNotebook(notebookId: Long?) {
        _state.update { it.copy(activeNotebookId = notebookId) }
    }

    fun linkVerse(verseId: Long) {
        val current = _state.value.linkedVerseIds
        if (verseId !in current) {
            _state.update { it.copy(linkedVerseIds = current + verseId) }
        }
    }

    fun unlinkVerse(verseId: Long) {
        _state.update { it.copy(linkedVerseIds = it.linkedVerseIds - verseId) }
    }

    /** Save the note and return the note ID. Suspend — call from a coroutine. */
    suspend fun save(): Long? {
        val s = _state.value
        _state.update { it.copy(isSaving = true) }
        return try {
            withContext(Dispatchers.IO) {
                val noteId: Long?
                if (s.isNew) {
                    noteId = noteRepository.createNote(
                        notebookId = s.activeNotebookId,
                        title = s.title.ifBlank { "Untitled" },
                        contentText = s.contentText.ifBlank { null },
                        penStrokes = s.penStrokes,
                        penMode = s.penMode
                    )
                } else {
                    s.note?.let { note ->
                        noteRepository.updateNote(
                            note.copy(
                                title = s.title.ifBlank { "Untitled" },
                                contentText = s.contentText.ifBlank { null },
                                penStrokes = s.penStrokes,
                                penMode = s.penMode,
                                notebookId = s.activeNotebookId
                            )
                        )
                    }
                    noteId = s.note?.id
                }
                // Sync verse links
                noteId?.let { id ->
                    val existing = noteRepository.getLinkedVerseIds(id)
                    for (v in s.linkedVerseIds) { if (v !in existing) noteRepository.linkVerse(id, v) }
                    for (v in existing) { if (v !in s.linkedVerseIds) noteRepository.unlinkVerse(id, v) }
                }
                noteId
            }
        } finally {
            _state.update { it.copy(isSaving = false, isNew = false) }
        }
    }

    fun deleteNote() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }
}
