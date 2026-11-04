@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.core.health.BodyMetrics
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.units.ValueFormatter
import app.openpulse.data.prefs.UserSettings
import app.openpulse.data.prefs.zone
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.CompactStats
import app.openpulse.ui.components.DemoBanner
import app.openpulse.ui.components.EmptyState
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.components.MeasurementChartPanel
import app.openpulse.ui.components.SectionCard
import app.openpulse.ui.components.SectionTitle
import app.openpulse.ui.components.TypeBadge
import app.openpulse.ui.components.TypeChips
import app.openpulse.ui.components.computeStats
import app.openpulse.ui.format.Formats
import app.openpulse.ui.theme.NumericStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DashboardScreen(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    onRecord: (MeasurementType?) -> Unit,
    onEdit: (Long) -> Unit,
    onOpenType: (MeasurementType) -> Unit,
) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val zone = profile.zone()
    val measurements = all
    val enabled = settings.orderedEnabledTypes
    val selected = settings.dashboardType?.takeIf { it in settings.enabledTypes } ?: enabled.firstOrNull()
    val now = System.currentTimeMillis()
    val latest = measurements?.firstOrNull()
    val age = profile.dateOfBirth?.let { BodyMetrics.ageYears(it, LocalDate.now(zone)) }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Open Pulse", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (latest != null) "Last measurement ${Formats.relative(latest.timestamp, now, zone)} · ${latest.type.shortTitle}"
                            else "No measurements recorded yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onRecord(selected) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Record") },
            )
        },
    ) { padding ->
        if (measurements == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val chartPart: @Composable () -> Unit = {
            if (selected == null) {
                EmptyState("No categories enabled", "Enable the measurements you track in Settings → Categories & dashboard.")
            } else {
                MainChartCard(vm, settings, selected, measurements, zone, age, onEdit, onOpenType)
            }
        }
        val sidePart: @Composable () -> Unit = {
            if (settings.checklistEnabled && settings.checklistTypes.any { it in settings.enabledTypes }) {
                ChecklistCard(settings, measurements, zone, onRecord)
                Spacer(Modifier.height(16.dp))
            }
            VitalsSection(vm, settings, measurements, zone, selected, onOpenType)
            Spacer(Modifier.height(12.dp))
            InfoNote("Open Pulse is a personal tracking tool, not a diagnostic device. Discuss your readings with a healthcare professional.")
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (settings.demoMode) { DemoBanner(Modifier.padding(bottom = 12.dp)) }
            if (enabled.size > 1) {
                TypeChips(enabled, selected, onSelect = { t -> vm.updateSettings { it.copy(dashboardType = t) } })
                Spacer(Modifier.height(12.dp))
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.4f)) { chartPart() }
                    Column(Modifier.weight(1f)) { sidePart() }
                }
            } else {
                chartPart()
                Spacer(Modifier.height(16.dp))
                sidePart()
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun MainChartCard(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    type: MeasurementType,
    measurements: List<Measurement>,
    zone: ZoneId,
    age: Int?,
    onEdit: (Long) -> Unit,
    onOpenType: (MeasurementType) -> Unit,
) {
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    val latest = measurements.firstOrNull { it.type == type }
    val range = settings.dashboardRange
    val stats = remember(type, measurements, range, zone) { computeStats(type, measurements, range, zone).filter { it.type.chartChannels.any { c -> c.channel == it.channel } } }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(type, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    latest?.let { "Latest · ${Formats.relative(it.timestamp, System.currentTimeMillis(), zone)}" } ?: "Not recorded yet",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onOpenType(type) }) { Icon(Icons.Outlined.ChevronRight, "Open ${type.title}") }
        }
        if (latest != null) {
            Text(
                formatter.measurement(latest),
                style = MaterialTheme.typography.headlineMedium.merge(NumericStyle),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        MeasurementChartPanel(
            type = type,
            measurements = measurements,
            settings = settings,
            zone = zone,
            range = range,
            onRangeChange = { r -> vm.updateSettings { it.copy(dashboardRange = r) } },
            onOpenMeasurement = onEdit,
            ageYears = age,
        )
        Spacer(Modifier.height(12.dp))
        CompactStats(stats, formatter)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VitalsSection(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    measurements: List<Measurement>,
    zone: ZoneId,
    selected: MeasurementType?,
    onOpenType: (MeasurementType) -> Unit,
) {
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    val cards = settings.visibleDashboardCards
    if (cards.isEmpty()) return
    SectionTitle("Vitals")
    val now = System.currentTimeMillis()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        cards.forEach { type ->
            val latest = measurements.firstOrNull { it.type == type }
            Card(
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).clickable {
                    if (type == selected) onOpenType(type) else vm.updateSettings { it.copy(dashboardType = type) }
                },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (type == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(type, 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(type.shortTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        latest?.let { formatter.measurement(it, includePulse = false) } ?: "—",
                        style = MaterialTheme.typography.titleLarge.merge(NumericStyle),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        latest?.let { Formats.relative(it.timestamp, now, zone) } ?: "No data yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (cards.size % 2 == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ChecklistCard(settings: UserSettings, measurements: List<Measurement>, zone: ZoneId, onRecord: (MeasurementType?) -> Unit) {
    val today = LocalDate.now(zone)
    val types = settings.orderedEnabledTypes.filter { it in settings.checklistTypes }
    val doneToday = measurements.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }.map { it.type }.toSet()
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today's checklist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${types.count { it in doneToday }}/${types.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        types.forEach { t ->
            val done = t in doneToday
            Row(
                Modifier.fillMaxWidth().clickable { onRecord(t) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(t.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(if (done) "Done" else "Record", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
