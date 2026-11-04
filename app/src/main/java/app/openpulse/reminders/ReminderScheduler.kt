package app.openpulse.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.openpulse.OpenPulseApp
import app.openpulse.core.model.Reminder
import app.openpulse.core.time.ReminderSchedule
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules local reminder notifications with WorkManager (survives reboots, no exact-alarm
 * permission needed). Each reminder has one pending one-shot job that re-schedules itself.
 */
class ReminderScheduler(private val context: Context) {
    private fun workName(id: Long) = "reminder-$id"

    fun schedule(reminder: Reminder) {
        val wm = WorkManager.getInstance(context)
        val next = ReminderSchedule.nextTrigger(reminder, ZonedDateTime.now())
        if (next == null) { wm.cancelUniqueWork(workName(reminder.id)); return }
        val delay = Duration.between(ZonedDateTime.now(), next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to reminder.id))
            .addTag("reminder")
            .build()
        wm.enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(id: Long) { WorkManager.getInstance(context).cancelUniqueWork(workName(id)) }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val container = (applicationContext as OpenPulseApp).container
        val reminder = container.reminderRepository.get(id) ?: return Result.success()
        if (!reminder.enabled) return Result.success()
        val text = if (reminder.types.isEmpty()) "Time to record your measurements."
        else "Time to record: " + reminder.types.joinToString { it.title }
        Notifications.showReminder(
            applicationContext, id.toInt(), reminder.title.ifBlank { "Measurement reminder" }, text,
            reminder.types.singleOrNull()?.key,
        )
        // Schedule the next occurrence (slightly in the future to avoid double-firing).
        container.reminderScheduler.schedule(reminder)
        return Result.success()
    }

    companion object { const val KEY_ID = "reminder_id" }
}
