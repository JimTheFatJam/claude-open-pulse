@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.openpulse.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.core.model.Reminder
import app.openpulse.data.prefs.UserSettings
import app.openpulse.reminders.Notifications
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.EmptyState
import app.openpulse.ui.components.InfoNote
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RemindersScreen(vm: OpenPulseViewModel, settings: UserSettings, onBack: () -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message("Notifications are blocked – reminders can't be shown")
    }
    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Reminder(title = "Measurement reminder", types = settings.checklistTypes, hour = 8, minute = 0, daysOfWeek = emptySet())
            }) { Icon(Icons.Outlined.Add, "Add reminder") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    InfoNote("Reminders are local notifications created on this device. They don't require an internet connection.")
                    if (!Notifications.canPost(context)) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = ::ensurePermission) { Text("Allow notifications") }
                    }
                }
            }
            if (reminders.isEmpty()) item { EmptyState("No reminders", "Add a reminder to be notified when it's time to measure.") }
            items(reminders, key = { it.id }) { r ->
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    headlineContent = { Text("${formatTime(r.hour, r.minute)} · ${r.title}") },
                    supportingContent = {
                        Text(daysLabel(r.daysOfWeek) + if (r.types.isNotEmpty()) " · " + r.types.joinToString { it.shortTitle } else "")
                    },
                    trailingContent = {
                        Switch(checked = r.enabled, onCheckedChange = { on -> if (on) ensurePermission(); vm.saveReminder(r.copy(enabled = on)) })
                    },
                )
            }
        }
    }

    editing?.let { r ->
        ReminderEditor(
            initial = r, settings = settings,
            onDismiss = { editing = null },
            onDelete = if (r.id != 0L) ({ vm.deleteReminder(r.id); editing = null }) else null,
            onSave = { ensurePermission(); vm.saveReminder(it); editing = null },
        )
    }
}

private fun formatTime(h: Int, m: Int) = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.of(h, m))

private fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun ReminderEditor(initial: Reminder, settings: UserSettings, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Reminder) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var days by remember { mutableStateOf(initial.daysOfWeek) }
    var types by remember { mutableStateOf(initial.types) }
    val time = rememberTimePickerState(initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New reminder" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                TimeInput(time)
                Text("Days", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Text(if (days.isEmpty()) "No days selected = every day" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Measurements", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.orderedEnabledTypes.forEach { t ->
                        FilterChip(selected = t in types, onClick = { types = if (t in types) types - t else types + t }, label = { Text(t.shortTitle) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(title = title.trim(), hour = time.hour, minute = time.minute, daysOfWeek = days, types = types)) }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
