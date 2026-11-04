package app.openpulse.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class TickGranularity { HOUR, DAY, WEEK, MONTH, YEAR }

data class AxisTick(val time: Long, val granularity: TickGranularity)

/**
 * Generates calendar-aligned axis ticks and day boundaries for a continuous time axis.
 * The axis is continuous: every day in the window occupies space whether or not it has data,
 * so days without measurements remain visible as empty space instead of being collapsed.
 */
object TimelineAxis {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    fun ticks(start: Long, end: Long, zone: ZoneId, maxTicks: Int = 6): List<AxisTick> {
        if (end <= start) return emptyList()
        val span = end - start
        val (granularity, stepCandidates) = when {
            span <= 2 * DAY_MS -> TickGranularity.HOUR to listOf(1L, 2, 3, 6, 12)
            span <= 20 * DAY_MS -> TickGranularity.DAY to listOf(1L, 2, 3, 4, 7)
            span <= 120 * DAY_MS -> TickGranularity.WEEK to listOf(1L, 2, 3, 4)
            span <= 4 * 365 * DAY_MS -> TickGranularity.MONTH to listOf(1L, 2, 3, 4, 6, 12)
            else -> TickGranularity.YEAR to listOf(1L, 2, 5, 10, 20, 50)
        }
        for (step in stepCandidates) {
            val ticks = generate(start, end, zone, granularity, step)
            if (ticks.size <= maxTicks || step == stepCandidates.last()) return ticks
        }
        return emptyList()
    }

    private fun generate(start: Long, end: Long, zone: ZoneId, g: TickGranularity, step: Long): List<AxisTick> {
        val startZ = Instant.ofEpochMilli(start).atZone(zone)
        var t: ZonedDateTime = when (g) {
            TickGranularity.HOUR -> {
                val base = startZ.truncatedTo(ChronoUnit.HOURS)
                val alignedHour = ((base.hour + step - 1) / step) * step
                base.toLocalDate().atStartOfDay(zone).plusHours(alignedHour)
            }
            TickGranularity.DAY -> startZ.toLocalDate().atStartOfDay(zone)
            TickGranularity.WEEK -> startZ.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone)
            TickGranularity.MONTH -> {
                val d = startZ.toLocalDate().withDayOfMonth(1)
                val monthIndex = d.monthValue - 1
                val aligned = (monthIndex / step) * step
                LocalDate.of(d.year, aligned.toInt() + 1, 1).atStartOfDay(zone)
            }
            TickGranularity.YEAR -> LocalDate.of((startZ.year / step.toInt()) * step.toInt(), 1, 1).atStartOfDay(zone)
        }
        val result = mutableListOf<AxisTick>()
        var guard = 0
        while (t.toInstant().toEpochMilli() < end && guard++ < 2000) {
            val ms = t.toInstant().toEpochMilli()
            if (ms >= start) result += AxisTick(ms, g)
            t = when (g) {
                TickGranularity.HOUR -> t.plusHours(step)
                TickGranularity.DAY -> t.plusDays(step)
                TickGranularity.WEEK -> t.plusWeeks(step)
                TickGranularity.MONTH -> t.plusMonths(step)
                TickGranularity.YEAR -> t.plusYears(step)
            }
        }
        return result
    }

    /** Local midnights within [start, end): one per calendar day, including days without data. */
    fun dayBoundaries(start: Long, end: Long, zone: ZoneId, maxDays: Int = 120): List<Long> {
        if (end <= start) return emptyList()
        val first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val out = mutableListOf<Long>()
        var d = first
        while (out.size <= maxDays) {
            val ms = d.atStartOfDay(zone).toInstant().toEpochMilli()
            if (ms >= end) break
            if (ms >= start) out += ms
            d = d.plusDays(1)
        }
        return if (out.size > maxDays) emptyList() else out
    }

    /** Lists each calendar day in the window and whether it has at least one timestamp. */
    fun dayCoverage(window: TimeWindow, timestamps: List<Long>, zone: ZoneId): List<Pair<LocalDate, Boolean>> {
        val days = timestamps.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
        val first = Instant.ofEpochMilli(window.start).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(window.end - 1).atZone(zone).toLocalDate()
        val out = mutableListOf<Pair<LocalDate, Boolean>>()
        var d = first
        while (!d.isAfter(last)) { out += d to (d in days); d = d.plusDays(1) }
        return out
    }
}
