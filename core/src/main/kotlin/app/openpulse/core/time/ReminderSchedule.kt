package app.openpulse.core.time

import app.openpulse.core.model.Reminder
import java.time.ZonedDateTime

object ReminderSchedule {
    /** Next time strictly after [now] at which [reminder] should fire, or null if it never fires. */
    fun nextTrigger(reminder: Reminder, now: ZonedDateTime): ZonedDateTime? {
        if (!reminder.enabled) return null
        val days = reminder.daysOfWeek.filter { it in 1..7 }.toSet()
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (days.isNotEmpty() && date.dayOfWeek.value !in days) continue
            // atStartOfDay/withHour resolve DST gaps by moving forward, so the reminder still fires.
            val candidate = date.atStartOfDay(now.zone).withHour(reminder.hour).withMinute(reminder.minute)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
