package app.openpulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openpulse.core.model.Aggregation
import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.stats.StatisticsCalculator
import app.openpulse.core.time.TimeRange
import app.openpulse.core.time.TimeWindow
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.core.units.ValueFormatter
import app.openpulse.core.health.ReferenceRanges
import app.openpulse.data.prefs.UserSettings
import app.openpulse.ui.format.Formats
import app.openpulse.ui.theme.NumericStyle
import app.openpulse.ui.theme.channelColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Builds display-unit chart series for one measurement type. */
@Composable
fun rememberSeries(type: MeasurementType, measurements: List<Measurement>, formatter: ValueFormatter, zone: ZoneId): List<ChartSeries> {
    val specs = type.chartChannels
    val colors = specs.map { type.channelColor(it.channel) }
    val raw = remember(type, measurements, formatter, zone) {
        val ofType = measurements.filter { it.type == type }
        specs.map { spec ->
            val unit = formatter.unitFor(type, spec.channel)
            spec to StatisticsCalculator.series(ofType, spec.channel, zone).map { it.copy(value = unit.fromCanonical(it.value)) }
        }
    }
    return raw.mapIndexed { i, (spec, pts) -> ChartSeries(spec.channel.name, spec.label, colors[i], pts) }
}

fun dataBounds(measurements: List<Measurement>, window: TimeWindow, zone: ZoneId): TimeWindow {
    val earliest = measurements.minOfOrNull { it.timestamp } ?: return window
    val start = Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return TimeWindow(minOf(start, window.start), window.end)
}

fun yAxisFormatter(type: MeasurementType, formatter: ValueFormatter): (Double) -> String {
    val unit = formatter.unitFor(type)
    val decimals = when (unit) {
        MeasurementUnit.LITER -> 1
        MeasurementUnit.MMOL_L, MeasurementUnit.HOURS -> 1
        else -> 0
    }
    return { v ->
        if (decimals == 0 || v % 1.0 == 0.0) formatter.decimal(v, 0) else formatter.decimal(v, decimals)
    }
}

/**
 * Range selector + interactive chart + legend + details of a tapped dot.
 */
@Composable
fun MeasurementChartPanel(
    type: MeasurementType,
    measurements: List<Measurement>,
    settings: UserSettings,
    zone: ZoneId,
    range: TimeRange,
    onRangeChange: (TimeRange) -> Unit,
    onOpenMeasurement: (Long) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 260.dp,
    ageYears: Int? = null,
) {
    val formatter = remember(settings.units) { ValueFormatter(settings.units) }
    val ofType = remember(measurements, type) { measurements.filter { it.type == type } }
    val now = remember(range, ofType) { Instant.now() }
    val window = remember(range, now, zone, ofType) { range.window(now, zone, ofType.minOfOrNull { it.timestamp }) }
    val bounds = remember(window, ofType, zone) { dataBounds(ofType, window, zone) }
    val viewport = rememberChartViewport(window, bounds)
    val series = rememberSeries(type, ofType, formatter, zone)
    var selection by remember(type, range) { mutableStateOf<ChartSelection?>(null) }
    val gap = if (settings.connectLines) settings.connectMaxGapDays * 86_400_000L else null
    val band = if (settings.showReferenceBands) {
        ReferenceRanges.forType(type, ageYears, null)?.let { r ->
            val unit = formatter.unitFor(type)
            if (r.bandLow != null && r.bandHigh != null) unit.fromCanonical(r.bandLow!!)..unit.fromCanonical(r.bandHigh!!) else null
        }
    } else null

    Column(modifier) {
        RangeSelector(range, onRangeChange)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatter.axisLabel(type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(viewport.isModified) {
                TextButton(onClick = { viewport.reset() }) {
                    Icon(Icons.Outlined.ZoomOutMap, null, Modifier.padding(end = 4.dp))
                    Text("Reset zoom")
                }
            }
        }
        HealthChart(
            series = series,
            viewport = viewport,
            zone = zone,
            yLabel = yAxisFormatter(type, formatter),
            height = chartHeight,
            connectMaxGapMillis = gap,
            referenceBand = band,
            selection = selection,
            onSelect = { selection = it },
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            series.forEach { LegendDot(it.color, it.label + if (type.aggregation == Aggregation.DAILY_SUM) " (daily total)" else "") }
            if (gap != null) LegendDot(MaterialTheme.colorScheme.onSurfaceVariant, "≤ ${settings.connectMaxGapDays} d apart", dashed = true)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Dots are recorded values. Empty days stay empty – nothing is filled in.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(selection != null) {
            selection?.let { sel ->
                SelectionCard(type, sel, ofType, formatter, zone, onClose = { selection = null }, onOpen = onOpenMeasurement)
            }
        }
    }
}

@Composable
private fun SelectionCard(
    type: MeasurementType,
    sel: ChartSelection,
    ofType: List<Measurement>,
    formatter: ValueFormatter,
    zone: ZoneId,
    onClose: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val m = sel.point.measurementId?.let { id -> ofType.firstOrNull { it.id == id } }
    val day: LocalDate = Instant.ofEpochMilli(sel.point.time).atZone(zone).toLocalDate()
    val sameDay = ofType.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }
    Surface(
        Modifier.fillMaxWidth().padding(top = 10.dp).let { if (m != null) it.clickable { onOpen(m.id) } else it },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (m != null) {
                    Text(formatter.measurement(m), style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                    Text(Formats.dateTime(m.timestamp, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (m.zoneId != zone.id) {
                        Text(
                            "Recorded at ${Formats.time(m.timestamp, ZoneId.of(m.zoneId))} local time (${m.zoneId})",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    TagRow(m.tags, Modifier.padding(top = 4.dp))
                } else {
                    // Daily total point
                    val canonical = formatter.unitFor(type, Channel.PRIMARY).toCanonical(sel.point.value)
                    Text(formatter.withUnit(type, Channel.PRIMARY, canonical), style = MaterialTheme.typography.titleMedium.merge(NumericStyle))
                    Text(
                        "Daily total of ${sel.point.entryCount} entr${if (sel.point.entryCount == 1) "y" else "ies"} · ${Formats.date(day)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sameDay > 1 && m != null) {
                    Text("$sameDay readings recorded on this day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (m != null) Text("Tap to edit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close details") }
        }
    }
}
