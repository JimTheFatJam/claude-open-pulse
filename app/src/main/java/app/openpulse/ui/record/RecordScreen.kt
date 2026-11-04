@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.openpulse.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openpulse.OpenPulseApp
import app.openpulse.core.model.Channel
import app.openpulse.core.model.ChannelSpec
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.data.prefs.UserSettings
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.components.TypeChips
import app.openpulse.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val suggestedTags = listOf("Morning", "Evening", "Resting", "After exercise", "Fasting", "After meal", "Seated", "Standing", "Stressed", "Unwell", "Medication taken")

@Composable
fun RecordScreen(type: MeasurementType?, id: Long?, settings: UserSettings, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as OpenPulseApp
    val vm: RecordViewModel = viewModel(key = "record-${type?.key}-$id", factory = RecordViewModel.factory(app.container, type, id))
    val s by vm.state.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var customTag by remember { mutableStateOf("") }

    LaunchedEffect(s.saved) { if (s.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.editingId != null) "Edit measurement" else "Record measurement") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, "Cancel") } },
                actions = {
                    if (s.editingId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete entry") }
                    TextButton(onClick = vm::save, enabled = !s.loading) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (s.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                if (s.editingId == null) {
                    val types = settings.orderedEnabledTypes.ifEmpty { MeasurementType.entries.toList() }
                    TypeChips(types, s.type, vm::setType)
                    Spacer(Modifier.height(12.dp))
                }
                Text(s.type.title, style = MaterialTheme.typography.headlineSmall)
                Text(s.type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.isDemo) Text("This is a demo entry.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(16.dp))

                s.type.channels.forEachIndexed { index, spec ->
                    ValueInput(
                        spec = spec,
                        unit = vm.unitOf(s.type, spec.channel),
                        input = s.inputs[spec.channel] ?: ChannelInput(),
                        onChange = { vm.setInput(spec.channel, it) },
                        error = s.errors.firstOrNull { it.channel == spec.channel }?.message,
                        onUnitChange = { vm.changeUnit(spec.channel, it) },
                        // Only the first field of a kind offers the unit menu.
                        showUnitMenu = s.type.channels.indexOfFirst { it.unitKind == spec.unitKind } == index,
                        imeAction = if (index == s.type.channels.lastIndex) ImeAction.Done else ImeAction.Next,
                        onDone = vm::save,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Text("Date & time", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDate = true }) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(Formats.date(s.date))
                    }
                    OutlinedButton(onClick = { showTime = true }) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(s.time))
                    }
                    TextButton(onClick = vm::setNow) { Text("Now") }
                }
                Text("Time zone: ${s.zone.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.errors.filter { it.channel == null }.forEach {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))

                Text("Context", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (suggestedTags + s.tags.filter { it !in suggestedTags }).forEach { tag ->
                        FilterChip(
                            selected = tag in s.tags, onClick = { vm.toggleTag(tag) }, label = { Text(tag) },
                            leadingIcon = if (tag in s.tags) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
                        )
                    }
                }
                OutlinedTextField(
                    value = customTag, onValueChange = { customTag = it.take(40) },
                    label = { Text("Add tag") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.toggleTag(customTag); customTag = "" }),
                    trailingIcon = { if (customTag.isNotBlank()) TextButton(onClick = { vm.toggleTag(customTag); customTag = "" }) { Text("Add") } },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = s.note, onValueChange = vm::setNote,
                    label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (s.editingId != null) "Save changes" else "Save measurement")
                }
                Spacer(Modifier.height(10.dp))
                InfoNote("Entries are never merged or overwritten – each save creates its own record, even on the same day.")
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showDate) {
        val todayUtc = LocalDate.now(s.zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        val state = rememberTimePickerState(s.time.hour, s.time.minute, is24)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { vm.setTime(LocalTime.of(state.hour, state.minute)); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This measurement will be permanently removed. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ValueInput(
    spec: ChannelSpec,
    unit: MeasurementUnit,
    input: ChannelInput,
    onChange: (ChannelInput) -> Unit,
    error: String?,
    onUnitChange: (MeasurementUnit) -> Unit,
    showUnitMenu: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    val label = spec.label + if (!spec.required) " (optional)" else ""
    val kb = KeyboardOptions(keyboardType = if (unit == MeasurementUnit.STEPS || unit == MeasurementUnit.BPM || unit == MeasurementUnit.MMHG) KeyboardType.Number else KeyboardType.Decimal, imeAction = imeAction)
    val actions = KeyboardActions(onDone = { onDone() })
    val options = MeasurementUnit.optionsFor(unit.kind)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (unit) {
            MeasurementUnit.FEET_INCHES -> {
                OutlinedTextField(input.main, { onChange(input.copy(main = it)) }, Modifier.weight(1f), label = { Text("$label – ft") }, suffix = { Text("ft") }, isError = error != null, singleLine = true, keyboardOptions = kb.copy(imeAction = ImeAction.Next))
                OutlinedTextField(input.sub, { onChange(input.copy(sub = it)) }, Modifier.weight(1f), label = { Text("in") }, suffix = { Text("in") }, isError = error != null, singleLine = true, keyboardOptions = kb, keyboardActions = actions)
            }
            MeasurementUnit.HOURS -> {
                OutlinedTextField(input.main, { onChange(input.copy(main = it)) }, Modifier.weight(1f), label = { Text(label) }, suffix = { Text("h") }, isError = error != null, singleLine = true, keyboardOptions = kb.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                OutlinedTextField(input.sub, { onChange(input.copy(sub = it)) }, Modifier.weight(1f), label = { Text("Minutes") }, suffix = { Text("min") }, isError = error != null, singleLine = true, keyboardOptions = kb.copy(keyboardType = KeyboardType.Number), keyboardActions = actions)
            }
            else -> OutlinedTextField(
                input.main, { onChange(input.copy(main = it)) }, Modifier.weight(1f),
                label = { Text(label) }, suffix = { Text(unit.symbol) }, isError = error != null, singleLine = true,
                keyboardOptions = kb, keyboardActions = actions,
                supportingText = if (error != null) ({ Text(error) }) else null,
            )
        }
        if (showUnitMenu && options.size > 1) UnitMenu(unit, options, onUnitChange)
    }
    if (error != null && (unit == MeasurementUnit.FEET_INCHES || unit == MeasurementUnit.HOURS)) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UnitMenu(current: MeasurementUnit, options: List<MeasurementUnit>, onSelect: (MeasurementUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.widthIn(max = 120.dp)) {
        OutlinedTextField(
            value = current.symbol, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { u ->
                DropdownMenuItem(text = { Text(u.symbol) }, onClick = { onSelect(u); expanded = false })
            }
        }
    }
}
