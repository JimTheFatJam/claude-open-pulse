package app.openpulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.BuildConfig
import app.openpulse.core.units.UnitPreferences
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.update.UpdateState

@Composable
fun OnboardingDialog(vm: OpenPulseViewModel) {
    var imperial by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to Open Pulse") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("A private health journal for the measurements you take at home. Everything stays on this device – no account, no cloud.")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open Pulse is not a medical device and does not diagnose conditions or suggest treatments. " +
                        "Always discuss your health with a qualified professional.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("Units", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!imperial, { imperial = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Metric") }
                    SegmentedButton(imperial, { imperial = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Imperial") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(demo, { demo = it })
                    Text("Explore with sample data first (demo mode)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings { it.copy(onboardingDone = true, units = if (imperial) UnitPreferences.IMPERIAL else UnitPreferences.METRIC) }
                if (demo) vm.setDemoMode(true)
            }) { Text("I understand – start") }
        },
    )
}

@Composable
fun UpdateDialog(vm: OpenPulseViewModel) {
    val visible by vm.updateDialogVisible.collectAsStateWithLifecycle()
    val state by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (!visible) return
    val s = state
    val update = when (s) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Verifying -> s.update
        is UpdateState.NeedsInstallPermission -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.AwaitingConfirmation -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    } ?: return
    val busy = s is UpdateState.Downloading || s is UpdateState.Verifying || s is UpdateState.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) vm.dismissUpdateDialog() },
        title = { Text("Update available: ${update.version}") },
        text = {
            Column {
                Text("Installed: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Release notes", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(update.releaseNotes.ifBlank { "No release notes were provided." }, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                when (s) {
                    is UpdateState.Downloading -> {
                        Text("Downloading over Wi-Fi…", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        if (s.progress != null) LinearProgressIndicator(progress = { s.progress!! }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateState.Verifying -> Text("Verifying SHA-256 checksum and signing certificate…", style = MaterialTheme.typography.bodySmall)
                    is UpdateState.Installing, is UpdateState.AwaitingConfirmation -> Text("Waiting for Android to confirm the installation…", style = MaterialTheme.typography.bodySmall)
                    is UpdateState.NeedsInstallPermission -> Text(
                        "The update is downloaded and verified. Android needs your permission for Open Pulse to install updates. " +
                            "Allow it in the next screen, then come back.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Text(
                        "The download (${"%.1f".format(update.apk.size / 1_048_576.0)} MB, Wi-Fi only) is checked against its published SHA-256 checksum " +
                            "and must be signed with the same key as this app before Android asks you to install it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.Available, is UpdateState.Failed -> TextButton(onClick = { vm.downloadUpdate(update) }) {
                    Text(if (s is UpdateState.Failed) "Retry" else "Download & install")
                }
                is UpdateState.NeedsInstallPermission -> TextButton(onClick = { context.startActivity(vm.unknownSourcesIntent()) }) { Text("Open settings") }
                else -> {}
            }
        },
        dismissButton = {
            Row {
                if (s is UpdateState.Available) TextButton(onClick = { vm.skipUpdate(update) }) { Text("Skip version") }
                if (busy || s is UpdateState.NeedsInstallPermission || s is UpdateState.AwaitingConfirmation) {
                    TextButton(onClick = { vm.cancelUpdate() }) { Text("Cancel") }
                } else {
                    TextButton(onClick = { vm.dismissUpdateDialog() }) { Text("Later") }
                }
            }
        },
    )
}
