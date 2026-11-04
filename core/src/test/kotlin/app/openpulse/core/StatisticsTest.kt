package app.openpulse.core

import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.stats.StatisticsCalculator
import app.openpulse.core.stats.Trend
import app.openpulse.core.time.TimeRange
import app.openpulse.core.time.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StatisticsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun m(type: MeasurementType, time: String, v: Double, v2: Double? = null, id: Long = 0) =
        Measurement(id = id, type = type, value = v, secondaryValue = v2, timestamp = at(time), zoneId = zone.id)

    @Test fun averagesUseOnlyRecordedValues_noZeroFill() {
        // BP recorded Monday and Thursday only
        val data = listOf(
            m(MeasurementType.BLOOD_PRESSURE, "2026-03-02T08:00", 120.0, 80.0, 1),
            m(MeasurementType.BLOOD_PRESSURE, "2026-03-05T08:00", 130.0, 84.0, 2),
        )
        val w = TimeWindow(at("2026-03-02T00:00"), at("2026-03-09T00:00"))
        val sys = StatisticsCalculator.compute(MeasurementType.BLOOD_PRESSURE, Channel.PRIMARY, data, w, null, zone)
        val dia = StatisticsCalculator.compute(MeasurementType.BLOOD_PRESSURE, Channel.SECONDARY, data, w, null, zone)
        assertEquals(2, sys.count)
        assertEquals(125.0, sys.average!!, 1e-9)
        assertEquals(82.0, dia.average!!, 1e-9)
        assertEquals(120.0, sys.min!!.value, 0.0)
        assertEquals(130.0, sys.max!!.value, 0.0)
        assertEquals(130.0, sys.latest!!.value, 0.0)
        assertEquals(120.0, sys.previous!!.value, 0.0)
    }

    @Test fun emptyWindowProducesNoStatistics() {
        val data = listOf(m(MeasurementType.WEIGHT, "2026-01-01T08:00", 80.0))
        val w = TimeWindow(at("2026-02-01T00:00"), at("2026-02-08T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.WEIGHT, Channel.PRIMARY, data, w, null, zone)
        assertEquals(0, s.count)
        assertNull(s.average)
        assertNull(s.min)
        assertEquals(Trend.INSUFFICIENT_DATA, s.trend.trend)
        // latest is still the most recent recorded value before the window end
        assertEquals(80.0, s.latest!!.value, 0.0)
    }

    @Test fun multipleSameDayEntriesAreAllCounted() {
        val data = listOf(
            m(MeasurementType.WEIGHT, "2026-03-02T07:00", 80.0, id = 1),
            m(MeasurementType.WEIGHT, "2026-03-02T07:00", 80.4, id = 2),
            m(MeasurementType.WEIGHT, "2026-03-02T21:00", 81.0, id = 3),
        )
        val w = TimeWindow(at("2026-03-02T00:00"), at("2026-03-03T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.WEIGHT, Channel.PRIMARY, data, w, null, zone)
        assertEquals(3, s.count)
        assertEquals((80.0 + 80.4 + 81.0) / 3, s.average!!, 1e-9)
    }

    @Test fun periodComparisonRequiresEnoughDataInBothPeriods() {
        val data = listOf(
            m(MeasurementType.WEIGHT, "2026-03-01T08:00", 80.0),
            m(MeasurementType.WEIGHT, "2026-03-09T08:00", 79.0),
            m(MeasurementType.WEIGHT, "2026-03-10T08:00", 79.5),
        )
        val w = TimeWindow(at("2026-03-08T00:00"), at("2026-03-15T00:00"))
        val pw = TimeWindow(at("2026-03-01T00:00"), at("2026-03-08T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.WEIGHT, Channel.PRIMARY, data, w, pw, zone)
        assertEquals(1, s.comparison!!.previousCount)
        assertNull(s.comparison!!.difference)
        assertNull(s.comparison!!.percentChange)
    }

    @Test fun periodComparisonAndPercentChange() {
        val data = listOf(
            m(MeasurementType.WEIGHT, "2026-03-01T08:00", 80.0),
            m(MeasurementType.WEIGHT, "2026-03-03T08:00", 80.0),
            m(MeasurementType.WEIGHT, "2026-03-09T08:00", 78.0),
            m(MeasurementType.WEIGHT, "2026-03-10T08:00", 78.0),
        )
        val w = TimeWindow(at("2026-03-08T00:00"), at("2026-03-15T00:00"))
        val pw = TimeWindow(at("2026-03-01T00:00"), at("2026-03-08T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.WEIGHT, Channel.PRIMARY, data, w, pw, zone)
        assertEquals(-2.0, s.comparison!!.difference!!, 1e-9)
        assertEquals(-2.5, s.comparison!!.percentChange!!, 1e-9)
    }

    @Test fun percentChangeNotReportedForTemperature() {
        val data = listOf(
            m(MeasurementType.BODY_TEMPERATURE, "2026-03-01T08:00", 36.5),
            m(MeasurementType.BODY_TEMPERATURE, "2026-03-02T08:00", 36.7),
            m(MeasurementType.BODY_TEMPERATURE, "2026-03-09T08:00", 37.0),
            m(MeasurementType.BODY_TEMPERATURE, "2026-03-10T08:00", 37.2),
        )
        val w = TimeWindow(at("2026-03-08T00:00"), at("2026-03-15T00:00"))
        val pw = TimeWindow(at("2026-03-01T00:00"), at("2026-03-08T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.BODY_TEMPERATURE, Channel.PRIMARY, data, w, pw, zone)
        assertEquals(0.5, s.comparison!!.difference!!, 1e-9)
        assertNull(s.comparison!!.percentChange)
    }

    @Test fun dailySumTypesSumPerDayAndSkipEmptyDays() {
        val data = listOf(
            m(MeasurementType.WATER, "2026-03-02T08:00", 250.0),
            m(MeasurementType.WATER, "2026-03-02T12:00", 500.0),
            m(MeasurementType.WATER, "2026-03-04T09:00", 1000.0),
        )
        val w = TimeWindow(at("2026-03-01T00:00"), at("2026-03-08T00:00"))
        val s = StatisticsCalculator.compute(MeasurementType.WATER, Channel.PRIMARY, data, w, null, zone)
        assertEquals(2, s.count) // two days with entries, not 7
        assertEquals(3, s.entryCount)
        assertEquals(875.0, s.average!!, 1e-9) // (750 + 1000) / 2
    }

    @Test fun trendNeedsEnoughPoints() {
        val pts = (0 until 3).map { app.openpulse.core.model.DataPoint(at("2026-03-0${it + 1}T08:00"), 80.0 - it) }
        assertEquals(Trend.INSUFFICIENT_DATA, StatisticsCalculator.trend(pts, 0.5).trend)
    }

    @Test fun trendNeedsEnoughSpan() {
        val pts = (0 until 6).map { app.openpulse.core.model.DataPoint(at("2026-03-01T0${it + 1}:00"), 80.0 - it) }
        assertEquals(Trend.INSUFFICIENT_DATA, StatisticsCalculator.trend(pts, 0.5).trend)
    }

    @Test fun trendDirections() {
        fun pts(f: (Int) -> Double) = (0 until 10).map { app.openpulse.core.model.DataPoint(at("2026-03-01T08:00") + it * 86_400_000L, f(it)) }
        assertEquals(Trend.DECREASING, StatisticsCalculator.trend(pts { 80.0 - it * 0.3 }, 0.5).trend)
        assertEquals(Trend.INCREASING, StatisticsCalculator.trend(pts { 80.0 + it * 0.3 }, 0.5).trend)
        assertEquals(Trend.STABLE, StatisticsCalculator.trend(pts { 80.0 + (it % 2) * 0.1 }, 0.5).trend)
        assertEquals(Trend.VARIABLE, StatisticsCalculator.trend(pts { if (it % 2 == 0) 70.0 else 90.0 + it * 0.1 }, 0.5).trend)
    }

    @Test fun timeRangeWindowsAreCalendarAware() {
        val now = Instant.parse("2026-03-29T10:00:00Z") // DST switch day in Europe/Berlin
        val w = TimeRange.WEEK.window(now, zone)
        assertEquals(at("2026-03-30T00:00"), w.end)
        assertEquals(at("2026-03-23T00:00"), w.start)
        assertEquals(7 * 86_400_000L - 3_600_000L, w.duration) // one hour shorter due to DST
        val prev = TimeRange.WEEK.previousWindow(w, zone)!!
        assertEquals(at("2026-03-16T00:00"), prev.start)
        assertEquals(w.start, prev.end)
        val day = TimeRange.DAY.window(now, zone)
        assertEquals(at("2026-03-29T00:00"), day.start)
    }

    @Test fun allTimeWindowStartsAtFirstRecordedDay() {
        val now = Instant.parse("2026-03-10T10:00:00Z")
        val w = TimeRange.ALL.window(now, zone, earliest = at("2025-01-15T13:30"))
        assertEquals(at("2025-01-15T00:00"), w.start)
        assertNull(TimeRange.ALL.previousWindow(w, zone))
    }
}
