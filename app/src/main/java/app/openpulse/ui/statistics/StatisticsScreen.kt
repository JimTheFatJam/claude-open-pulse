@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import app.openpulse.core.health.BodyMetrics
import app.openpulse.core.health.ReferenceRanges
import app.openpulse.core.model.Channel
import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.time.TimeRange
import app.openpulse.core.units.ValueFormatter
import app.openpulse.data.prefs.UserSettings
import app.openpulse.data.prefs.zone
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.ChannelStatsBlock
import app.openpulse.ui.components.DemoBanner
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.components.RangeSelector
import app.openpulse.ui.components.SectionCard
import app.openpulse.ui.components.StatGrid
import app.openpulse.ui.components.TypeBadge
import app.openpulse.ui.components.computeStats
import app.openpulse.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StatisticsScreen(vm: OpenPulseViewModel, settings: UserSettings, onOpenProfile: () -> Unit) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val zone = profile.zone()
    val measurements = all.orEmpty()
    var range by rememberSaveable { mutableStateOf(TimeRange.MONTH) }
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }

    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            if (settings.demoMode) item { DemoBanner(Modifier.padding(bottom = 12.dp)) }
            item {
                RangeSelector(range, { range = it })
                Spacer(Modifier.height(6.dp))
                InfoNote(
                    "All figures use only values you actually recorded in the selected period. " +
                        "Days without entries are not counted as zero, and nothing is interpolated.",
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                BodyMetricsCard(profile, measurements, settings, formatter, zone, range, onOpenProfile)
                Spacer(Modifier.height(12.dp))
            }
            if (MeasurementType.BLOOD_PRESSURE in settings.enabledTypes) {
                item {
                    BloodPressureContextCard(profile, measurements, range, zone, formatter)
                }
            }
            items(settings.orderedEnabledTypes, key = { it.key }) { type ->
                val stats = remember(measurements, type, range, zone) { computeStats(type, measurements, range, zone) }
                SectionCard(Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(type, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(type.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    stats.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        ChannelStatsBlock(s, formatter, zone, range, showTitle = stats.size > 1)
                    }
                }
            }
            item {
                InfoNote(
                    "Trends need at least 4 values spread over 3 or more days. A single measurement is never treated as a trend. " +
                        "These statistics are informational and are not a medical assessment.",
                )
            }
        }
    }
}

@Composable
private fun BodyMetricsCard(
    profile: HealthProfile,
    measurements: List<Measurement>,
    settings: UserSettings,
    formatter: ValueFormatter,
    zone: ZoneId,
    range: TimeRange,
    onOpenProfile: () -> Unit,
) {
    val today = LocalDate.now(zone)
    val age = profile.dateOfBirth?.let { BodyMetrics.ageYears(it, today) }
    val weights = measurements.filter { it.type == MeasurementType.WEIGHT }.sortedBy { it.timestamp }
    val latestWeight = weights.lastOrNull()
    val heightCm = profile.heightCm ?: measurements.firstOrNull { it.type == MeasurementType.HEIGHT }?.value
    val bmi = BodyMetrics.bmi(latestWeight?.value, heightCm)
    val category = bmi?.let { BodyMetrics.bmiCategory(it, age) }
    val window = range.window(Instant.now(), zone, weights.firstOrNull()?.timestamp)
    val inRange = weights.filter { it.timestamp in window.start until window.end }

    SectionCard {
        Text("Body metrics", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val items = mutableListOf<Triple<String, String, String?>>()
        items += Triple("Age", age?.let { "$it years" } ?: "—", if (age == null) "Set date of birth" else null)
        items += Triple("Height", heightCm?.let { formatter.withUnit(MeasurementType.HEIGHT, Channel.PRIMARY, it) } ?: "—", if (profile.heightCm == null) "From profile" else "Profile")
        items += Triple(
            "BMI", bmi?.let { formatter.decimal(it, 1) } ?: "—",
            when {
                bmi == null -> "Needs height + weight"
                else -> "Weight of ${Formats.date(latestWeight!!.timestamp, zone)}"
            },
        )
        val initial = profile.initialWeightKg
        if (initial != null && latestWeight != null) {
            items += Triple("Since initial weight", formatter.delta(MeasurementType.WEIGHT, Channel.PRIMARY, latestWeight.value - initial), "Profile: ${formatter.withUnit(MeasurementType.WEIGHT, Channel.PRIMARY, initial)}")
        }
        if (inRange.size >= 2) {
            items += Triple("Change in ${range.shortLabel}", formatter.delta(MeasurementType.WEIGHT, Channel.PRIMARY, inRange.last().value - inRange.first().value), "First vs last of ${inRange.size} values")
        } else if (MeasurementType.WEIGHT in settings.enabledTypes) {
            items += Triple("Change in ${range.shortLabel}", "—", "Needs 2+ weights")
        }
        StatGrid(items)
        if (category != null) {
            Spacer(Modifier.height(6.dp))
            Text("WHO adult BMI band: ${category.label}", style = MaterialTheme.typography.bodySmall)
        } else if (bmi != null && (age == null || age < 20)) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (age == null) "Add your date of birth to see the WHO adult reference band." else "Adult BMI bands do not apply under 20; BMI-for-age percentiles are used instead.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        InfoNote("BMI does not distinguish muscle from fat and is a population screening measure, not a diagnosis.")
        if (profile.heightCm == null || profile.dateOfBirth == null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenProfile) { Text("Complete health profile") }
        }
    }
}

@Composable
private fun BloodPressureContextCard(profile: HealthProfile, measurements: List<Measurement>, range: TimeRange, zone: ZoneId, formatter: ValueFormatter) {
    val stats = remember(measurements, range, zone) { computeStats(MeasurementType.BLOOD_PRESSURE, measurements, range, zone) }
    val sys = stats.firstOrNull { it.channel == Channel.PRIMARY } ?: return
    val dia = stats.firstOrNull { it.channel == Channel.SECONDARY } ?: return
    if (sys.count == 0) return
    val age = profile.dateOfBirth?.let { BodyMetrics.ageYears(it, LocalDate.now(zone)) }
    val category = if (sys.average != null && dia.average != null) ReferenceRanges.bloodPressureCategory(sys.average!!, dia.average!!, sys.count, age) else null
    SectionCard(Modifier.padding(bottom = 12.dp)) {
        Text("Blood pressure context", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Column {
            Text(
                "Average of ${sys.count} readings: systolic ${formatter.withUnit(MeasurementType.BLOOD_PRESSURE, Channel.PRIMARY, sys.average!!)}, " +
                    "diastolic ${formatter.withUnit(MeasurementType.BLOOD_PRESSURE, Channel.SECONDARY, dia.average!!)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    category != null -> "These averages fall in the AHA/ACC 2017 adult \"$category\" (reference only)."
                    age == null -> "Add your date of birth to the profile to see general adult reference categories."
                    age < 18 -> "Adult reference categories do not apply under 18."
                    else -> "At least 2 readings are needed before a reference category is shown."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            InfoNote("Home readings vary with posture, time of day, activity and cuff size. Only a healthcare professional can diagnose high or low blood pressure.")
        }
    }
}
