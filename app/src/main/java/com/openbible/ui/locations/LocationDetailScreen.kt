package com.openbible.ui.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openbible.data.db.dao.LocationVerseLink
import com.openbible.data.db.entity.BibleLocationEntity
import com.openbible.data.db.entity.LocationEventEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: String,
    onNavigateBack: () -> Unit,
    onOpenVerse: (translationId: String, bookId: Int, chapter: Int) -> Unit,
    onOpenParallels: (eventId: String) -> Unit = {},
    onAddNote: (title: String) -> Unit = {},
    viewModel: LocationViewModel = hiltViewModel()
) {
    LaunchedEffect(locationId) {
        viewModel.selectLocation(locationId)
    }

    val location by viewModel.selectedLocation.collectAsState()
    val verseLinks by viewModel.verseLinks.collectAsState()
    val events by viewModel.events.collectAsState()
    val defaultTranslation by viewModel.defaultTranslation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(location?.name ?: "Location") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onAddNote(location?.name ?: "Location") }) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Add note")
                    }
                }
            )
        }
    ) { padding ->
        if (location == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LocationDetailContent(
                location = location!!,
                verseLinks = verseLinks,
                events = events,
                defaultTranslation = defaultTranslation,
                onOpenVerse = onOpenVerse,
                onOpenParallels = onOpenParallels,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun LocationDetailContent(
    location: BibleLocationEntity,
    verseLinks: List<LocationVerseLink>,
    events: List<LocationEventEntity>,
    defaultTranslation: String = "kjv",
    onOpenVerse: (translationId: String, bookId: Int, chapter: Int) -> Unit,
    onOpenParallels: (eventId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Header ──────────────────────────────────────────────
        item {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = location.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (location.modernName != null && location.modernName != location.name) {
                Text(
                    text = "Also known as: ${location.modernName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(16.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(location.category) }
            )
            Spacer(Modifier.height(24.dp))
        }

        // ── Description ─────────────────────────────────────────
        item {
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = location.description,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
        }

        // ── Significance ────────────────────────────────────────
        if (location.significance != null) {
            item {
                Text(
                    text = "Biblical Significance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = location.significance,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Coordinates ─────────────────────────────────────────
        item {
            Text(
                text = "Coordinates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${"%.4f".format(location.latitude)}°N, ${"%.4f".format(location.longitude)}°E",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        // ── Biblical Events ──────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Events at this location (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "No events recorded for this location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        } else {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, defaultTranslation = defaultTranslation, onOpenVerse = onOpenVerse, onOpenParallels = onOpenParallels)
            }
        }

        // ── Spacer before verse links ─────────────────────────────
        item { Spacer(Modifier.height(8.dp)) }

        // ── Related Bible Verses ─────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Related Bible verses (${verseLinks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (verseLinks.isEmpty()) {
            item {
                Text(
                    text = "No verse references for this location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(verseLinks, key = { it.verseId }) { link ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onOpenVerse(link.translationId, link.bookId, link.chapter) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "${link.abbreviation} ${link.chapter}:${link.verse}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = link.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Event Card ─────────────────────────────────────────────────

private val eventCategoryColors = mapOf(
    "creation" to Color(0xFF4CAF50),
    "covenant" to Color(0xFF2196F3),
    "miracle" to Color(0xFFFF9800),
    "battle" to Color(0xFFF44336),
    "ministry" to Color(0xFF9C27B0),
    "prophecy" to Color(0xFF00BCD4),
    "judgment" to Color(0xFFFF5722),
    "birth" to Color(0xFFE91E63),
    "death" to Color(0xFF607D8B),
    "revelation" to Color(0xFFFFD700),
    "exile" to Color(0xFF795548)
)

@Composable
private fun EventCard(
    event: LocationEventEntity,
    defaultTranslation: String = "kjv",
    onOpenVerse: (translationId: String, bookId: Int, chapter: Int) -> Unit,
    onOpenParallels: (eventId: String) -> Unit = {}
) {
    val categoryColor = eventCategoryColors[event.category] ?: MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Era badge + category dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(categoryColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.era.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text(event.category, style = MaterialTheme.typography.labelSmall) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = categoryColor.copy(alpha = 0.15f),
                        labelColor = categoryColor
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            // Description
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // Reference + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.reference,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { onOpenParallels(event.id) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Parallels", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = { onOpenVerse(defaultTranslation, event.bookId, event.chapter) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Read", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
