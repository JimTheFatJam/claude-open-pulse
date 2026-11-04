package app.openpulse.core.model

/**
 * The value "channels" a measurement can carry. Most types only use [PRIMARY].
 * Blood pressure uses PRIMARY = systolic, SECONDARY = diastolic and an optional PULSE.
 */
enum class Channel { PRIMARY, SECONDARY, PULSE }

/** Physical quantity families. Unit preferences are chosen per family. */
enum class UnitKind {
    PRESSURE, HEART_RATE, MASS, TEMPERATURE, PERCENT, HEIGHT, CIRCUMFERENCE,
    GLUCOSE, RESPIRATORY_RATE, DURATION, VOLUME, COUNT,
}

/**
 * How multiple entries are combined for charts and statistics.
 * [NONE]: every entry is an independent reading (blood pressure, weight, ...).
 * [DAILY_SUM]: entries are portions of a daily total (water, steps). Days WITHOUT
 * entries are never treated as zero - they simply have no value.
 */
enum class Aggregation { NONE, DAILY_SUM }

data class ChannelSpec(
    val channel: Channel,
    val label: String,
    val unitKind: UnitKind,
    /** Plausibility limits in the canonical unit; values outside are rejected. */
    val minValid: Double,
    val maxValid: Double,
    val required: Boolean = true,
)

