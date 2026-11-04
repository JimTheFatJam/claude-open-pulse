package app.openpulse.core.stats

import app.openpulse.core.model.Aggregation
import app.openpulse.core.model.Channel
import app.openpulse.core.model.DataPoint
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.time.TimeWindow
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

enum class Trend(val description: String) {
    INCREASING("Increasing trend"),
    DECREASING("Decreasing trend"),
    STABLE("Relatively stable"),
    VARIABLE("No consistent direction"),
    INSUFFICIENT_DATA("Insufficient data"),
}

data class TrendResult(
    val trend: Trend,
    /** Fitted change per day (canonical unit), only when a trend could be estimated. */
    val slopePerDay: Double? = null,
    val pointsUsed: Int,
    val reason: String? = null,
)

data class PeriodComparison(
    val previousCount: Int,
    val previousAverage: Double?,
    /** current average − previous average; null when either period has too little data. */
    val difference: Double?,
    /** Relative change in %, only where mathematically meaningful. */
    val percentChange: Double?,
    val reason: String? = null,
)

data class ChannelStatistics(
    val type: MeasurementType,
    val channel: Channel,
    /** Number of valid recorded values used for average/min/max in the window. */
    val count: Int,
    /** For daily-sum types: number of raw entries that make up the daily totals. */
    val entryCount: Int,
    val latest: DataPoint?,
    val previous: DataPoint?,
    val average: Double?,
    val min: DataPoint?,
    val max: DataPoint?,
    val comparison: PeriodComparison?,
    val trend: TrendResult,
)

/**
 * Computes statistics strictly from recorded values. Missing days are never filled,
 * interpolated or treated as zero; every figure states how many values it is based on.
 */
object StatisticsCalculator {
    const val MIN_POINTS_FOR_COMPARISON = 2
    const val MIN_POINTS_FOR_TREND = 4
    const val MIN_TREND_SPAN_DAYS = 3.0
    /** Minimum |correlation| between time and value for a direction to be called a trend. */
    const val MIN_TREND_CORRELATION = 0.3

    /**
     * Value series for a channel. For [Aggregation.DAILY_SUM] types, entries are summed per local
     * calendar day; days without entries produce no point at all.
     */
    fun series(measurements: List<Measurement>, channel: Channel, zone: ZoneId): List<DataPoint> {
        val type = measurements.firstOrNull()?.type ?: return emptyList()
        val raw = measurements.mapNotNull { m -> m.valueOf(channel)?.let { DataPoint(m.timestamp, it, m.id) } }
            .sortedWith(compareBy({ it.time }, { it.measurementId }))
        return if (type.aggregation == Aggregation.DAILY_SUM && channel == Channel.PRIMARY) dailyTotals(raw, zone) else raw
    }

    fun dailyTotals(points: List<DataPoint>, zone: ZoneId): List<DataPoint> =
        points.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (day, pts) ->
                DataPoint(
                    // Position daily totals at local noon so they sit in the middle of their day.
                    time = day.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(),
                    value = pts.sumOf { it.value },
                    measurementId = null,
                    entryCount = pts.size,
                )
            }

    fun compute(
        type: MeasurementType,
        channel: Channel,
        allMeasurements: List<Measurement>,
        window: TimeWindow,
        previousWindow: TimeWindow?,
        zone: ZoneId,
    ): ChannelStatistics {
        val ofType = allMeasurements.filter { it.type == type }
        val all = series(ofType, channel, zone)
        val inWindow = all.filter { it.time in window }
        val upToEnd = all.filter { it.time < window.end }
        val latest = upToEnd.lastOrNull()
        val previous = if (upToEnd.size >= 2) upToEnd[upToEnd.size - 2] else null
        val average = inWindow.takeIf { it.isNotEmpty() }?.map { it.value }?.average()

        val comparison = previousWindow?.let { pw ->
            val prev = all.filter { it.time in pw }
            val prevAvg = prev.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
            val enough = inWindow.size >= MIN_POINTS_FOR_COMPARISON && prev.size >= MIN_POINTS_FOR_COMPARISON
            val diff = if (enough && average != null && prevAvg != null) average - prevAvg else null
            val pct = if (diff != null && type.percentChangeMeaningful && prevAvg != null && prevAvg != 0.0) {
                diff / abs(prevAvg) * 100.0
            } else null
            PeriodComparison(
                previousCount = prev.size,
                previousAverage = prevAvg,
                difference = diff,
                percentChange = pct,
                reason = if (!enough) "Needs at least $MIN_POINTS_FOR_COMPARISON values in both periods" else null,
            )
        }

        return ChannelStatistics(
            type = type,
            channel = channel,
            count = inWindow.size,
            entryCount = inWindow.sumOf { it.entryCount },
            latest = latest,
            previous = previous,
            average = average,
            min = inWindow.minByOrNull { it.value },
            max = inWindow.maxByOrNull { it.value },
            comparison = comparison,
            trend = trend(inWindow, type.stableThreshold),
        )
    }

    /**
     * Least-squares trend over actual recorded points only.
     * A direction is reported only when there are enough points over enough days, the fitted
     * change exceeds the type's stability threshold, and the fit is reasonably consistent.
     */
    fun trend(points: List<DataPoint>, stableThreshold: Double): TrendResult {
        val n = points.size
        if (n < MIN_POINTS_FOR_TREND) {
            return TrendResult(Trend.INSUFFICIENT_DATA, null, n, "Needs at least $MIN_POINTS_FOR_TREND values")
        }
        val dayMs = 86_400_000.0
        val t0 = points.first().time
        val xs = points.map { (it.time - t0) / dayMs }
        val ys = points.map { it.value }
        val spanDays = xs.max() - xs.min()
        if (spanDays < MIN_TREND_SPAN_DAYS) {
            return TrendResult(Trend.INSUFFICIENT_DATA, null, n, "Values must span at least ${MIN_TREND_SPAN_DAYS.toInt()} days")
        }
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        if (sxx == 0.0) return TrendResult(Trend.INSUFFICIENT_DATA, null, n, "All values at the same time")
        val slope = sxy / sxx
        val projectedChange = slope * spanDays
        if (abs(projectedChange) < stableThreshold) return TrendResult(Trend.STABLE, slope, n)
        val r = if (syy == 0.0) 0.0 else sxy / sqrt(sxx * syy)
        if (abs(r) < MIN_TREND_CORRELATION) return TrendResult(Trend.VARIABLE, slope, n)
        return TrendResult(if (slope > 0) Trend.INCREASING else Trend.DECREASING, slope, n)
    }
}
