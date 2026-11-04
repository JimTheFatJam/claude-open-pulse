@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.measurements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.time.TimeRange
import app.openpulse.core.units.ValueFormatter
import app.openpulse.data.prefs.UserSettings
import app.openpulse.data.prefs.zone
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.ChannelStatsBlock
import app.openpulse.ui.components.DemoBanner
import app.openpulse.ui.components.EmptyState
import app.openpulse.ui.components.EntryRow
import app.openpulse.ui.components.HealthChart
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.components.MeasurementChartPanel
import app.openpulse.ui.components.RangeSelector
import app.openpulse.ui.components.SectionCard
import app.openpulse.ui.components.TypeBadge
import app.openpulse.ui.components.computeStats
import app.openpulse.ui.components.dataBounds
import app.openpulse.ui.components.rememberChartViewport
import app.openpulse.ui.components.rememberSeries
import app.openpulse.ui.components.yAxisFormatter
import app.openpulse.ui.format.Formats
import app.openpulse.ui.theme.NumericStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Overview of all tracked categories as small multiples sharing the same time range. */
@Composable
fun MeasurementsScreen(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    onOpenType: (MeasurementType) -> Unit,
    onRecord: (MeasurementType?) -> Unit,
    onManageCategories: () -> Unit,
) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val zone = profile.zone()
    var range by rememberSaveable { mutableStateOf(TimeRange.MONTH) }
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    val measurements = all.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurements") },
                actions = { IconButton(onClick = onManageCategories) { Icon(Icons.Outlined.Tune, "Manage categories") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onRecord(null) }) { Icon(Icons.Outlined.Add, "Record measurement") }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.demoMode) item(span = { GridItemSpan(maxLineSpan) }) { DemoBanner() }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    RangeSelector(range, { range = it })
                    Spacer(Modifier.height(4.dp))
                    InfoNote("Each chart uses its own unit and scale; all share the same time axis so you can compare when things were recorded.")
                }
            }
            if (settings.orderedEnabledTypes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nothing to show", "Enable categories you want to track.") }
            }
            items(settings.orderedEnabledTypes, key = { it.key }) { type ->
                OverviewCard(type, measurements, range, zone, formatter, onClick = { onOpenType(type) })
            }
        }
    }
}

@Composable
private fun OverviewCard(type: MeasurementType, measurements: List<Measurement>, range: TimeRange, zone: ZoneId, formatter: ValueFormatter, onClick: () -> Unit) {
    val ofType = remember(measurements, type) { measurements.filter { it.type == type } }
    val latest = ofType.firstOrNull()
    val window = remember(range, ofType, zone) { range.window(Instant.now(), zone, ofType.minOfOrNull { it.timestamp }) }
    val count = ofType.count { it.timestamp in window.start until window.end }
    val viewport = rememberChartViewport(window, dataBounds(ofType, window, zone))
    val series = rememberSeries(type, ofType, formatter, zone)
    SectionCard(Modifier.clickable(onClick = onClick), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(type, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    latest?.let { Formats.relative(it.timestamp, System.currentTimeMillis(), zone) } ?: "No data yet",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(latest?.let { formatter.measurement(it, includePulse = false) } ?: "—", style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                Text("$count in ${range.shortLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        HealthChart(
            series = series, viewport = viewport, zone = zone, yLabel = yAxisFormatter(type, formatter),
            height = 130.dp, connectMaxGapMillis = null, interactive = false, emptyText = "No values in this period",
        )
    }
}

@Composable
fun MeasurementDetailScreen(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    type: MeasurementType,
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val zone = profile.zone()
    val measurements = all.orEmpty()
    val ofType = remember(measurements, type) { measurements.filter { it.type == type } }
    var range by rememberSaveable { mutableStateOf(TimeRange.MONTH) }
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    val age = profile.dateOfBirth?.let { BodyMetrics.ageYears(it, LocalDate.now(zone)) }
    val stats = remember(ofType, range, zone) { computeStats(type, ofType, range, zone) }
    val reference = ReferenceRanges.forType(type, age, profile.sex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onRecord, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Record") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (settings.demoMode) DemoBanner(Modifier.padding(bottom = 12.dp))
                    Text(type.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        MeasurementChartPanel(type, ofType, settings, zone, range, { range = it }, onEdit, chartHeight = 300.dp, ageYears = age)
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        Text("Statistics · ${range.label}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        stats.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            ChannelStatsBlock(s, formatter, zone, range, showTitle = stats.size > 1)
                        }
                    }
                    if (reference != null) {
                        Spacer(Modifier.height(12.dp))
                        ReferenceCard(reference)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("All entries (${ofType.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (ofType.isEmpty()) {
                item { EmptyState("No entries yet", "Tap Record to add your first ${type.title.lowercase()} measurement.") }
            }
            items(ofType, key = { it.id }) { m ->
                Column {
                    Text(
                        Formats.date(m.timestamp, zone),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 64.dp, top = 6.dp),
                    )
                    EntryRow(m, formatter, zone, onClick = { onEdit(m.id) }, showType = false)
                }
            }
        }
    }
}

@Composable
fun ReferenceCard(reference: app.openpulse.core.health.ReferenceInfo) {
    SectionCard {
        Text("General reference information", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(reference.summary, style = MaterialTheme.typography.bodyMedium)
        reference.details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("Source: ${reference.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(reference.applicability, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        InfoNote(ReferenceRanges.DISCLAIMER)
    }
}
