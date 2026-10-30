package com.example.stickiespost.presentation

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.stickiespost.domain.model.RecurringTask
import com.example.stickiespost.ui.theme.NoteAccentText
import com.example.stickiespost.ui.theme.NoteRowCream
import com.example.stickiespost.ui.theme.NoteTypeGreen
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showComposer by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) viewModel.onNotificationPermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.clearScheduledMessage()
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3_000)
            viewModel.clearScheduledMessage()
        }
    }

    LaunchedEffect(state.notificationPermissionRequestId) {
        if (
            state.notificationPermissionRequestId > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openTasks = state.tasks.filterNot(RecurringTask::isFulfilled)
    val fulfilledTasks = state.tasks.filter(RecurringTask::isFulfilled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks", fontWeight = FontWeight.Bold)
                        Text(
                            "Recurring reminders",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposer = true },
                containerColor = NoteTypeGreen,
                contentColor = NoteAccentText
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            state.message?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            item { TaskSectionHeader("Open", openTasks.size) }
            if (openTasks.isEmpty()) {
                item { Text("No open tasks.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(openTasks, key = RecurringTask::id) { task ->
                    TaskRow(task) { viewModel.setFulfilled(task, true) }
                }
            }
            item { TaskSectionHeader("Fulfilled", fulfilledTasks.size) }
            if (fulfilledTasks.isEmpty()) {
                item {
                    Text(
                        "No fulfilled tasks yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(fulfilledTasks, key = RecurringTask::id) { task ->
                    TaskRow(task) { viewModel.setFulfilled(task, false) }
                }
            }
        }
    }

    if (showComposer) {
        ModalBottomSheet(
            onDismissRequest = { showComposer = false },
            sheetState = sheetState,
            containerColor = NoteRowCream
        ) {
            TaskComposer(
                isSaving = state.isSaving,
                onSave = { name, frequency, hour, minute, weekday, dayOfMonth ->
                    viewModel.addTask(
                        name,
                        frequency,
                        hour,
                        minute,
                        weekday,
                        dayOfMonth
                    ) {
                        coroutineScope.launch {
                            sheetState.hide()
                            showComposer = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TaskSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskRow(task: RecurringTask, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NoteRowCream)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isFulfilled, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${taskScheduleLabel(task)} · ${
                        java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT,
                            java.text.DateFormat.SHORT,
                            Locale.getDefault()
                        ).format(Date(task.nextReminderAtMillis))
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Reminder time",
                modifier = Modifier.size(18.dp),
                tint = NoteAccentText
            )
        }
    }
}

private fun taskScheduleLabel(task: RecurringTask): String = when (task.frequency) {
    RecurringTask.Frequency.DAILY -> "Daily"
    RecurringTask.Frequency.WEEKLY -> {
        val day = DayOfWeek.of(requireNotNull(task.weekday))
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
        "Every $day"
    }
    RecurringTask.Frequency.MONTHLY -> "Monthly on the ${task.dayOfMonth}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskComposer(
    isSaving: Boolean,
    onSave: (
        String,
        RecurringTask.Frequency,
        Int,
        Int,
        Int?,
        Int?
    ) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(RecurringTask.Frequency.DAILY) }
    var weekday by rememberSaveable { mutableStateOf(DayOfWeek.MONDAY.value) }
    var monthlyDay by rememberSaveable { mutableStateOf("1") }
    val currentTime = remember { Calendar.getInstance() }
    var hour by rememberSaveable { mutableStateOf(currentTime.get(Calendar.HOUR_OF_DAY)) }
    var minute by rememberSaveable { mutableStateOf(currentTime.get(Calendar.MINUTE)) }
    var showWeekdayPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Task name") },
            placeholder = { Text("e.g. Take medication") },
            singleLine = true
        )
        Text("Frequency", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringTask.Frequency.entries.forEach { option ->
                FilterChip(
                    selected = frequency == option,
                    onClick = { frequency = option },
                    label = { Text(option.label) }
                )
            }
        }

        when (frequency) {
            RecurringTask.Frequency.DAILY -> Unit
            RecurringTask.Frequency.WEEKLY -> {
                val weekdayName = DayOfWeek.of(weekday)
                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                TextButton(onClick = { showWeekdayPicker = true }) {
                    Text("Repeat every $weekdayName")
                }
            }
            RecurringTask.Frequency.MONTHLY -> {
                OutlinedTextField(
                    value = monthlyDay,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) monthlyDay = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Day of month (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, selectedHour, selectedMinute ->
                        hour = selectedHour
                        minute = selectedMinute
                    },
                    hour,
                    minute,
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Text(
                "  Reminder time: %02d:%02d".format(Locale.getDefault(), hour, minute)
            )
        }
        Button(
            onClick = {
                val parsedDay = monthlyDay.toIntOrNull()
                if (
                    name.isNotBlank() &&
                    (frequency != RecurringTask.Frequency.MONTHLY ||
                        parsedDay?.let { it in 1..31 } == true)
                ) {
                    onSave(
                        name,
                        frequency,
                        hour,
                        minute,
                        if (frequency == RecurringTask.Frequency.WEEKLY) weekday else null,
                        if (frequency == RecurringTask.Frequency.MONTHLY) {
                            requireNotNull(parsedDay)
                        } else {
                            null
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && name.isNotBlank() &&
                (frequency != RecurringTask.Frequency.MONTHLY ||
                    monthlyDay.toIntOrNull()?.let { it in 1..31 } == true)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Add task")
            }
        }
    }

    if (showWeekdayPicker) {
        AlertDialog(
            onDismissRequest = { showWeekdayPicker = false },
            title = { Text("Choose weekday") },
            text = {
                Column {
                    DayOfWeek.entries.forEach { day ->
                        TextButton(
                            onClick = {
                                weekday = day.value
                                showWeekdayPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeekdayPicker = false }) { Text("Close") }
            }
        )
    }

}
