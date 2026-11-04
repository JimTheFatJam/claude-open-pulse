package app.openpulse

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.security.AppLock
import app.openpulse.ui.OpenPulseRoot
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.theme.OpenPulseTheme

class MainActivity : FragmentActivity() {
    private val container get() = (application as OpenPulseApp).container
    private val viewModel: OpenPulseViewModel by viewModels { OpenPulseViewModel.factory(container) }

    private var locked by mutableStateOf(true)
    private var lockError by mutableStateOf<String?>(null)
    private var backgroundedAt = 0L
    private var pendingRecordType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRecordType = intent?.getStringExtra(EXTRA_RECORD_TYPE)
        if (savedInstanceState != null) locked = savedInstanceState.getBoolean(KEY_LOCKED, true)

        // Automatic update check on open (WorkManager + Wi-Fi-only checks inside).
        container.updateManager.scheduleLaunchCheck()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val s = settings
            LaunchedEffect(s?.secureScreen) {
                if (s?.secureScreen == true) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            OpenPulseTheme(themeMode = s?.themeMode ?: app.openpulse.data.prefs.ThemeMode.SYSTEM, dynamicColor = s?.dynamicColor ?: false) {
                OpenPulseRoot(
                    viewModel = viewModel,
                    isLocked = s?.appLock == true && locked,
                    lockError = lockError,
                    onUnlock = ::unlock,
                    pendingRecordType = pendingRecordType,
                    onPendingRecordConsumed = { pendingRecordType = null },
                )
            }
            LaunchedEffect(s?.appLock) {
                if (s != null && !s.appLock) locked = false
                else if (s?.appLock == true && locked) unlock()
            }
        }
    }

    private fun unlock() {
        if (!AppLock.isAvailable(this)) {
            // Screen lock was removed from the device: don't lock the user out of their data.
            lockError = "No device screen lock is set up. App lock is inactive until you add one."
            locked = false
            return
        }
        AppLock.authenticate(
            this,
            onSuccess = { locked = false; lockError = null },
            onError = { lockError = it },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_RECORD_TYPE)?.let { pendingRecordType = it }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > AppLock.RELOCK_AFTER_MS) {
            if (viewModel.settings.value?.appLock == true) { locked = true; unlock() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the "install unknown apps" settings screen.
        viewModel.retryInstallAfterPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    companion object {
        const val EXTRA_RECORD_TYPE = "record_type"
        private const val KEY_LOCKED = "locked"
    }
}
