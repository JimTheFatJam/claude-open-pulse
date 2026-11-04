package app.openpulse.core

import app.openpulse.core.health.BodyMetrics
import app.openpulse.core.health.BmiCategory
import app.openpulse.core.health.ReferenceRanges
import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.UnitKind
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.core.units.UnitPreferences
import app.openpulse.core.units.ValueFormatter
import app.openpulse.core.validation.MeasurementValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class UnitsValidationTest {
    @Test fun conversionsRoundTrip() {
        for (u in MeasurementUnit.entries) {
            val v = 37.25
            assertEquals(u.name, v, u.fromCanonical(u.toCanonical(v)), 1e-9)
        }
        assertEquals(98.6, MeasurementUnit.FAHRENHEIT.fromCanonical(37.0), 1e-9)
        assertEquals(1.0, MeasurementUnit.POUND.toCanonical(2.20462262185), 1e-9)
        assertEquals(5.55, MeasurementUnit.MMOL_L.fromCanonical(100.0), 0.01)
        assertEquals(2500.0, MeasurementUnit.LITER.toCanonical(2.5), 1e-9)
    }

    @Test fun formatterUsesPreferredUnits() {
        val f = ValueFormatter(UnitPreferences.IMPERIAL, Locale.US)
        assertEquals("176.4 lb", f.withUnit(MeasurementType.WEIGHT, Channel.PRIMARY, 80.0))
        assertEquals("5′ 9″", f.withUnit(MeasurementType.HEIGHT, Channel.PRIMARY, 175.26))
        assertEquals("98.6 °F", f.withUnit(MeasurementType.BODY_TEMPERATURE, Channel.PRIMARY, 37.0))
        // A temperature *difference* must not apply the 32° offset
        assertEquals("+0.9 °F", f.delta(MeasurementType.BODY_TEMPERATURE, Channel.PRIMARY, 0.5))
        val metric = ValueFormatter(UnitPreferences.METRIC, Locale.US)
        val bp = Measurement(type = MeasurementType.BLOOD_PRESSURE, value = 128.0, secondaryValue = 82.0, pulse = 64.0, timestamp = 0, zoneId = "UTC")
        assertEquals("128/82 mmHg · pulse 64 bpm", metric.measurement(bp))
        assertEquals("7 h 30 min", metric.withUnit(MeasurementType.SLEEP, Channel.PRIMARY, 7.5))
        assertEquals("97%", metric.withUnit(MeasurementType.BLOOD_OXYGEN, Channel.PRIMARY, 97.0).replace(".0", ""))
    }

    @Test fun unitPreferencesIgnoreWrongKind() {
        val p = UnitPreferences(mapOf(UnitKind.MASS to MeasurementUnit.CELSIUS))
        assertEquals(MeasurementUnit.KILOGRAM, p.unitFor(UnitKind.MASS))
    }

    @Test fun validation() {
        val now = 1_800_000_000_000L
        val ok = MeasurementValidator.validate(MeasurementType.BLOOD_PRESSURE, mapOf(Channel.PRIMARY to 120.0, Channel.SECONDARY to 80.0, Channel.PULSE to null), now, now)
        assertTrue(ok.isEmpty())
        val inverted = MeasurementValidator.validate(MeasurementType.BLOOD_PRESSURE, mapOf(Channel.PRIMARY to 80.0, Channel.SECONDARY to 120.0), now, now)
        assertTrue(inverted.any { it.channel == Channel.SECONDARY })
        val missing = MeasurementValidator.validate(MeasurementType.BLOOD_PRESSURE, mapOf(Channel.PRIMARY to 120.0), now, now)
        assertEquals(1, missing.size)
        val future = MeasurementValidator.validate(MeasurementType.WEIGHT, mapOf(Channel.PRIMARY to 80.0), now + 3_600_000, now)
        assertTrue(future.any { it.channel == null })
        val implausible = MeasurementValidator.validate(MeasurementType.BLOOD_OXYGEN, mapOf(Channel.PRIMARY to 101.0), now, now)
        assertEquals(1, implausible.size)
        val zero = MeasurementValidator.validate(MeasurementType.WEIGHT, mapOf(Channel.PRIMARY to 0.0), now, now)
        assertEquals(1, zero.size)
    }

    @Test fun parseNumberAcceptsCommaDecimal() {
        assertEquals(72.5, MeasurementValidator.parseNumber("72,5")!!, 0.0)
        assertEquals(72.5, MeasurementValidator.parseNumber(" 72.5 ")!!, 0.0)
        assertNull(MeasurementValidator.parseNumber("7.2.5"))
        assertNull(MeasurementValidator.parseNumber("abc"))
        assertNull(MeasurementValidator.parseNumber(""))
    }

    @Test fun bmiAndAge() {
        assertEquals(24.69, BodyMetrics.bmi(80.0, 180.0)!!, 0.01)
        assertNull(BodyMetrics.bmi(80.0, null))
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(24.69, 35))
        assertNull(BodyMetrics.bmiCategory(24.69, 15)) // not applicable to children
        assertNull(BodyMetrics.bmiCategory(24.69, null))
        assertEquals(36, BodyMetrics.ageYears(LocalDate.of(1990, 11, 5), LocalDate.of(2026, 11, 5)))
        assertEquals(35, BodyMetrics.ageYears(LocalDate.of(1990, 11, 5), LocalDate.of(2026, 11, 4)))
        assertNull(BodyMetrics.ageYears(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 11, 4)))
    }

    @Test fun bloodPressureCategoryRequiresMultipleReadingsAndAdult() {
        assertNull(ReferenceRanges.bloodPressureCategory(150.0, 95.0, readings = 1, ageYears = 40))
        assertNull(ReferenceRanges.bloodPressureCategory(150.0, 95.0, readings = 5, ageYears = null))
        assertEquals("Stage 2 hypertension range", ReferenceRanges.bloodPressureCategory(150.0, 95.0, 5, 40))
        assertEquals("Stage 1 hypertension range", ReferenceRanges.bloodPressureCategory(118.0, 82.0, 5, 40))
        assertEquals("Elevated range", ReferenceRanges.bloodPressureCategory(125.0, 78.0, 5, 40))
    }
}