enum class MeasurementType(
    /** Stable identifier used in the database and in exports. Never change. */
    val key: String,
    val title: String,
    val shortTitle: String,
    val channels: List<ChannelSpec>,
    /** Displayed with this many decimals in its canonical unit. */
    val decimals: Int,
    /** Enabled by default for new users. */
    val primaryCategory: Boolean,
    /** Absolute change (canonical unit) below which a trend is described as stable. */
    val stableThreshold: Double,
    /** Whether a relative (%) change is mathematically meaningful (ratio scale, not already %). */
    val percentChangeMeaningful: Boolean,
    val aggregation: Aggregation = Aggregation.NONE,
    val description: String,
) {
    BLOOD_PRESSURE(
        key = "blood_pressure", title = "Blood Pressure", shortTitle = "BP",
        channels = listOf(
            ChannelSpec(Channel.PRIMARY, "Systolic", UnitKind.PRESSURE, 50.0, 260.0),
            ChannelSpec(Channel.SECONDARY, "Diastolic", UnitKind.PRESSURE, 30.0, 160.0),
            ChannelSpec(Channel.PULSE, "Pulse", UnitKind.HEART_RATE, 25.0, 250.0, required = false),
        ),
        decimals = 0, primaryCategory = true, stableThreshold = 4.0, percentChangeMeaningful = true,
        description = "Systolic and diastolic pressure recorded together, with optional pulse.",
    ),
    RESTING_HEART_RATE(
        key = "resting_heart_rate", title = "Resting Heart Rate", shortTitle = "Resting HR",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Resting heart rate", UnitKind.HEART_RATE, 25.0, 220.0)),
        decimals = 0, primaryCategory = true, stableThreshold = 3.0, percentChangeMeaningful = true,
        description = "Heart rate measured at rest, e.g. after waking up.",
    ),
    WEIGHT(
        key = "weight", title = "Weight", shortTitle = "Weight",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Weight", UnitKind.MASS, 1.0, 500.0)),
        decimals = 1, primaryCategory = true, stableThreshold = 0.5, percentChangeMeaningful = true,
        description = "Body weight.",
    ),
    BODY_TEMPERATURE(
        key = "body_temperature", title = "Body Temperature", shortTitle = "Temp",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Temperature", UnitKind.TEMPERATURE, 30.0, 45.0)),
        decimals = 1, primaryCategory = true, stableThreshold = 0.2, percentChangeMeaningful = false,
        description = "Body temperature. Values vary by measurement site and time of day.",
    ),
    BLOOD_OXYGEN(
        key = "spo2", title = "Blood Oxygen (SpO₂)", shortTitle = "SpO₂",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "SpO₂", UnitKind.PERCENT, 50.0, 100.0)),
        decimals = 0, primaryCategory = true, stableThreshold = 1.0, percentChangeMeaningful = false,
        description = "Peripheral oxygen saturation from a pulse oximeter.",
    ),
    HEIGHT(
        key = "height", title = "Height", shortTitle = "Height",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Height", UnitKind.HEIGHT, 30.0, 272.0)),
        decimals = 1, primaryCategory = false, stableThreshold = 0.5, percentChangeMeaningful = true,
        description = "Track height changes over time. Your profile height is used for BMI.",
    ),
    WAIST(
        key = "waist", title = "Waist Circumference", shortTitle = "Waist",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Waist", UnitKind.CIRCUMFERENCE, 20.0, 300.0)),
        decimals = 1, primaryCategory = false, stableThreshold = 1.0, percentChangeMeaningful = true,
        description = "Waist circumference, measured midway between lowest rib and hip bone.",
    ),
    BODY_FAT(
        key = "body_fat", title = "Body Fat", shortTitle = "Body fat",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Body fat", UnitKind.PERCENT, 1.0, 75.0)),
        decimals = 1, primaryCategory = false, stableThreshold = 0.5, percentChangeMeaningful = false,
        description = "Body fat percentage, e.g. from a smart scale. Methods differ in accuracy.",
    ),
    BLOOD_GLUCOSE(
        key = "blood_glucose", title = "Blood Glucose", shortTitle = "Glucose",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Glucose", UnitKind.GLUCOSE, 10.0, 1000.0)),
        decimals = 0, primaryCategory = false, stableThreshold = 5.0, percentChangeMeaningful = true,
        description = "Capillary blood glucose. Tag readings as fasting or after meals for context.",
    ),
    RESPIRATORY_RATE(
        key = "respiratory_rate", title = "Respiratory Rate", shortTitle = "Resp. rate",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Respiratory rate", UnitKind.RESPIRATORY_RATE, 4.0, 60.0)),
        decimals = 0, primaryCategory = false, stableThreshold = 1.0, percentChangeMeaningful = true,
        description = "Breaths per minute at rest.",
    ),
    HEART_RATE(
        key = "heart_rate", title = "Heart Rate", shortTitle = "HR",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Heart rate", UnitKind.HEART_RATE, 25.0, 250.0)),
        decimals = 0, primaryCategory = false, stableThreshold = 3.0, percentChangeMeaningful = true,
        description = "Heart rate in any situation (separate from resting heart rate).",
    ),
    SLEEP(
        key = "sleep", title = "Sleep Duration", shortTitle = "Sleep",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Sleep", UnitKind.DURATION, 0.0, 24.0)),
        decimals = 1, primaryCategory = false, stableThreshold = 0.25, percentChangeMeaningful = true,
        description = "Hours slept. Record it with the date and time you woke up.",
    ),
    WATER(
        key = "water", title = "Water Intake", shortTitle = "Water",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Water", UnitKind.VOLUME, 1.0, 20000.0)),
        decimals = 0, primaryCategory = false, stableThreshold = 150.0, percentChangeMeaningful = true,
        aggregation = Aggregation.DAILY_SUM,
        description = "Water you drank. Entries on the same day are added up into a daily total.",
    ),
    STEPS(
        key = "steps", title = "Steps", shortTitle = "Steps",
        channels = listOf(ChannelSpec(Channel.PRIMARY, "Steps", UnitKind.COUNT, 1.0, 200000.0)),
        decimals = 0, primaryCategory = false, stableThreshold = 500.0, percentChangeMeaningful = true,
        aggregation = Aggregation.DAILY_SUM,
        description = "Manually entered step counts. Entries on the same day are added up.",
    );

    fun spec(channel: Channel): ChannelSpec? = channels.firstOrNull { it.channel == channel }
    val primarySpec: ChannelSpec get() = channels.first()

    /** Channels that are charted together on the same axis (same unit). Pulse is shown separately. */
    val chartChannels: List<ChannelSpec>
        get() = channels.filter { it.unitKind == primarySpec.unitKind }

    companion object {
        fun fromKey(key: String): MeasurementType? = entries.firstOrNull { it.key == key }
        val defaultEnabled: Set<MeasurementType> get() = entries.filter { it.primaryCategory }.toSet()
    }
}
