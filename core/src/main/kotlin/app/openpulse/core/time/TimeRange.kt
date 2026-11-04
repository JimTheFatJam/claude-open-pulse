package app.openpulse.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime

/** A half-open time window [start, end) in epoch milliseconds. */
data class TimeWindow(val start: Long, val end: Long) {
    init { require(end >= start) { "end must be >= start" } }
    val duration: Long get() = end - start
    operator fun contains(t: Long): Boolean = t >= start && t < end
}

/** Selectable chart/statistics periods. Windows end at the end of the current local day. */
enum class TimeRange(val label: String, val shortLabel: String, private val period: Period?) {
    DAY("Day", "1D", Period.ofDays(1)),
    WEEK("Week", "1W", Period.ofWeeks(1)),
    MONTH("Month", "1M", Period.ofMonths(1)),
    THREE_MONTHS("3 Months", "3M", Period.ofMonths(3)),
    SIX_MONTHS("6 Months", "6M", Period.ofMonths(6)),
    YEAR("Year", "1Y", Period.ofYears(1)),
    ALL("All time", "All", null);

    /**
     * Current window. [earliest] is the oldest recorded timestamp (for [ALL]).
     * Uses calendar arithmetic in [zone], so DST transitions and month lengths are respected.
     */
    fun window(now: Instant, zone: ZoneId, earliest: Long? = null): TimeWindow {
        val endOfToday = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone)
        val start: ZonedDateTime = if (period != null) {
            endOfToday.minus(period)
        } else {
            val first = earliest?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone).atStartOfDay(zone) }
            val minStart = endOfToday.minusDays(7)
            if (first == null || first.isAfter(minStart)) minStart else first
        }
        return TimeWindow(start.toInstant().toEpochMilli(), endOfToday.toInstant().toEpochMilli())
    }

    /** The equally long window immediately before [current], or null for [ALL]. */
    fun previousWindow(current: TimeWindow, zone: ZoneId): TimeWindow? {
        val p = period ?: return null
        val end = Instant.ofEpochMilli(current.start).atZone(zone)
        return TimeWindow(end.minus(p).toInstant().toEpochMilli(), current.start)
    }
}
