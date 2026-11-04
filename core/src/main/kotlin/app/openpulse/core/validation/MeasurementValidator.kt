package app.openpulse.core.validation

import app.openpulse.core.model.Channel
import app.openpulse.core.model.MeasurementType

data class ValidationError(val channel: Channel?, val message: String)

/** Rejects physiologically implausible or inconsistent entries. Values are canonical. */
object MeasurementValidator {
    /** Allowed clock skew when checking that a measurement isn't in the future. */
    const val FUTURE_TOLERANCE_MS = 5 * 60 * 1000L
    /** Earliest accepted measurement date (1900-01-01). */
    const val EARLIEST_TIMESTAMP = -2208988800000L

    fun validate(
        type: MeasurementType,
        values: Map<Channel, Double?>,
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (spec in type.channels) {
            val v = values[spec.channel]
            if (v == null) {
                if (spec.required) errors += ValidationError(spec.channel, "${spec.label} is required")
                continue
            }
            if (v.isNaN() || v.isInfinite()) {
                errors += ValidationError(spec.channel, "${spec.label} is not a valid number")
            } else if (v < spec.minValid || v > spec.maxValid) {
                errors += ValidationError(spec.channel, "${spec.label} is outside the plausible range")
            }
        }
        if (type == MeasurementType.BLOOD_PRESSURE) {
            val sys = values[Channel.PRIMARY]
            val dia = values[Channel.SECONDARY]
            if (sys != null && dia != null && dia >= sys) {
                errors += ValidationError(Channel.SECONDARY, "Diastolic must be lower than systolic")
            }
        }
        if (timestamp > now + FUTURE_TOLERANCE_MS) errors += ValidationError(null, "Date and time cannot be in the future")
        if (timestamp < EARLIEST_TIMESTAMP) errors += ValidationError(null, "Date is too far in the past")
        return errors
    }

    /** Parses user input, accepting either '.' or ',' as decimal separator. Returns null if invalid. */
    fun parseNumber(input: String): Double? {
        val cleaned = input.trim().replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
        return cleaned.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }
}
