package com.openbible.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openbible.data.db.entity.BookEntity

/**
 * State holder for book/chapter selection.
 */
class BookChapterSelectorState(
    initialBookId: Int = 1,
    initialChapter: Int = 1
) {
    var selectedBookId by mutableStateOf(initialBookId)
    var selectedChapter by mutableStateOf(initialChapter)
}

/**
 * Two-pane book/chapter selector.
 *
 * Left pane: scrollable list of books filtered by [books] flow.
 * Right pane: grid of chapters for the selected book.
 * Tap a chapter to fire [onChapterSelected].
 *
 * Shows loading state while flows are empty.
 */
@Composable
fun BookChapterSelector(
    translationId: String = "kjv",
    state: BookChapterSelectorState = remember { BookChapterSelectorState() },
    onChapterSelected: (bookId: Int, chapter: Int) -> Unit
) {
    // We need a BibleDao — look it up via LocalContext
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.openbible.OpenBibleApp
    val bibleDao = app.database.bibleDao()

    val books by bibleDao.getBooks(translationId).collectAsState(initial = emptyList())
    val chapters by bibleDao.getChapters(translationId, state.selectedBookId)
        .collectAsState(initial = emptyList())

    if (books.isEmpty() && chapters.isEmpty()) {
        // Loading state
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 500.dp)
    ) {
        // ── Left Pane: Book List ────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp)),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(books, key = { it.id }) { book ->
                val isSelected = book.id == state.selectedBookId
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.selectedBookId = book.id }
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // ── Right Pane: Chapter Grid ────────────────────────
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = "${chapters.size} chapter${if (chapters.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp)),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gridItems(chapters, key = { it }) { chapter ->
                    val isSelected = chapter == state.selectedChapter
                            && state.selectedBookId == state.selectedBookId
                    val selectedBg = if (isSelected) MaterialTheme.colorScheme.primary
                                     else Color.Transparent

                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                color = selectedBg,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onChapterSelected(state.selectedBookId, chapter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chapter.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
