package app.openpulse

import android.content.Context
import app.openpulse.data.db.OpenPulseDatabase
import app.openpulse.data.prefs.SettingsRepository
import app.openpulse.data.repo.BackupManager
import app.openpulse.data.repo.MeasurementRepository
import app.openpulse.data.repo.ReminderRepository
import app.openpulse.reminders.ReminderScheduler
import app.openpulse.update.ApkInstaller
import app.openpulse.update.ApkVerifier
import app.openpulse.update.GitHubReleaseClient
import app.openpulse.update.NetworkStatus
import app.openpulse.update.UpdateConfig
import app.openpulse.update.UpdateManager

/** Manual dependency container (keeps the dependency graph explicit and small). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: OpenPulseDatabase by lazy { OpenPulseDatabase.build(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val reminderScheduler by lazy { ReminderScheduler(appContext) }
    val measurementRepository by lazy { MeasurementRepository(database.measurementDao(), settingsRepository) }
    val reminderRepository by lazy { ReminderRepository(database.reminderDao(), reminderScheduler) }
    val backupManager by lazy { BackupManager(appContext, measurementRepository, reminderRepository, settingsRepository) }
    val updateManager by lazy {
        val config = UpdateConfig()
        UpdateManager(
            context = appContext,
            config = config,
            client = GitHubReleaseClient(config),
            verifier = ApkVerifier(appContext),
            installer = ApkInstaller(appContext),
            network = NetworkStatus(appContext),
            settings = settingsRepository,
        )
    }
}
