package com.openbible.ui.notes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.NotebookEntity
import com.openbible.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotebookListViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : androidx.lifecycle.ViewModel() {

    val notebooks: StateFlow<List<NotebookEntity>> = noteRepository.getAllNotebooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = noteRepository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNotebook(name: String, color: Int) {
        viewModelScope.launch { noteRepository.createNotebook(name, color) }
    }

    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch { noteRepository.deleteNotebook(notebook) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookListScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onBack: () -> Unit,
    viewModel: NotebookListViewModel = hiltViewModel()
) {
    val notebooks by viewModel.notebooks.collectAsState()
    val allNotes by viewModel.notes.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedNotebookId by remember { mutableStateOf<Long?>(null) }

    val displayNotes = if (selectedNotebookId == null) allNotes
    else allNotes.filter { it.notebookId == selectedNotebookId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedNotebookId == null) "Notebooks" else "Notes") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedNotebookId != null) selectedNotebookId = null
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedNotebookId != null) {
                        IconButton(onClick = onNewNote) {
                            Icon(Icons.Default.Add, contentDescription = "New note")
                        }
                    } else {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New notebook")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedNotebookId == null) {
            // Notebook list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (notebooks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.NoteAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No notebooks yet", style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = { showCreateDialog = true }) { Text("Create one") }
                            }
                        }
                    }
                }
                items(notebooks) { nb ->
                    NotebookCard(
                        notebook = nb,
                        onClick = { selectedNotebookId = nb.id },
                        onDelete = { viewModel.deleteNotebook(nb) }
                    )
                }
            }
        } else {
            // Notes in selected notebook
            val notebook = notebooks.find { it.id == selectedNotebookId }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (displayNotes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No notes in this notebook", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                items(displayNotes) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onDelete = { viewModel.deleteNote(note) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNotebookDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                viewModel.createNotebook(name, color)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun NotebookCard(
    notebook: NotebookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(notebook.color).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Color(notebook.color),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = notebook.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val preview = when (note.penMode) {
                com.openbible.data.model.PenMode.INK -> "[Ink drawing]"
                com.openbible.data.model.PenMode.BOTH -> "[Text + Ink]"
                com.openbible.data.model.PenMode.TEXT -> note.contentText?.take(80) ?: ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Updated: ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(note.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colors = listOf(
        0xFF1A237E.toInt(), 0xFFB71C1C.toInt(), 0xFF2E7D32.toInt(),
        0xFFE65100.toInt(), 0xFF6A1B9A.toInt(), 0xFF00838F.toInt()
    )
    var selectedColor by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Notebook") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Color:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(2.dp)
                                .clickable { selectedColor = color }
                                .then(
                                    if (isSelected) Modifier
                                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                    else Modifier
                                )
                                .padding(2.dp)
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(Color(color))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, selectedColor) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
