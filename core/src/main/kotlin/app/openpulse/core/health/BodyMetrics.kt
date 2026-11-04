package app.openpulse.core.health

import java.time.LocalDate
import java.time.Period

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Below 18.5 (WHO: underweight)"),
    HEALTHY("18.5–24.9 (WHO: healthy weight range)"),
    OVERWEIGHT("25.0–29.9 (WHO: overweight)"),
    OBESE("30.0 or higher (WHO: obesity)"),
}

object BodyMetrics {
    /** Age in completed years on [today], or null if the birth date is in the future. */
    fun ageYears(dateOfBirth: LocalDate, today: LocalDate): Int? =
        if (dateOfBirth.isAfter(today)) null else Period.between(dateOfBirth, today).years

    /** BMI = kg / m². Returns null for missing or non-positive inputs. */
    fun bmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null || weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /**
     * WHO adult BMI reference bands. Only applicable to adults (20+); for younger people
     * BMI-for-age percentiles are needed, so null is returned. Not a diagnosis: BMI does not
     * account for muscle mass, body composition, ethnicity or other individual factors.
     */
    fun bmiCategory(bmi: Double, ageYears: Int?): BmiCategory? {
        if (ageYears == null || ageYears < 20) return null
        return when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 25.0 -> BmiCategory.HEALTHY
            bmi < 30.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }
    }
}
