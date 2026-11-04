package app.openpulse.core

import app.openpulse.core.chart.ChartMath
import app.openpulse.core.model.DataPoint
import app.openpulse.core.time.TickGranularity
import app.openpulse.core.time.TimeWindow
import app.openpulse.core.time.TimelineAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChartAndAxisTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    @Test fun runsBreakAtGapsAndNeverInsertPoints() {
        val pts = listOf(
            DataPoint(at("2026-03-02T08:00"), 120.0), // Monday
            DataPoint(at("2026-03-03T08:00"), 122.0),
            DataPoint(at("2026-03-10T08:00"), 125.0), // week later
        )
        val runs = ChartMath.connectableRuns(pts, maxGapMillis = 3 * day)
        assertEquals(2, runs.size)
        assertEquals(2, runs[0].size)
        assertEquals(1, runs[1].size)
        assertEquals(3, runs.sumOf { it.size }) // nothing fabricated
    }

    @Test fun noConnectionsWhenDisabled() {
        val pts = listOf(DataPoint(1, 1.0), DataPoint(2, 2.0))
        assertEquals(2, ChartMath.connectableRuns(pts, null).size)
    }

    @Test fun dayBoundariesIncludeEmptyDays() {
        val w = TimeWindow(at("2026-03-01T00:00"), at("2026-03-08T00:00"))
        val days = TimelineAxis.dayBoundaries(w.start, w.end, zone)
        assertEquals(7, days.size) // all 7 days visible, despite DST change on 2026-03-08? (window ends before)
        val coverage = TimelineAxis.dayCoverage(w, listOf(at("2026-03-02T09:00"), at("2026-03-05T09:00")), zone)
        assertEquals(7, coverage.size)
        assertEquals(2, coverage.count { it.second })
        assertEquals(LocalDate.of(2026, 3, 1), coverage.first().first)
    }

    @Test fun tickGranularityAdaptsToSpan() {
        val start = at("2026-01-01T00:00")
        assertEquals(TickGranularity.HOUR, TimelineAxis.ticks(start, start + day, zone).first().granularity)
        assertEquals(TickGranularity.DAY, TimelineAxis.ticks(start, start + 7 * day, zone).first().granularity)
        assertEquals(TickGranularity.WEEK, TimelineAxis.ticks(start, start + 60 * day, zone).first().granularity)
        assertEquals(TickGranularity.MONTH, TimelineAxis.ticks(start, start + 365 * day, zone).first().granularity)
        val ticks = TimelineAxis.ticks(start, start + 365 * day, zone, maxTicks = 6)
        assertTrue(ticks.size <= 6)
        assertTrue(ticks.all { it.time >= start })
    }

    @Test fun niceScaleCoversRange() {
        val s = ChartMath.niceScale(72.3, 81.9)
        assertTrue(s.min <= 72.3 && s.max >= 81.9)
        assertTrue(s.ticks.size in 3..8)
        val flat = ChartMath.niceScale(80.0, 80.0, minSpan = 2.0)
        assertTrue(flat.max > flat.min)
    }

    @Test fun nearestIndexRespectsMaxDistance() {
        val xs = listOf(10f, 50f, 100f)
        val ys = listOf(0f, 0f, 0f)
        assertEquals(1, ChartMath.nearestIndex(xs, ys, 55f, 0f, 20f))
        assertEquals(null, ChartMath.nearestIndex(xs, ys, 75f, 0f, 20f))
    }
}

class ReminderScheduleTest {
    private val zone = java.time.ZoneId.of("Europe/London")
    private fun r(days: Set<Int>, h: Int = 8, m: Int = 0, enabled: Boolean = true) =
        app.openpulse.core.model.Reminder(title = "t", types = emptySet(), hour = h, minute = m, daysOfWeek = days, enabled = enabled)

    @Test fun nextTriggerSameDayOrNext() {
        val now = java.time.ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, zone) // Wednesday
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 4, 8, 0, 0, 0, zone), app.openpulse.core.time.ReminderSchedule.nextTrigger(r(emptySet()), now))
        val later = now.withHour(9)
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 5, 8, 0, 0, 0, zone), app.openpulse.core.time.ReminderSchedule.nextTrigger(r(emptySet()), later))
        // Only Mondays
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, zone), app.openpulse.core.time.ReminderSchedule.nextTrigger(r(setOf(1)), now))
        assertEquals(null, app.openpulse.core.time.ReminderSchedule.nextTrigger(r(emptySet(), enabled = false), now))
    }
}
