@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.openpulse.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.units.ValueFormatter
import app.openpulse.data.prefs.UserSettings
import app.openpulse.data.prefs.zone
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.DemoBanner
import app.openpulse.ui.components.EmptyState
import app.openpulse.ui.components.EntryRow
import app.openpulse.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HistoryScreen(vm: OpenPulseViewModel, settings: UserSettings, onEdit: (Long) -> Unit, onRecord: (MeasurementType?) -> Unit) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val zone = profile.zone()
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var fromDay by rememberSaveable { mutableStateOf<Long?>(null) } // epoch day
    var toDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRange by remember { mutableStateOf(false) }

    val types = typeKeys.mapNotNull { MeasurementType.fromKey(it) }.toSet()
    val measurements = all.orEmpty()
    val filtered = remember(measurements, query, typeKeys, fromDay, toDay, zone, formatter) {
        val q = query.trim().lowercase()
        measurements.filter { m ->
            val day = Instant.ofEpochMilli(m.timestamp).atZone(zone).toLocalDate().toEpochDay()
            (types.isEmpty() || m.type in types) &&
                (fromDay == null || day >= fromDay!!) && (toDay == null || day <= toDay!!) &&
                (q.isEmpty() || matches(m, q, formatter, zone))
        }
    }
    val grouped = remember(filtered, zone) {
        filtered.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toList()
    }
    val today = LocalDate.now(zone)

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
        floatingActionButton = { FloatingActionButton(onClick = { onRecord(null) }) { Icon(Icons.Outlined.Add, "Record measurement") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (settings.demoMode) DemoBanner(Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                        placeholder = { Text("Search notes, tags, values, dates") },
                        shape = MaterialTheme.shapes.large,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { showRange = true },
                            label = {
                                Text(
                                    if (fromDay == null && toDay == null) "Any date"
                                    else "${fromDay?.let { Formats.date(LocalDate.ofEpochDay(it)) } ?: "…"} – ${toDay?.let { Formats.date(LocalDate.ofEpochDay(it)) } ?: "…"}",
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                        )
                        settings.orderedEnabledTypes.forEach { t ->
                            FilterChip(
                                selected = t.key in typeKeys,
                                onClick = { typeKeys = if (t.key in typeKeys) typeKeys - t.key else typeKeys + t.key },
                                label = { Text(t.shortTitle) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${filtered.size} of ${measurements.size} entries",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (query.isNotEmpty() || typeKeys.isNotEmpty() || fromDay != null || toDay != null) {
                            TextButton(onClick = { query = ""; typeKeys = emptyList(); fromDay = null; toDay = null }) { Text("Clear filters") }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        if (measurements.isEmpty()) "No measurements yet" else "No matching entries",
                        if (measurements.isEmpty()) "Your recorded measurements will appear here, newest first." else "Try a different search or filter.",
                    )
                }
            }
            grouped.forEach { (day, entries) ->
                stickyHeader(key = "h$day") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Formats.dayHeader(day, today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${entries.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(entries, key = { it.id }) { m -> EntryRow(m, formatter, zone, onClick = { onEdit(m.id) }) }
            }
        }
    }

    if (showRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = fromDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialSelectedEndDateMillis = toDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDay = state.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    toDay = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showRange = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { fromDay = null; toDay = null; showRange = false }) { Text("Any date") }
            },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f), title = { Text("Filter by date", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private fun matches(m: Measurement, q: String, formatter: ValueFormatter, zone: java.time.ZoneId): Boolean =
    m.note.lowercase().contains(q) ||
        m.tags.any { it.lowercase().contains(q) } ||
        m.type.title.lowercase().contains(q) ||
        formatter.measurement(m).lowercase().contains(q) ||
        Formats.dateTime(m.timestamp, zone).lowercase().contains(q)
