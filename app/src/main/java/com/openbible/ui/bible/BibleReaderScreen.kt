package com.openbible.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openbible.data.db.dao.CrossReferenceDisplay
import com.openbible.data.db.entity.VerseEntity

/**
 * Minimal embeddable Bible reader — no Scaffold, no external controls.
 * Used inside split-screen layouts where BibleScreen's full Scaffold would conflict.
 */
@Composable
fun BibleReaderScreen(
    verses: List<VerseEntity>,
    bookName: String,
    chapter: Int,
    translationLabel: String,
    crossReferenceMap: Map<Long, List<CrossReferenceDisplay>> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val expandedRefs = remember { mutableStateMapOf<Long, Boolean>() }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Text(
            text = "$bookName $chapter — $translationLabel",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(verses, key = { it.id }) { verse ->
                VerseLine(
                    verseNumber = verse.verse,
                    text = verse.text,
                    isRetro = false,
                    verseId = verse.id,
                    crossRefs = crossReferenceMap[verse.id] ?: emptyList(),
                    isExpanded = expandedRefs[verse.id] == true,
                    onToggleRefs = {
                        expandedRefs[verse.id] = expandedRefs[verse.id] != true
                    }
                )
            }
        }
    }
}

/**
 * Standalone verse line — reduced version of VerseLine from BibleScreen.
 */
@Composable
private fun VerseLine(
    verseNumber: Int,
    text: String,
    isRetro: Boolean,
    verseId: Long = 0L,
    crossRefs: List<CrossReferenceDisplay> = emptyList(),
    isExpanded: Boolean = false,
    onToggleRefs: () -> Unit = {}
) {
    Column {
        Text(
            text = "$verseNumber $text",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
        )

        if (crossRefs.isNotEmpty()) {
            TextButton(
                onClick = onToggleRefs,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            ) {
                Text(
                    text = if (isExpanded) "▲ ${crossRefs.size} cross-references"
                           else "▼ ${crossRefs.size} cross-references",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 2.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    crossRefs.take(5).forEach { ref ->
                        val range = if (ref.toVerseEnd != null && ref.toVerseEnd != ref.toVerseStart)
                            "${ref.toVerseStart}-${ref.toVerseEnd}"
                        else ref.toVerseStart.toString()
                        Text(
                            text = "${ref.toBookAbbreviation} ${ref.toChapter}:$range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (crossRefs.size > 5) {
                        Text("+ ${crossRefs.size - 5} more", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
