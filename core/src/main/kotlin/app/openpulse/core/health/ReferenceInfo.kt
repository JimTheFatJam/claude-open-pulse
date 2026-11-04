package app.openpulse.core.health

import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Sex

/**
 * General, population-level reference information. This is NOT personalised medical advice
 * and is never used to label an individual reading as normal or abnormal.
 */
data class ReferenceInfo(
    val summary: String,
    val details: List<String>,
    val source: String,
    /** Optional general band (canonical units) that can be shaded on charts on request. */
    val bandLow: Double? = null,
    val bandHigh: Double? = null,
    val applicability: String = "General reference for adults at rest.",
)

object ReferenceRanges {
    const val DISCLAIMER =
        "Reference information is general and population-based. It is not a diagnosis and may not " +
            "apply to you. Individual targets depend on age, health conditions, medication and " +
            "measurement technique. Discuss your readings with a healthcare professional."

    fun forType(type: MeasurementType, ageYears: Int?, sex: Sex?): ReferenceInfo? {
        val adult = ageYears == null || ageYears >= 18
        if (!adult) {
            return ReferenceInfo(
                summary = "Reference ranges for children and adolescents differ by age.",
                details = listOf("Adult reference values are not shown because your profile age is under 18."),
                source = "—",
                applicability = "Not applicable under 18.",
            )
        }
        return when (type) {
            MeasurementType.BLOOD_PRESSURE -> ReferenceInfo(
                summary = "AHA/ACC 2017 adult categories are based on averages of multiple readings, not single values.",
                details = listOf(
                    "Normal: systolic < 120 and diastolic < 80 mmHg",
                    "Elevated: systolic 120–129 and diastolic < 80",
                    "Stage 1 hypertension: systolic 130–139 or diastolic 80–89",
                    "Stage 2 hypertension: systolic ≥ 140 or diastolic ≥ 90",
                    "Above 180 systolic and/or 120 diastolic: seek prompt medical attention, especially with symptoms",
                ),
                source = "American Heart Association / American College of Cardiology, 2017 Guideline",
            )
            MeasurementType.RESTING_HEART_RATE -> ReferenceInfo(
                summary = "A typical adult resting heart rate is 60–100 bpm.",
                details = listOf("Well-trained athletes and some medications can produce lower values."),
                source = "American Heart Association",
                bandLow = 60.0, bandHigh = 100.0,
            )
            MeasurementType.HEART_RATE -> ReferenceInfo(
                summary = "Heart rate varies widely with activity, stress, temperature and medication.",
                details = listOf("Resting values for adults are typically 60–100 bpm."),
                source = "American Heart Association",
            )
            MeasurementType.BLOOD_OXYGEN -> ReferenceInfo(
                summary = "Healthy adults commonly read about 95–100% on a pulse oximeter.",
                details = listOf(
                    "Readings can be affected by cold hands, movement, nail polish and skin pigmentation.",
                    "Some lung conditions have different individual targets.",
                ),
                source = "U.S. FDA pulse oximeter guidance; WHO pulse oximetry training manual",
                bandLow = 95.0, bandHigh = 100.0,
            )
            MeasurementType.BODY_TEMPERATURE -> ReferenceInfo(
                summary = "Typical adult body temperature is around 36.1–37.2 °C (97–99 °F).",
                details = listOf("Varies by measurement site (oral, ear, armpit, forehead), time of day and activity."),
                source = "MedlinePlus (U.S. National Library of Medicine)",
                bandLow = 36.1, bandHigh = 37.2,
            )
            MeasurementType.RESPIRATORY_RATE -> ReferenceInfo(
                summary = "Typical adult resting respiratory rate is 12–20 breaths per minute.",
                details = emptyList(),
                source = "MedlinePlus (U.S. National Library of Medicine)",
                bandLow = 12.0, bandHigh = 20.0,
            )
            MeasurementType.BLOOD_GLUCOSE -> ReferenceInfo(
                summary = "Context matters: fasting and after-meal values have different reference ranges.",
                details = listOf(
                    "Fasting plasma glucose for adults without diabetes: 70–99 mg/dL (3.9–5.5 mmol/L)",
                    "Diagnosis requires laboratory testing; home meters are for monitoring only.",
                    "People with diabetes should follow targets agreed with their care team.",
                ),
                source = "American Diabetes Association, Standards of Care",
            )
            MeasurementType.SLEEP -> ReferenceInfo(
                summary = "Adults are generally recommended to sleep 7 or more hours per night.",
                details = emptyList(),
                source = "American Academy of Sleep Medicine / Sleep Research Society consensus",
            )
            MeasurementType.WAIST -> ReferenceInfo(
                summary = "WHO uses waist circumference as one indicator of cardiometabolic risk.",
                details = when (sex) {
                    Sex.FEMALE -> listOf("Increased risk threshold used by WHO for women: > 80 cm")
                    Sex.MALE -> listOf("Increased risk threshold used by WHO for men: > 94 cm")
                    else -> listOf("WHO thresholds: > 94 cm for men, > 80 cm for women (vary by population)")
                },
                source = "WHO Expert Consultation on Waist Circumference, 2008",
            )
            MeasurementType.WEIGHT -> ReferenceInfo(
                summary = "Weight alone has no reference range; BMI uses it together with height.",
                details = listOf("Set your height in the profile to see BMI."),
                source = "World Health Organization",
            )
            MeasurementType.BODY_FAT -> ReferenceInfo(
                summary = "There is no single accepted reference range; values depend on method, age and sex.",
                details = listOf("Consumer scales can differ by several percentage points from clinical methods."),
                source = "—",
            )
            MeasurementType.WATER, MeasurementType.STEPS, MeasurementType.HEIGHT -> null
        }
    }

    /**
     * AHA/ACC 2017 category for an AVERAGE blood pressure. Returns null when fewer than
     * [minReadings] readings are available or the user may be under 18, because a single
     * reading is not sufficient evidence and paediatric categories differ.
     */
    fun bloodPressureCategory(avgSystolic: Double, avgDiastolic: Double, readings: Int, ageYears: Int?, minReadings: Int = 2): String? {
        if (readings < minReadings || ageYears == null || ageYears < 18) return null
        return when {
            avgSystolic >= 140 || avgDiastolic >= 90 -> "Stage 2 hypertension range"
            avgSystolic >= 130 || avgDiastolic >= 80 -> "Stage 1 hypertension range"
            avgSystolic >= 120 -> "Elevated range"
            else -> "Normal range"
        }
    }
}
