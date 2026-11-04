package app.openpulse.ui.format

import app.openpulse.core.time.TickGranularity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

object Formats {
    private val locale: Locale get() = Locale.getDefault()

    fun date(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(d: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(d)

    fun longDate(d: LocalDate): String = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale).format(d)

    fun time(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dateTime(millis: Long, zone: ZoneId): String = date(millis, zone) + " · " + time(millis, zone)

    fun dayHeader(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy", locale).format(d)
    }

    fun tick(millis: Long, granularity: TickGranularity, zone: ZoneId): String {
        val z = Instant.ofEpochMilli(millis).atZone(zone)
        val pattern = when (granularity) {
            TickGranularity.HOUR -> "HH:mm"
            TickGranularity.DAY -> "EEE d"
            TickGranularity.WEEK -> "d MMM"
            TickGranularity.MONTH -> if (z.monthValue == 1) "MMM yy" else "MMM"
            TickGranularity.YEAR -> "yyyy"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(z)
    }

    /** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", or a date. */
    fun relative(millis: Long, now: Long, zone: ZoneId): String {
        val diff = now - millis
        if (diff < 0) return "in the future"
        val minutes = diff / 60_000
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            days == 0L -> "${minutes / 60} h ago"
            days == 1L -> "yesterday"
            days < 30 -> "$days days ago"
            else -> date(millis, zone)
        }
    }

    fun percent(v: Double): String {
        val sign = if (v > 0) "+" else if (v < 0) "−" else "±"
        return sign + String.format(locale, "%.1f", abs(v)) + "%"
    }
}
