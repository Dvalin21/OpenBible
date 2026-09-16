package com.openbible.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
@Composable
fun BibleWithNotesScreen(
    initialTranslationId: String = "kjv",
    initialBookId: Int = 1,
    initialChapter: Int = 1,
    noteId: Long? = null,
    isNewNote: Boolean = false,
    notesOnLeft: Boolean = false,
    hasRail: Boolean = true,
    onToggleNotesSide: () -> Unit = {},
    onNavigateBack: () -> Unit,
    bibleViewModel: BibleViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    // ponytail: drag math needs PIXELS — dragAmount arrives in px, but the old
    // code divided by dp, so the bar outran the finger ~1.9x on this tablet
    val density = LocalDensity.current
    val contentWidthPx = with(density) {
        screenWidthDp.toPx() - (if (hasRail) 160.dp.toPx() else 0f)
    }

    // Side-swap is owned here so the top-bar button and divider chevron share one state.
    var notesOnLeftState by remember { mutableStateOf(notesOnLeft) }
    val toggleSide = { notesOnLeftState = !notesOnLeftState; onToggleNotesSide() }

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
                            onNavigateBack = { showNotes = false },
                            compact = true
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
        // Wide screen: side-by-side split. One shared 48dp action row lives
        // in the notes pane (Back saves, then swap) — no second top bar.
        var splitRatio by remember { mutableStateOf(0.5f) }

        Row(modifier = Modifier.fillMaxSize()) {
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
                if (verses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    NoteEditorScreen(
                        noteId = noteId,
                        initialTitle = if (noteId == null) currentRef else null,
                        initialLinkedVerseId = if (noteId == null) linkedVerseId else null,
                        onNavigateBack = onNavigateBack,
                        compact = true,
                        showSideSwap = true,
                        notesOnLeft = notesOnLeftState,
                        onToggleSide = toggleSide
                    )
                }
            }

            val divider = @Composable {
                var stripPressed by remember { mutableStateOf(false) }
                Box(modifier = Modifier.width(8.dp).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (stripPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant)
                            .pointerInput(notesOnLeftState) {
                                detectHorizontalDragGestures(
                                    onDragStart = { stripPressed = true },
                                    onDragEnd = { stripPressed = false },
                                    onDragCancel = { stripPressed = false }
                                ) { _, dragAmount ->
                                    val delta = dragAmount / contentWidthPx
                                    // ponytail: bar follows the finger — drag right
                                    // grows the LEFT pane (was inverted: the bar
                                    // fled from the finger on the notes-right side)
                                    val dir = if (notesOnLeftState) 1f else -1f
                                    splitRatio = (splitRatio + dir * delta).coerceIn(0.15f, 0.85f)
                                }
                            }
                    )
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ponytail: grip dots — the strip already drags, but
                        // nothing signalled that. Dots sit OUTSIDE the button
                        // so a grab starts a drag, not a side-swap.
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            )
                            if (it < 2) Spacer(modifier = Modifier.height(5.dp))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        IconButton(onClick = toggleSide) {
                            Icon(
                                if (notesOnLeftState) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                contentDescription = "Swap sides",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (notesOnLeftState) {
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
