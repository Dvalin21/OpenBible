package com.openbible.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openbible.ui.bible.BibleReaderScreen
import com.openbible.ui.bible.BibleViewModel

/**
 * Split-screen layout: Bible reader on one side, note editor on the other.
 *
 * Supports toggling the note panel to the left or right side.
 * The divider is draggable to adjust the split ratio.
 * Adapts to screen width — on narrow phones it shows one at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleWithNotesScreen(
    initialTranslationId: String = "kjv",
    initialBookId: Int = 1,
    initialChapter: Int = 1,
    noteId: Long? = null,
    isNewNote: Boolean = false,
    notesOnLeft: Boolean = false,
    onToggleNotesSide: () -> Unit = {},
    onNavigateBack: () -> Unit,
    bibleViewModel: BibleViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp

    // Load Bible data
    val verses by bibleViewModel.verses.collectAsState()
    val currentBook by bibleViewModel.currentBook.collectAsState()
    val selectedChapter by bibleViewModel.selectedChapter.collectAsState()
    val selectedTranslationId by bibleViewModel.selectedTranslationId.collectAsState()
    val crossReferenceMap by bibleViewModel.crossReferenceMap.collectAsState()

    // Passage context for seeding a new note (Study Mode ties notes to the chapter read)
    val currentRef = "${currentBook?.name ?: "Bible"} $selectedChapter"
    var linkedVerseId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedTranslationId, selectedChapter, currentBook?.id) {
        val bookId = currentBook?.id ?: return@LaunchedEffect
        linkedVerseId = bibleViewModel.getFirstVerseId(selectedTranslationId, bookId, selectedChapter)
    }

    // Navigate to initial chapter
    LaunchedEffect(Unit) {
        bibleViewModel.selectTranslation(initialTranslationId)
        bibleViewModel.selectBook(initialBookId)
        bibleViewModel.selectChapter(initialChapter)
    }

    // Only show split if screen is wide enough (>= 600dp = 7" tablet)
    val canSplit = screenWidthDp >= 600.dp

    if (!canSplit) {
        var showNotes by remember { mutableStateOf(isNewNote || noteId != null) }

        Box(modifier = modifier.fillMaxSize()) {
                if (showNotes) {
                    if (linkedVerseId == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        NoteEditorScreen(
                            noteId = noteId,
                            initialTitle = if (noteId == null) currentRef else null,
                            initialLinkedVerseId = if (noteId == null) linkedVerseId else null,
                            onNavigateBack = { showNotes = false }
                        )
                    }
                } else {
                BibleReaderScreen(
                    verses = verses,
                    bookName = currentBook?.name ?: "Bible",
                    bookNumber = currentBook?.number ?: 1,
                    chapter = selectedChapter,
                    translationLabel = selectedTranslationId.uppercase(),
                    crossReferenceMap = crossReferenceMap
                )
            }

            FloatingActionButton(
                onClick = { showNotes = !showNotes },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (showNotes) Icons.Default.Book else Icons.Default.EditNote,
                    contentDescription = if (showNotes) "Show Bible" else "Show Notes"
                )
            }
        }
    } else {
        // Wide screen: side-by-side split
        var splitRatio by remember { mutableStateOf(0.5f) }

        Row(modifier = modifier.fillMaxSize()) {
            val biblePane = @Composable {
                BibleReaderScreen(
                    verses = verses,
                    bookName = currentBook?.name ?: "Bible",
                    bookNumber = currentBook?.number ?: 1,
                    chapter = selectedChapter,
                    translationLabel = selectedTranslationId.uppercase(),
                    crossReferenceMap = crossReferenceMap
                )
            }

            val notesPane = @Composable {
                if (linkedVerseId == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    NoteEditorScreen(
                        noteId = noteId,
                        initialTitle = if (noteId == null) currentRef else null,
                        initialLinkedVerseId = if (noteId == null) linkedVerseId else null,
                        onNavigateBack = onNavigateBack
                    )
                }
            }

            val divider = @Composable {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val delta = dragAmount / screenWidthDp.value
                                splitRatio = (splitRatio + delta).coerceIn(0.15f, 0.85f)
                            }
                        }
                ) {
                    IconButton(
                        onClick = onToggleNotesSide,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            if (notesOnLeft) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                            contentDescription = "Swap sides",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (notesOnLeft) {
                Box(modifier = Modifier.weight(splitRatio)) { notesPane() }
                divider()
                Box(modifier = Modifier.weight(1f - splitRatio)) { biblePane() }
            } else {
                Box(modifier = Modifier.weight(1f - splitRatio)) { biblePane() }
                divider()
                Box(modifier = Modifier.weight(splitRatio)) { notesPane() }
            }
        }
    }
}
