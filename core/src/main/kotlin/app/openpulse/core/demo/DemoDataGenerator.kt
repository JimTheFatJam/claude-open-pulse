package app.openpulse.core.demo

import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates clearly-flagged sample data ([Measurement.isDemo] = true) with a realistic,
 * irregular schedule: skipped days, a multi-day gap and several readings on some days.
 */
object DemoDataGenerator {
    fun generate(zone: ZoneId, today: LocalDate, days: Int = 120, seed: Int = 42): List<Measurement> {
        val rnd = Random(seed)
        val out = mutableListOf<Measurement>()
        val gapStart = today.minusDays(40); val gapEnd = today.minusDays(33) // a week without any data
        var weight = 82.0
        fun ts(day: LocalDate, h: Int, m: Int): Long =
            day.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
        fun add(type: MeasurementType, day: LocalDate, h: Int, m: Int, v: Double, v2: Double? = null, p: Double? = null, tags: List<String> = emptyList()) {
            val t = ts(day, h, m)
            if (t > Instant.now().toEpochMilli()) return
            out += Measurement(type = type, value = v, secondaryValue = v2, pulse = p, timestamp = t, zoneId = zone.id, tags = tags, note = "Sample data", isDemo = true, createdAt = t, updatedAt = t)
        }
        for (i in days downTo 0) {
            val day = today.minusDays(i.toLong())
            if (!day.isBefore(gapStart) && !day.isAfter(gapEnd)) continue
            weight += rnd.nextDouble(-0.25, 0.2)
            if (rnd.nextDouble() < 0.55) {
                val sys = 124 + rnd.nextInt(-8, 9) - (days - i) / 30
                val dia = 80 + rnd.nextInt(-6, 6)
                add(MeasurementType.BLOOD_PRESSURE, day, 7, rnd.nextInt(0, 50), sys.toDouble(), dia.toDouble(), (62 + rnd.nextInt(-5, 8)).toDouble(), listOf("Morning"))
                if (rnd.nextDouble() < 0.3) add(MeasurementType.BLOOD_PRESSURE, day, 21, rnd.nextInt(0, 50), (sys - 4 + rnd.nextInt(0, 6)).toDouble(), (dia - 2 + rnd.nextInt(0, 4)).toDouble(), null, listOf("Evening"))
            }
            if (rnd.nextDouble() < 0.45) add(MeasurementType.WEIGHT, day, 7, 5, (weight * 10).roundToInt() / 10.0)
            if (rnd.nextDouble() < 0.5) add(MeasurementType.RESTING_HEART_RATE, day, 6, 45, (60 + rnd.nextInt(-4, 6)).toDouble(), tags = listOf("Resting"))
            if (rnd.nextDouble() < 0.2) add(MeasurementType.BODY_TEMPERATURE, day, 8, 0, ((36.6 + rnd.nextDouble(-0.4, 0.4)) * 10).roundToInt() / 10.0)
            if (rnd.nextDouble() < 0.3) add(MeasurementType.BLOOD_OXYGEN, day, 8, 10, (97 + rnd.nextInt(-1, 3)).toDouble().coerceAtMost(100.0))
            if (rnd.nextDouble() < 0.6) add(MeasurementType.SLEEP, day, 6, 30, ((7.0 + rnd.nextDouble(-1.2, 1.0)) * 4).roundToInt() / 4.0)
            if (rnd.nextDouble() < 0.5) {
                repeat(rnd.nextInt(2, 6)) { k -> add(MeasurementType.WATER, day, 9 + k * 3, 0, (250 + rnd.nextInt(0, 3) * 50).toDouble()) }
            }
        }
        return out
    }
}
