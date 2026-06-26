package com.openbible.ui.strongs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrongDetailScreen(
    strongNumber: String,
    onNavigateBack: () -> Unit,
    onOpenVerse: (String, Int, Int) -> Unit,
    viewModel: StrongViewModel = hiltViewModel()
) {
    LaunchedEffect(strongNumber) {
        viewModel.loadDetail(strongNumber)
    }

    val detail by viewModel.detail.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strong's $strongNumber") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val data = detail
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = data.number.number,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = data.number.language,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${data.number.lemma}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "(${data.number.transliteration})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (data.number.pronunciation != null) {
                            Text(
                                text = "Pronunciation: ${data.number.pronunciation}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (data.number.partOfSpeech != null) {
                            Spacer(Modifier.height(4.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(data.number.partOfSpeech) }
                            )
                        }
                    }
                }

                // Definition
                Text(
                    text = "Definition",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    text = data.number.definition,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Derivation
                data.number.derivation?.let { derivation ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Derivation",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Text(
                        text = derivation,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Usage count
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Appears ${data.number.usageCount} times",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Verse occurrences
                if (data.verseLinks.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Occurrences (${data.verseLinks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    // For now, just show verse IDs — proper verse text lookup is a future enhancement
                    data.verseLinks.take(20).forEach { link ->
                        Text(
                            text = "Verse ${link.verseId} (word ${link.wordPosition}: ${link.originalWord})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                    if (data.verseLinks.size > 20) {
                        Text(
                            text = "… and ${data.verseLinks.size - 20} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
