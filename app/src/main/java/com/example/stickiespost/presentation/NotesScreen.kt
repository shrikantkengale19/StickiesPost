package com.example.stickiespost.presentation

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.stickiespost.R
import com.example.stickiespost.domain.model.NoteCategory
import com.example.stickiespost.domain.model.NoteItem
import com.example.stickiespost.domain.model.NoteStatus
import com.example.stickiespost.ui.theme.ClosedNoteRow
import com.example.stickiespost.ui.theme.ClosedStatusAccent
import com.example.stickiespost.ui.theme.InProgressNoteRow
import com.example.stickiespost.ui.theme.InProgressStatusAccent
import com.example.stickiespost.ui.theme.NoteAccentText
import com.example.stickiespost.ui.theme.NoteChipOutline
import com.example.stickiespost.ui.theme.NoteRowCream
import com.example.stickiespost.ui.theme.NoteTypeGreen
import com.example.stickiespost.ui.theme.OpenNoteRow
import com.example.stickiespost.ui.theme.OpenStatusAccent
import com.example.stickiespost.ui.theme.SelectedTypePink
import com.example.stickiespost.ui.theme.StickiesPostTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenTasks: () -> Unit,
    currentUserEmail: String? = null,
    onSignOut: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    NotesScreenContent(
        state = state,
        onOpenTasks = onOpenTasks,
        currentUserEmail = currentUserEmail,
        onSignOut = onSignOut,
        callbacks = NotesScreenCallbacks(
            processCommand = viewModel::processCommand,
            addNote = viewModel::addNote,
            selectCategory = viewModel::selectCategory,
            advanceStatus = viewModel::advanceStatus,
            setReminder = viewModel::setReminder,
            deleteNote = viewModel::deleteNote,
            saveApiKey = viewModel::saveApiKey,
            removeApiKey = viewModel::removeApiKey,
            dismissMessage = viewModel::dismissMessage,
            showError = viewModel::showError,
            onNotificationPermissionDenied = viewModel::onNotificationPermissionDenied
        )
    )
}

