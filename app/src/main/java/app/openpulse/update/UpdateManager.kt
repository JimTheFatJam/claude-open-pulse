package app.openpulse.update

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.openpulse.OpenPulseApp
import app.openpulse.core.update.AvailableUpdate
import app.openpulse.core.update.ChecksumParser
import app.openpulse.core.update.ReleaseEvaluator
import app.openpulse.core.update.UpdateCheckResult
import app.openpulse.data.prefs.SettingsRepository
import app.openpulse.reminders.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NotOnWifi : UpdateState
    data class Info(val message: String) : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    data class Verifying(val update: AvailableUpdate) : UpdateState
    data class NeedsInstallPermission(val update: AvailableUpdate, val file: File) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class AwaitingConfirmation(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String, val update: AvailableUpdate? = null) : UpdateState
}

/**
 * Orchestrates update checks (Wi-Fi only), secure download, SHA-256 + signing-certificate
 * verification and hand-off to the system installer.
 */
class UpdateManager(
    private val context: Context,
    val config: UpdateConfig,
    private val client: GitHubReleaseClient,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val network: NetworkStatus,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val checkMutex = Mutex()
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    /** Called when the app opens. Runs via WorkManager, constrained to unmetered networks. */
    fun scheduleLaunchCheck() {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("update-check", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun check(manual: Boolean): Unit = checkMutex.withLock<Unit> {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Verifying || busy is UpdateState.Installing) return@withLock
        if (!config.isConfigured) {
            if (manual) _state.value = UpdateState.Info("No update repository is configured for this build.")
            return@withLock
        }
        // Strictly no update traffic on mobile data or when offline.
        if (!network.isOnWifi()) {
            if (manual) _state.value = UpdateState.NotOnWifi
            return@withLock
        }
        val s = settings.current()
        val now = System.currentTimeMillis()
        if (!manual && (!s.autoUpdateCheck || now - s.lastUpdateCheck < AUTO_CHECK_INTERVAL_MS)) return@withLock

        _state.value = UpdateState.Checking
        try {
            val release = client.latestRelease()
            settings.update { it.copy(lastUpdateCheck = now) }
            val result = release?.let { ReleaseEvaluator.evaluate(it, config.installedVersion) }
                ?: UpdateCheckResult.Unavailable("No releases have been published yet.")
            when (result) {
                is UpdateCheckResult.Available -> {
                    if (!manual && s.skippedVersion == result.update.version.toString()) {
                        _state.value = UpdateState.Idle
                    } else {
                        _state.value = UpdateState.Available(result.update)
                        _dialogVisible.value = true
                        if (!isAppInForeground()) Notifications.showUpdateAvailable(context, result.update.version.toString())
                    }
                }
                UpdateCheckResult.UpToDate -> _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                is UpdateCheckResult.Unavailable -> _state.value = if (manual) UpdateState.Info(result.reason) else UpdateState.Idle
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (manual) UpdateState.Failed(e.message ?: "Update check failed") else UpdateState.Idle
        }
    }

    fun downloadAndInstall(update: AvailableUpdate) {
        if (downloadJob?.isActive == true) return
        _dialogVisible.value = true
        downloadJob = scope.launch {
            if (!network.isOnWifi()) {
                _state.value = UpdateState.Failed("Connect to Wi-Fi to download the update.", update)
                return@launch
            }
            ApkInstaller.cleanup(context)
            val file = File(ApkInstaller.updatesDir(context), "open-pulse-update.apk")
            try {
                _state.value = UpdateState.Downloading(update, null)
                val checksumText = client.downloadText(update.checksum.downloadUrl)
                val expected = ChecksumParser.find(checksumText, update.apk.name)
                    ?: throw UpdateException("The published checksum does not list ${update.apk.name}")
                val actual = client.downloadFile(update.apk.downloadUrl, file, config.maxApkBytes) { p ->
                    _state.value = UpdateState.Downloading(update, p)
                }
                _state.value = UpdateState.Verifying(update)
                if (!MessageDigest.isEqual(expected.lowercase().toByteArray(), actual.lowercase().toByteArray())) {
                    throw UpdateException("Integrity check failed: SHA-256 checksum does not match")
                }
                when (val v = verifier.verify(file)) {
                    is ApkVerification.Invalid -> throw UpdateException("Update rejected: ${v.reason}")
                    ApkVerification.Valid -> Unit
                }
                if (!installer.canRequestInstalls()) {
                    _state.value = UpdateState.NeedsInstallPermission(update, file)
                    return@launch
                }
                install(update, file)
            } catch (e: CancellationException) {
                ApkInstaller.cleanup(context)
                throw e
            } catch (e: Exception) {
                ApkInstaller.cleanup(context)
                _state.value = UpdateState.Failed(e.message ?: "Update failed", update)
            }
        }
    }

    private fun install(update: AvailableUpdate, file: File) {
        _state.value = UpdateState.Installing(update)
        try {
            installer.install(file)
        } catch (e: Exception) {
            ApkInstaller.cleanup(context)
            _state.value = UpdateState.Failed("Could not start installation: ${e.message}", update)
        }
    }

    fun unknownSourcesIntent() = installer.unknownSourcesSettingsIntent()

    /** Call when returning from the "install unknown apps" settings screen. */
    fun retryAfterPermission() {
        val s = _state.value as? UpdateState.NeedsInstallPermission ?: return
        if (!installer.canRequestInstalls()) return
        if (!s.file.exists()) { _state.value = UpdateState.Available(s.update); return }
        // Re-verify: the file could have been touched while we were in Settings.
        when (val v = verifier.verify(s.file)) {
            is ApkVerification.Invalid -> { ApkInstaller.cleanup(context); _state.value = UpdateState.Failed("Update rejected: ${v.reason}", s.update) }
            ApkVerification.Valid -> install(s.update, s.file)
        }
    }

    fun onAwaitingConfirmation() {
        val u = currentUpdate() ?: return
        _state.value = UpdateState.AwaitingConfirmation(u)
    }

    fun onInstallFinished(success: Boolean, message: String?) {
        ApkInstaller.cleanup(context)
        _state.value = if (success) UpdateState.Idle else UpdateState.Failed(message ?: "Installation failed", currentUpdate())
        if (success) _dialogVisible.value = false
    }

    fun cancel() {
        downloadJob?.cancel()
        ApkInstaller.cleanup(context)
        val u = currentUpdate()
        _state.value = if (u != null) UpdateState.Available(u) else UpdateState.Idle
        _dialogVisible.value = false
    }

    fun showDialog() { _dialogVisible.value = true }
    fun dismissDialog() {
        _dialogVisible.value = false
        val s = _state.value
        if (s is UpdateState.Failed && s.update != null) _state.value = UpdateState.Available(s.update)
    }

    fun skip(update: AvailableUpdate) {
        scope.launch { settings.update { it.copy(skippedVersion = update.version.toString()) } }
        _state.value = UpdateState.Idle
        _dialogVisible.value = false
    }

    fun clearTransient() {
        val s = _state.value
        if (s is UpdateState.UpToDate || s is UpdateState.NotOnWifi || s is UpdateState.Info || (s is UpdateState.Failed && s.update == null)) {
            _state.value = UpdateState.Idle
        }
    }

    private fun currentUpdate(): AvailableUpdate? = when (val s = _state.value) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Verifying -> s.update
        is UpdateState.NeedsInstallPermission -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.AwaitingConfirmation -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        /** Automatic checks run at most hourly (GitHub allows 60 unauthenticated calls/hour). */
        const val AUTO_CHECK_INTERVAL_MS = 60 * 60 * 1000L
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as OpenPulseApp).container.updateManager.check(manual = false)
        return Result.success()
    }
}
