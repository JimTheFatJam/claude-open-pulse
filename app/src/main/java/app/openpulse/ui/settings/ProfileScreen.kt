@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.core.health.BodyMetrics
import app.openpulse.core.model.Channel
import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Sex
import app.openpulse.core.model.UnitKind
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.data.prefs.UserSettings
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.format.Formats
import app.openpulse.ui.record.ChannelInput
import app.openpulse.ui.record.RecordViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun ProfileScreen(vm: OpenPulseViewModel, settings: UserSettings, onBack: () -> Unit) {
    val saved by vm.profile.collectAsStateWithLifecycle()
    val heightUnit = settings.units.unitFor(UnitKind.HEIGHT)
    val weightUnit = settings.units.unitFor(UnitKind.MASS)
    var dob by remember(saved) { mutableStateOf(saved.dateOfBirth) }
    var sex by remember(saved) { mutableStateOf(saved.sex) }
    var height by remember(saved, heightUnit) { mutableStateOf(saved.heightCm?.let { RecordViewModel.toInput(it, heightUnit) } ?: ChannelInput()) }
    var weight by remember(saved, weightUnit) { mutableStateOf(saved.initialWeightKg?.let { RecordViewModel.toInput(it, weightUnit) } ?: ChannelInput()) }
    var zoneId by remember(saved) { mutableStateOf(saved.timeZoneId) }
    var goals by remember(saved) { mutableStateOf(saved.goals) }
    var notes by remember(saved) { mutableStateOf(saved.medicalNotes) }
    var showDob by remember { mutableStateOf(false) }
    var showZone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val h = if (height.main.isBlank() && height.sub.isBlank()) null else RecordViewModel.parse(height, heightUnit)
        val w = if (weight.main.isBlank()) null else RecordViewModel.parse(weight, weightUnit)
        val hSpec = MeasurementType.HEIGHT.primarySpec
        val wSpec = MeasurementType.WEIGHT.primarySpec
        error = when {
            (height.main.isNotBlank() || height.sub.isNotBlank()) && (h == null || h < hSpec.minValid || h > hSpec.maxValid) -> "Height is not valid"
            weight.main.isNotBlank() && (w == null || w < wSpec.minValid || w > wSpec.maxValid) -> "Weight is not valid"
            else -> null
        }
        if (error == null) {
            vm.updateProfile(HealthProfile(dob, sex, h, w, zoneId, goals.trim(), notes.trim()))
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoNote("Your profile is stored only on this device. It is used to calculate BMI and age and to decide which general reference information applies.")
                Text("Date of birth", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDob = true }) { Text(dob?.let { Formats.date(it) } ?: "Not set") }
                    Spacer(Modifier.weight(1f))
                    dob?.let { d -> BodyMetrics.ageYears(d, LocalDate.now())?.let { Text("Age: $it years", style = MaterialTheme.typography.bodyLarge) } }
                    if (dob != null) TextButton(onClick = { dob = null }) { Text("Clear") }
                }

                Text("Sex (optional)", style = MaterialTheme.typography.titleSmall)
                val sexOptions = listOf<Sex?>(null) + Sex.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    sexOptions.forEachIndexed { i, o ->
                        SegmentedButton(selected = sex == o, onClick = { sex = o }, shape = SegmentedButtonDefaults.itemShape(i, sexOptions.size)) {
                            Text(o?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not set")
                        }
                    }
                }
                Text("Used only to select sex-specific reference information (e.g. waist circumference).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Height", style = MaterialTheme.typography.titleSmall)
                if (heightUnit == MeasurementUnit.FEET_INCHES) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(height.main, { height = height.copy(main = it) }, Modifier.weight(1f), suffix = { Text("ft") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(height.sub, { height = height.copy(sub = it) }, Modifier.weight(1f), suffix = { Text("in") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                } else {
                    OutlinedTextField(height.main, { height = height.copy(main = it) }, Modifier.fillMaxWidth(), suffix = { Text(heightUnit.symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }

                Text("Initial weight", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    weight.main, { weight = weight.copy(main = it) }, Modifier.fillMaxWidth(), suffix = { Text(weightUnit.symbol) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Starting point for \"weight change\". Record ongoing weights as measurements.") },
                )

                Text("Time zone", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { showZone = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(zoneId ?: "Automatic (device: ${ZoneId.systemDefault().id})")
                }
                Text("Day boundaries for charts, statistics and the daily checklist use this time zone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(goals, { goals = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Personal health goals (optional)") }, minLines = 2)
                OutlinedTextField(notes, { notes = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Medical notes (optional)") }, minLines = 3,
                    supportingText = { Text("e.g. conditions, medications, allergies – useful when sharing your history with a clinician.") })

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = ::save, Modifier.fillMaxWidth().height(52.dp)) { Text("Save profile") }
                Text(
                    "To track height changes over time, enable the Height category; your profile height is used for BMI.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDob) {
        val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dob?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = (dob ?: LocalDate.now().minusYears(40)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today },
        )
        DatePickerDialog(
            onDismissRequest = { showDob = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dob = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDob = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDob = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showZone) {
        TimeZoneDialog(onDismiss = { showZone = false }, onSelect = { zoneId = it; showZone = false })
    }
}

@Composable
private fun TimeZoneDialog(onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var q by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted() }
    val filtered = zones.filter { it.contains(q.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        Text("Automatic (device)", Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    items(filtered) { z -> Text(z.replace('_', ' '), Modifier.fillMaxWidth().clickable { onSelect(z) }.padding(vertical = 10.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
