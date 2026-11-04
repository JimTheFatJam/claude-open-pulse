package app.openpulse.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.openpulse.core.model.Aggregation
import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.stats.ChannelStatistics
import app.openpulse.core.stats.StatisticsCalculator
import app.openpulse.core.stats.Trend
import app.openpulse.core.time.TimeRange
import app.openpulse.core.units.ValueFormatter
import app.openpulse.ui.format.Formats
import java.time.Instant
import java.time.ZoneId

/** Statistics for every channel of [type] in the current [range]. */
fun computeStats(type: MeasurementType, measurements: List<Measurement>, range: TimeRange, zone: ZoneId, now: Instant = Instant.now()): List<ChannelStatistics> {
    val ofType = measurements.filter { it.type == type }
    val window = range.window(now, zone, ofType.minOfOrNull { it.timestamp })
    val prev = range.previousWindow(window, zone)
    return type.channels
        .filter { spec -> spec.required || ofType.any { it.valueOf(spec.channel) != null } }
        .map { spec -> StatisticsCalculator.compute(type, spec.channel, ofType, window, prev, zone) }
}

fun trendIcon(t: Trend): ImageVector = when (t) {
    Trend.INCREASING -> Icons.AutoMirrored.Outlined.TrendingUp
    Trend.DECREASING -> Icons.AutoMirrored.Outlined.TrendingDown
    Trend.STABLE -> Icons.AutoMirrored.Outlined.TrendingFlat
    Trend.VARIABLE -> Icons.Outlined.ShowChart
    Trend.INSUFFICIENT_DATA -> Icons.Outlined.HelpOutline
}

@Composable
fun TrendLine(stats: ChannelStatistics, formatter: ValueFormatter, modifier: Modifier = Modifier) {
    val t = stats.trend
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(trendIcon(t.trend), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        val detail = when {
            t.trend == Trend.INSUFFICIENT_DATA -> t.reason ?: ""
            t.slopePerDay != null && (t.trend == Trend.INCREASING || t.trend == Trend.DECREASING) ->
                "≈ ${formatter.delta(stats.type, stats.channel, t.slopePerDay!! * 7)} per week · ${t.pointsUsed} values"
            else -> "${t.pointsUsed} values"
        }
        Text(t.trend.description, style = MaterialTheme.typography.bodyMedium)
        if (detail.isNotEmpty()) {
            Text("  ·  $detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full statistics block for one channel. */
@Composable
fun ChannelStatsBlock(stats: ChannelStatistics, formatter: ValueFormatter, zone: ZoneId, range: TimeRange, showTitle: Boolean) {
    val type = stats.type
    val ch = stats.channel
    val daily = type.aggregation == Aggregation.DAILY_SUM && ch == Channel.PRIMARY
    fun v(x: Double?) = x?.let { formatter.withUnit(type, ch, it) } ?: "—"
    Column(Modifier.fillMaxWidth()) {
        if (showTitle) {
            Text(type.spec(ch)?.label ?: "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
        }
        val countLabel = if (daily) "${stats.count} days · ${stats.entryCount} entries" else "${stats.count} values"
        StatGrid(
            listOf(
                Triple(if (daily) "Latest day" else "Latest", v(stats.latest?.value), stats.latest?.let { Formats.date(it.time, zone) }),
                Triple("Previous", v(stats.previous?.value), stats.previous?.let { Formats.date(it.time, zone) }),
                Triple(if (daily) "Avg daily total" else "Average", v(stats.average), "n = ${stats.count}"),
                Triple("Minimum", v(stats.min?.value), stats.min?.let { Formats.date(it.time, zone) }),
                Triple("Maximum", v(stats.max?.value), stats.max?.let { Formats.date(it.time, zone) }),
                Triple("Recorded", countLabel, range.label),
            ),
        )
        stats.comparison?.let { c ->
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))
            Text("Compared with the previous ${range.label.lowercase()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.difference != null) {
                val pct = c.percentChange?.let { " (${Formats.percent(it)})" } ?: ""
                Text(
                    "Average ${formatter.delta(type, ch, c.difference!!)}$pct",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Based on ${stats.count} vs ${c.previousCount} recorded values" + if (c.percentChange == null && type.percentChangeMeaningful.not()) " · % change not meaningful for this measure" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Not enough data (${stats.count} now, ${c.previousCount} before). ${c.reason ?: ""}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TrendLine(stats, formatter)
    }
}

/** Compact summary row: average · min–max · count · trend for a period. */
@Composable
fun CompactStats(stats: List<ChannelStatistics>, formatter: ValueFormatter, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        stats.forEach { s ->
            val type = s.type
            fun v(x: Double?) = x?.let { formatter.withUnit(type, s.channel, it) } ?: "—"
            if (stats.size > 1) Text(type.spec(s.channel)?.label ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            StatGrid(
                listOf(
                    Triple("Average", v(s.average), "n = ${s.count}"),
                    Triple("Min", v(s.min?.value), null),
                    Triple("Max", v(s.max?.value), null),
                    Triple("Change", s.comparison?.difference?.let { formatter.delta(type, s.channel, it) } ?: "—",
                        if (s.comparison?.difference == null) "too few values" else "vs prev. period"),
                ),
                columns = 4,
            )
        }
        stats.firstOrNull()?.let { TrendLine(it, formatter, Modifier.padding(top = 4.dp)) }
    }
}
