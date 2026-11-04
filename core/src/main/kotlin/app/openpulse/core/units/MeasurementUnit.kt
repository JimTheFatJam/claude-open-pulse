package app.openpulse.core.units

import app.openpulse.core.model.UnitKind

/**
 * Units with conversions to and from the canonical (storage) unit of their [kind].
 * Canonical units: mmHg, bpm, kg, °C, %, cm, mg/dL, breaths/min, hours, mL, steps.
 */
enum class MeasurementUnit(
    val symbol: String,
    val kind: UnitKind,
    private val toCanonicalFn: (Double) -> Double,
    private val fromCanonicalFn: (Double) -> Double,
    /** Decimals used when displaying a value in this unit. */
    val decimals: Int,
) {
    MMHG("mmHg", UnitKind.PRESSURE, { it }, { it }, 0),
    BPM("bpm", UnitKind.HEART_RATE, { it }, { it }, 0),
    KILOGRAM("kg", UnitKind.MASS, { it }, { it }, 1),
    POUND("lb", UnitKind.MASS, { it * LB_TO_KG }, { it / LB_TO_KG }, 1),
    CELSIUS("°C", UnitKind.TEMPERATURE, { it }, { it }, 1),
    FAHRENHEIT("°F", UnitKind.TEMPERATURE, { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }, 1),
    PERCENT("%", UnitKind.PERCENT, { it }, { it }, 1),
    CENTIMETER_HEIGHT("cm", UnitKind.HEIGHT, { it }, { it }, 1),
    /** Height in feet and inches. The numeric value in this unit is total inches. */
    FEET_INCHES("ft/in", UnitKind.HEIGHT, { it * INCH_TO_CM }, { it / INCH_TO_CM }, 1),
    CENTIMETER("cm", UnitKind.CIRCUMFERENCE, { it }, { it }, 1),
    INCH("in", UnitKind.CIRCUMFERENCE, { it * INCH_TO_CM }, { it / INCH_TO_CM }, 1),
    MG_DL("mg/dL", UnitKind.GLUCOSE, { it }, { it }, 0),
    MMOL_L("mmol/L", UnitKind.GLUCOSE, { it * GLUCOSE_MG_DL_PER_MMOL_L }, { it / GLUCOSE_MG_DL_PER_MMOL_L }, 1),
    BREATHS_PER_MIN("breaths/min", UnitKind.RESPIRATORY_RATE, { it }, { it }, 0),
    HOURS("h", UnitKind.DURATION, { it }, { it }, 1),
    MILLILITER("mL", UnitKind.VOLUME, { it }, { it }, 0),
    LITER("L", UnitKind.VOLUME, { it * 1000.0 }, { it / 1000.0 }, 2),
    STEPS("steps", UnitKind.COUNT, { it }, { it }, 0);

    fun toCanonical(value: Double): Double = toCanonicalFn(value)
    fun fromCanonical(value: Double): Double = fromCanonicalFn(value)

    companion object {
        const val LB_TO_KG = 0.45359237
        const val INCH_TO_CM = 2.54
        /** Molar mass of glucose 180.156 g/mol → 1 mmol/L = 18.0156 mg/dL. */
        const val GLUCOSE_MG_DL_PER_MMOL_L = 18.0156

        fun canonicalFor(kind: UnitKind): MeasurementUnit = when (kind) {
            UnitKind.PRESSURE -> MMHG
            UnitKind.HEART_RATE -> BPM
            UnitKind.MASS -> KILOGRAM
            UnitKind.TEMPERATURE -> CELSIUS
            UnitKind.PERCENT -> PERCENT
            UnitKind.HEIGHT -> CENTIMETER_HEIGHT
            UnitKind.CIRCUMFERENCE -> CENTIMETER
            UnitKind.GLUCOSE -> MG_DL
            UnitKind.RESPIRATORY_RATE -> BREATHS_PER_MIN
            UnitKind.DURATION -> HOURS
            UnitKind.VOLUME -> MILLILITER
            UnitKind.COUNT -> STEPS
        }

        fun optionsFor(kind: UnitKind): List<MeasurementUnit> = entries.filter { it.kind == kind }

        /** Unit kinds where the user can choose between several units. */
        val configurableKinds: List<UnitKind>
            get() = UnitKind.entries.filter { optionsFor(it).size > 1 }

        fun fromName(name: String?): MeasurementUnit? = entries.firstOrNull { it.name == name }
    }
}

/** The user's preferred display unit per kind. Missing kinds fall back to canonical units. */
data class UnitPreferences(val selected: Map<UnitKind, MeasurementUnit> = emptyMap()) {
    fun unitFor(kind: UnitKind): MeasurementUnit =
        selected[kind]?.takeIf { it.kind == kind } ?: MeasurementUnit.canonicalFor(kind)

    fun with(unit: MeasurementUnit): UnitPreferences = copy(selected = selected + (unit.kind to unit))

    companion object {
        val METRIC = UnitPreferences()
        val IMPERIAL = UnitPreferences(
            mapOf(
                UnitKind.MASS to MeasurementUnit.POUND,
                UnitKind.TEMPERATURE to MeasurementUnit.FAHRENHEIT,
                UnitKind.HEIGHT to MeasurementUnit.FEET_INCHES,
                UnitKind.CIRCUMFERENCE to MeasurementUnit.INCH,
            ),
        )
    }
}
