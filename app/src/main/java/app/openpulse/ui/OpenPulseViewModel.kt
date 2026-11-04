package app.openpulse.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openpulse.AppContainer
import app.openpulse.core.demo.DemoDataGenerator
import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.Reminder
import app.openpulse.core.update.AvailableUpdate
import app.openpulse.data.prefs.UserSettings
import app.openpulse.data.prefs.zone
import app.openpulse.data.repo.ImportSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App-wide state shared by all screens (settings, profile, measurements) and their actions. */
class OpenPulseViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile: StateFlow<HealthProfile> = c.settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, HealthProfile())
    /** Newest first. Null until loaded from the database. */
    val measurements: StateFlow<List<Measurement>?> = c.measurementRepository.measurements
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val reminders: StateFlow<List<Reminder>> = c.reminderRepository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val updateState = c.updateManager.state
    val updateDialogVisible = c.updateManager.dialogVisible
    val updateConfig = c.updateManager.config

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun message(text: String) { _messages.tryEmit(text) }

    fun updateSettings(transform: (UserSettings) -> UserSettings) = viewModelScope.launch { c.settingsRepository.update(transform) }
    fun updateProfile(p: HealthProfile) = viewModelScope.launch { c.settingsRepository.updateProfile(p); message("Profile saved") }

    fun deleteMeasurement(id: Long) = viewModelScope.launch { c.measurementRepository.delete(id); message("Entry deleted") }

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            val zone = profile.value.zone()
            c.measurementRepository.insertDemo(DemoDataGenerator.generate(zone, LocalDate.now(zone)))
            c.settingsRepository.update { it.copy(demoMode = true) }
            message("Demo mode on – sample data only")
        } else {
            c.settingsRepository.update { it.copy(demoMode = false) }
            c.measurementRepository.deleteAllDemo()
            message("Demo mode off – sample data removed")
        }
    }

    fun saveReminder(r: Reminder) = viewModelScope.launch { c.reminderRepository.save(r) }
    fun deleteReminder(id: Long) = viewModelScope.launch { c.reminderRepository.delete(id) }

    fun exportCsv(uri: Uri) = io("Export failed") { message("Exported ${c.backupManager.exportCsv(uri)} measurements to CSV") }
    fun exportJson(uri: Uri) = io("Backup failed") { message("Backup saved (${c.backupManager.exportJson(uri)} measurements)") }
    fun importFile(uri: Uri) = io("Import failed") { summary("Imported", c.backupManager.importMerge(uri)) }
    fun restoreBackup(uri: Uri) = io("Restore failed") { summary("Restored", c.backupManager.restore(uri)) }

    private fun summary(verb: String, s: ImportSummary) {
        val parts = mutableListOf("$verb ${s.imported} measurements")
        if (s.duplicates > 0) parts += "${s.duplicates} duplicates skipped"
        if (s.invalid > 0 || s.messages.isNotEmpty()) parts += "${maxOf(s.invalid, s.messages.size)} invalid rows skipped"
        message(parts.joinToString(" · "))
    }

    private fun io(failure: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { message("$failure: ${e.message ?: e.javaClass.simpleName}") }
    }

    fun deleteAllMeasurements() = viewModelScope.launch {
        c.measurementRepository.deleteAllReal(); message("All measurements deleted")
    }

    /** Factory reset: all measurements, demo data, reminders, profile and settings. */
    fun resetEverything() = viewModelScope.launch {
        c.measurementRepository.deleteAllReal()
        c.measurementRepository.deleteAllDemo()
        c.reminderRepository.replaceAll(emptyList())
        c.settingsRepository.clearAll()
        message("All data has been deleted")
    }

    fun checkForUpdates() = viewModelScope.launch { c.updateManager.check(manual = true) }
    fun downloadUpdate(u: AvailableUpdate) = c.updateManager.downloadAndInstall(u)
    fun skipUpdate(u: AvailableUpdate) = c.updateManager.skip(u)
    fun cancelUpdate() = c.updateManager.cancel()
    fun dismissUpdateDialog() = c.updateManager.dismissDialog()
    fun showUpdateDialog() = c.updateManager.showDialog()
    fun retryInstallAfterPermission() = c.updateManager.retryAfterPermission()
    fun unknownSourcesIntent() = c.updateManager.unknownSourcesIntent()
    fun clearUpdateStatus() = c.updateManager.clearTransient()

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OpenPulseViewModel(c) }
        }
    }
}