private data class NotesScreenCallbacks(
    val processCommand: (String, (() -> Unit)?) -> Unit,
    val addNote: (String, (() -> Unit)?) -> Unit,
    val selectCategory: (NoteCategory) -> Unit,
    val advanceStatus: (Long) -> Unit,
    val setReminder: (Long, Long) -> Unit,
    val deleteNote: (Long) -> Unit,
    val saveApiKey: (String) -> Boolean,
    val removeApiKey: () -> Unit,
    val dismissMessage: () -> Unit,
    val showError: (String) -> Unit,
    val onNotificationPermissionDenied: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NotesScreenContent(
    state: NotesUiState,
    onOpenTasks: () -> Unit,
    currentUserEmail: String?,
    onSignOut: (() -> Unit)?,
    callbacks: NotesScreenCallbacks
) {
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStatusScreen by rememberSaveable { mutableStateOf(false) }
    var showAddNoteSheet by rememberSaveable { mutableStateOf(false) }
    val composerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismissComposer = {
        coroutineScope.launch {
            composerSheetState.hide()
            showAddNoteSheet = false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                draft = spokenText
                callbacks.processCommand(spokenText) {
                    draft = ""
                    dismissComposer()
                }
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) callbacks.onNotificationPermissionDenied()
    }

    LaunchedEffect(state.reminderPermissionRequestId) {
        if (
            state.reminderPermissionRequestId > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showStatusScreen) {
        NoteStatusScreen(
            notes = state.notes,
            onBack = { showStatusScreen = false },
            onAdvanceStatus = callbacks.advanceStatus,
            onDeleteNote = callbacks.deleteNote
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("StickiesPost", fontWeight = FontWeight.Bold)
                            Text(
                                "Your flying notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { showStatusScreen = true }) {
                            Text("Status")
                        }
                        TextButton(onClick = onOpenTasks) {
                            Text("Tasks")
                        }
                        if (currentUserEmail != null && onSignOut != null) {
                            TextButton(onClick = onSignOut) {
                                Text("Sign out")
                            }
                        }
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.semantics {
                                contentDescription = if (state.apiKeyConfigured) {
                                    "Settings and Gemini API key"
                                } else {
                                    "Add Gemini API key"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Image(
                        painter = painterResource(R.drawable.taskslist_banner_short),
                        contentDescription = "Notes, tasks, and reminders",
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }

                state.error?.let { message ->
                    StatusMessage(
                        message = message,
                        isError = true,
                        onDismiss = callbacks.dismissMessage
                    )
                }
                state.message?.let { message ->
                    StatusMessage(
                        message = message,
                        isError = false,
                        onDismiss = callbacks.dismissMessage
                    )
                }

                Text(
                    "Your lists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NoteCategory.entries.forEach { category ->
                            FilterChip(
                                selected = state.selectedCategory == category,
                                onClick = { callbacks.selectCategory(category) },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = NoteRowCream,
                                    labelColor = NoteAccentText,
                                    selectedContainerColor = SelectedTypePink,
                                    selectedLabelColor = NoteAccentText
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.selectedCategory == category) {
                                        SelectedTypePink
                                    } else {
                                        NoteChipOutline
                                    }
                                )
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { showAddNoteSheet = true },
                        modifier = Modifier
                            .padding(end = 8.dp, top = 8.dp)
                            .size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NoteTypeGreen,
                            contentColor = NoteAccentText
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add a note"
                        )
                    }
                }

                val visibleNotes = remember(state.notes, state.selectedCategory) {
                    if (state.selectedCategory == NoteCategory.ALL) state.notes
                    else state.notes.filter { it.category == state.selectedCategory }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visibleNotes.isEmpty()) {
                        item {
                            Text(
                                "Nothing here yet. Speak or type a note below.",
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(visibleNotes, key = NoteItem::id) { item ->
                            NoteRow(
                                item = item,
                                onAdvanceStatus = { callbacks.advanceStatus(item.id) },
                                onSetReminder = {
                                    showReminderPicker(context, item, callbacks.setReminder)
                                }
                            )
                        }
                    }
                }

            }
        }
    }

    if (showAddNoteSheet && !showStatusScreen) {
        ModalBottomSheet(
            onDismissRequest = { showAddNoteSheet = false },
            sheetState = composerSheetState,
            containerColor = NoteRowCream
        ) {
            NoteComposer(
                draft = draft,
                onDraftChange = { draft = it },
                isProcessing = state.isProcessing,
                isSavingNote = state.isSavingNote,
                onSpeak = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        .putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            "Speak a note or ask for a list"
                        )
                    try {
                        speechLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        callbacks.showError("Speech recognition is not available on this device.")
                    }
                },
                onSave = {
                    callbacks.addNote(draft) {
                        draft = ""
                        dismissComposer()
                    }
                },
                onAskGemini = {
                    callbacks.processCommand(draft) {
                        draft = ""
                        dismissComposer()
                    }
                }
            )
        }
    }

    if (showSettings) {
        ApiKeyDialog(
            keyConfigured = state.apiKeyConfigured,
            error = state.error,
            onDismiss = { showSettings = false },
            onSave = { key ->
                if (callbacks.saveApiKey(key)) showSettings = false
            },
            onRemove = callbacks.removeApiKey
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesScreenPreview() {
    StickiesPostTheme {
        NotesScreenContent(
            state = NotesUiState(
                notes = listOf(
                    NoteItem(
                        id = 1,
                        text = "Pick up groceries",
                        category = NoteCategory.GROCERIES,
                        createdAtMillis = System.currentTimeMillis(),
                        reminderAtMillis = null,
                        status = NoteStatus.OPEN
                    )
                )
            ),
            onOpenTasks = {},
            currentUserEmail = null,
            onSignOut = null,
            callbacks = NotesScreenCallbacks(
                processCommand = { _, _ -> },
                addNote = { _, _ -> },
                selectCategory = {},
                advanceStatus = {},
                setReminder = { _, _ -> },
                deleteNote = {},
                saveApiKey = { true },
                removeApiKey = {},
                dismissMessage = {},
                showError = {},
                onNotificationPermissionDenied = {}
            )
        )
    }
}

@Composable
private fun NoteComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isProcessing: Boolean,
    isSavingNote: Boolean,
    onSpeak: () -> Unit,
    onSave: () -> Unit,
    onAskGemini: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Add a note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Type a note or ask Gemini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = CircleShape, color = NoteTypeGreen) {
                    IconButton(onClick = onSpeak, enabled = !isProcessing) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak",
                            tint = NoteAccentText
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What do you need to remember?") },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpenStatusAccent,
                    unfocusedBorderColor = NoteChipOutline.copy(alpha = 0.55f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!isProcessing && draft.isNotBlank()) onAskGemini()
                    }
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OpenStatusAccent,
                    contentColor = Color.White
                ),
                enabled = !isProcessing && draft.isNotBlank(),
                onClick = onSave
            ) {
                if (isSavingNote) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Note")
                }
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                enabled = !isProcessing && draft.isNotBlank(),
                onClick = onAskGemini
            ) {
                if (isProcessing && !isSavingNote) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Ask Gemini")
                }
            }
        }
    }
}

