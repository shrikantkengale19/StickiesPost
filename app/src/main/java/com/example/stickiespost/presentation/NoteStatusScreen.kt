package com.example.stickiespost.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.model.NoteStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteStatusScreen(
    notes: List<NoteItem>,
    onBack: () -> Unit,
    onAdvanceStatus: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var notePendingDeletion by remember { mutableStateOf<NoteItem?>(null) }
    val openNotes = notes.filter { it.status == NoteStatus.OPEN }
    val inProgressNotes = notes.filter { it.status == NoteStatus.IN_PROGRESS }
    val closedNotes = notes.filter { it.status == NoteStatus.CLOSED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Note status", fontWeight = FontWeight.Bold)
                        Text(
                            "Open, in progress, and closed notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Tap a note's status to advance it: Open, In progress, Closed, then Open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                StatusSectionHeader(title = "Open", count = openNotes.size)
            }
            if (openNotes.isEmpty()) {
                item { EmptyStatusMessage("No open notes.") }
            } else {
                items(openNotes, key = NoteItem::id) { note ->
                    NoteRow(
                        item = note,
                        onAdvanceStatus = { onAdvanceStatus(note.id) },
                        onSetReminder = null,
                        onDelete = { notePendingDeletion = note }
                    )
                }
            }

            item {
                StatusSectionHeader(title = "In progress", count = inProgressNotes.size)
            }
            if (inProgressNotes.isEmpty()) {
                item { EmptyStatusMessage("No notes in progress.") }
            } else {
                items(inProgressNotes, key = NoteItem::id) { note ->
                    NoteRow(
                        item = note,
                        onAdvanceStatus = { onAdvanceStatus(note.id) },
                        onSetReminder = null,
                        onDelete = { notePendingDeletion = note }
                    )
                }
            }

            item {
                StatusSectionHeader(title = "Closed", count = closedNotes.size)
            }
            if (closedNotes.isEmpty()) {
                item { EmptyStatusMessage("No closed notes.") }
            } else {
                items(closedNotes, key = NoteItem::id) { note ->
                    NoteRow(
                        item = note,
                        onAdvanceStatus = { onAdvanceStatus(note.id) },
                        onSetReminder = null,
                        onDelete = { notePendingDeletion = note }
                    )
                }
            }

        }
        notePendingDeletion?.let { note ->
            AlertDialog(
                onDismissRequest = { notePendingDeletion = null },
                title = { Text("Delete note?") },
                text = { Text("“${note.text}” will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteNote(note.id)
                            notePendingDeletion = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { notePendingDeletion = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStatusMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
