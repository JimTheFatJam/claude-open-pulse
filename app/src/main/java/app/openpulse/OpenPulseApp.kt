package app.openpulse

import android.app.Application
import androidx.work.Configuration
import app.openpulse.reminders.Notifications
import app.openpulse.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenPulseApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Leftover update files (e.g. app killed mid-download) are removed on every start.
        ApkInstaller.cleanup(this)
        appScope.launch { runCatching { container.reminderRepository.rescheduleAll() } }
    }
}
