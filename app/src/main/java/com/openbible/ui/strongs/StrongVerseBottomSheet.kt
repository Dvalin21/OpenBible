package com.openbible.ui.strongs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Bottom sheet showing Strong's Concordance words for a specific verse.
 *
 * Each row shows the original Greek/Hebrew word, its transliteration,
 * and the Strong's number + definition. Tapping a row navigates to the
 * full detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrongVerseBottomSheet(
    verseId: Long,
    onOpenStrongDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StrongViewModel = hiltViewModel()
) {
    LaunchedEffect(verseId) {
        viewModel.loadWordsForVerse(verseId)
    }

    val words by viewModel.verseWords.collectAsState()
    val isLoading by viewModel.isLoadingVerseWords.collectAsState()

    ModalBottomSheet(onDismissRequest = {
        viewModel.clearVerseWords()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Strong's Concordance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (words.isEmpty()) {
                Text(
                    text = "No Strong's data available for this verse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(words, key = { "${it.strongNumber.number}-${it.originalWord}" }) { word ->
                        StrongWordCard(
                            wordInfo = word,
                            onClick = {
                                viewModel.clearVerseWords()
                                onOpenStrongDetail(word.strongNumber.number)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrongWordCard(
    wordInfo: WordStrongInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wordInfo.originalWord,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                wordInfo.transliteration?.let { trans ->
                    Text(
                        text = trans,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${wordInfo.strongNumber.number} — ${wordInfo.strongNumber.lemma}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = wordInfo.strongNumber.definition.take(80) + if (wordInfo.strongNumber.definition.length > 80) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}
