package app.openpulse.core.units

import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Formats canonical values for display in the user's preferred units, consistently app-wide. */
class ValueFormatter(
    private val units: UnitPreferences,
    private val locale: Locale = Locale.getDefault(),
) {
    fun unitFor(type: MeasurementType, channel: Channel = Channel.PRIMARY): MeasurementUnit {
        val spec = type.spec(channel) ?: type.primarySpec
        return units.unitFor(spec.unitKind)
    }

    /** Numeric part only, in the display unit (no symbol). */
    fun number(type: MeasurementType, channel: Channel, canonical: Double): String {
        val unit = unitFor(type, channel)
        val v = unit.fromCanonical(canonical)
        return when (unit) {
            MeasurementUnit.FEET_INCHES -> feetInches(v)
            MeasurementUnit.HOURS -> hoursMinutes(v)
            else -> decimal(v, unit.decimals)
        }
    }

    /** Number with unit symbol, e.g. "72.4 kg", "5′ 9″", "7 h 30 min". */
    fun withUnit(type: MeasurementType, channel: Channel, canonical: Double): String {
        val unit = unitFor(type, channel)
        return when (unit) {
            MeasurementUnit.FEET_INCHES, MeasurementUnit.HOURS -> number(type, channel, canonical)
            MeasurementUnit.PERCENT -> number(type, channel, canonical) + "%"
            else -> number(type, channel, canonical) + " " + unit.symbol
        }
    }

    /** Full value of a measurement, e.g. "128/82 mmHg · pulse 64 bpm". */
    fun measurement(m: Measurement, includePulse: Boolean = true): String = when (m.type) {
        MeasurementType.BLOOD_PRESSURE -> buildString {
            append(number(m.type, Channel.PRIMARY, m.value))
            append("/")
            append(m.secondaryValue?.let { number(m.type, Channel.SECONDARY, it) } ?: "–")
            append(" ")
            append(unitFor(m.type).symbol)
            if (includePulse && m.pulse != null) {
                append(" · pulse ")
                append(withUnit(m.type, Channel.PULSE, m.pulse))
            }
        }
        else -> withUnit(m.type, Channel.PRIMARY, m.value)
    }

    /** Signed difference, e.g. "+1.2 kg". [canonicalDelta] is a difference, so offsets don't apply. */
    fun delta(type: MeasurementType, channel: Channel, canonicalDelta: Double): String {
        val unit = unitFor(type, channel)
        val scaled = unit.fromCanonical(canonicalDelta) - unit.fromCanonical(0.0)
        val sign = if (scaled > 0) "+" else if (scaled < 0) "−" else "±"
        val magnitude = abs(scaled)
        val body = when (unit) {
            MeasurementUnit.FEET_INCHES -> decimal(magnitude, 1) + " in"
            MeasurementUnit.HOURS -> hoursMinutes(magnitude)
            MeasurementUnit.PERCENT -> decimal(magnitude, unit.decimals) + " pts"
            else -> decimal(magnitude, unit.decimals) + " " + unit.symbol
        }
        return sign + body
    }

    fun axisLabel(type: MeasurementType): String {
        val unit = unitFor(type)
        return if (unit == MeasurementUnit.FEET_INCHES) "in" else unit.symbol
    }

    fun decimal(v: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", v)

    companion object {
        fun feetInches(totalInches: Double): String {
            val rounded = (totalInches * 2).roundToInt() / 2.0 // nearest half inch
            val feet = floor(rounded / 12.0).toInt()
            val inches = rounded - feet * 12
            val inchText = if (inches % 1.0 == 0.0) inches.toInt().toString() else String.format(Locale.ROOT, "%.1f", inches)
            return "$feet′ $inchText″"
        }

        fun hoursMinutes(hours: Double): String {
            val totalMinutes = (hours * 60).roundToLong()
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h == 0L -> "$m min"
                m == 0L -> "$h h"
                else -> "$h h $m min"
            }
        }
    }
}
