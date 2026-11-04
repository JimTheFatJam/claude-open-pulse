package app.openpulse.core.model

/**
 * A single recorded measurement. All values are stored in the canonical unit of their
 * channel (see [app.openpulse.core.units.MeasurementUnit.canonicalFor]) so that changing the
 * preferred display unit never alters stored data.
 *
 * Entries are identified by [id]; two entries may share a date or even an exact time and are
 * never merged or overwritten because of that.
 */
data class Measurement(
    val id: Long = 0,
    val type: MeasurementType,
    /** Primary value (systolic for blood pressure). */
    val value: Double,
    /** Secondary value (diastolic for blood pressure). */
    val secondaryValue: Double? = null,
    /** Optional pulse recorded together with blood pressure. */
    val pulse: Double? = null,
    /** Instant of the measurement in epoch milliseconds (UTC). */
    val timestamp: Long,
    /** The time zone the user was in when recording; used to show original local time. */
    val zoneId: String,
    val note: String = "",
    val tags: List<String> = emptyList(),
    /** True only for generated sample data in demo mode. */
    val isDemo: Boolean = false,
    val createdAt: Long = timestamp,
    val updatedAt: Long = createdAt,
) {
    fun valueOf(channel: Channel): Double? = when (channel) {
        Channel.PRIMARY -> value
        Channel.SECONDARY -> secondaryValue
        Channel.PULSE -> pulse
    }

    /** Key used to detect identical records when importing, so re-imports don't duplicate. */
    fun dedupeKey(): String = listOf(type.key, timestamp, value, secondaryValue, pulse).joinToString("|")
}

/** A value at a point in time, optionally linked to the measurement it came from. */
data class DataPoint(
    val time: Long,
    val value: Double,
    val measurementId: Long? = null,
    /** Number of raw entries combined into this point (>1 only for daily totals). */
    val entryCount: Int = 1,
)

enum class Sex { FEMALE, MALE, OTHER }

data class HealthProfile(
    val dateOfBirth: java.time.LocalDate? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val initialWeightKg: Double? = null,
    /** Null means "use the device time zone". */
    val timeZoneId: String? = null,
    val goals: String = "",
    val medicalNotes: String = "",
)

data class Reminder(
    val id: Long = 0,
    val title: String,
    val types: Set<MeasurementType>,
    val hour: Int,
    val minute: Int,
    /** ISO day-of-week numbers 1 (Monday) .. 7 (Sunday). Empty = every day. */
    val daysOfWeek: Set<Int>,
    val enabled: Boolean = true,
)