@Composable
internal fun NoteRow(
    item: NoteItem,
    onAdvanceStatus: () -> Unit,
    onSetReminder: (() -> Unit)?,
    onDelete: (() -> Unit)? = null
) {
    val rowColor = when (item.status) {
        NoteStatus.OPEN -> OpenNoteRow
        NoteStatus.IN_PROGRESS -> InProgressNoteRow
        NoteStatus.CLOSED -> ClosedNoteRow
    }
    val statusColor = when (item.status) {
        NoteStatus.OPEN -> OpenStatusAccent
        NoteStatus.IN_PROGRESS -> InProgressStatusAccent
        NoteStatus.CLOSED -> ClosedStatusAccent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = rowColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val statusInitials = when (item.status) {
                NoteStatus.OPEN -> "O"
                NoteStatus.IN_PROGRESS -> "IP"
                NoteStatus.CLOSED -> "C"
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Status: ${item.status.label}. Tap to change."
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Change note status",
                        onClick = onAdvanceStatus
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(statusColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        statusInitials,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.status == NoteStatus.CLOSED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        item.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == NoteStatus.CLOSED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else MaterialTheme.colorScheme.onSurface
                    )
                    item.reminderAtMillis?.let {
                        Text(
                            DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT,
                                Locale.getDefault()
                            ).format(Date(it)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (onSetReminder != null) {
                IconButton(
                    onClick = onSetReminder,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = if (item.reminderAtMillis == null) {
                            "Set reminder"
                        } else {
                            "Change reminder"
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (item.reminderAtMillis == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete note",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun showReminderPicker(
    context: Context,
    note: NoteItem,
    onSetReminder: (Long, Long) -> Unit
) {
    val now = java.util.Calendar.getInstance()
    val initial = java.util.Calendar.getInstance().apply {
        timeInMillis = note.reminderAtMillis ?: now.timeInMillis
    }

    DatePickerDialog(
        context,
        { _, year, month, day ->
            val dateMatchesInitial = initial.get(java.util.Calendar.YEAR) == year &&
                initial.get(java.util.Calendar.MONTH) == month &&
                initial.get(java.util.Calendar.DAY_OF_MONTH) == day
            val dateMatchesToday = now.get(java.util.Calendar.YEAR) == year &&
                now.get(java.util.Calendar.MONTH) == month &&
                now.get(java.util.Calendar.DAY_OF_MONTH) == day
            val hour = when {
                dateMatchesInitial -> initial.get(java.util.Calendar.HOUR_OF_DAY)
                dateMatchesToday -> now.get(java.util.Calendar.HOUR_OF_DAY)
                else -> 9
            }
            val minute = when {
                dateMatchesInitial -> initial.get(java.util.Calendar.MINUTE)
                dateMatchesToday -> now.get(java.util.Calendar.MINUTE)
                else -> 0
            }

            TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    val reminderAt = java.util.Calendar.getInstance().apply {
                        set(year, month, day, selectedHour, selectedMinute, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onSetReminder(note.id, reminderAt)
                },
                hour,
                minute,
                android.text.format.DateFormat.is24HourFormat(context)
            ).show()
        },
        initial.get(java.util.Calendar.YEAR),
        initial.get(java.util.Calendar.MONTH),
        initial.get(java.util.Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.minDate = now.timeInMillis
    }.show()
}

@Composable
private fun StatusMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    keyConfigured: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter your Google AI Studio API key. It is encrypted on this device and sent directly to Google Gemini.")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (keyConfigured) {
                    Text(
                        "A key is already saved on this device.",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiKey) },
                enabled = apiKey.isNotBlank()
            ) { Text("Save key") }
        },
        dismissButton = {
            Row {
                if (keyConfigured) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
