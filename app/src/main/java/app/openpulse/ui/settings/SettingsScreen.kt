@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openpulse.BuildConfig
import app.openpulse.core.health.ReferenceRanges
import app.openpulse.core.model.UnitKind
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.data.prefs.ThemeMode
import app.openpulse.data.prefs.UserSettings
import app.openpulse.security.AppLock
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.InfoNote
import app.openpulse.update.UpdateState
import java.time.LocalDate
import kotlin.math.roundToInt

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
    return null
}

@Composable
fun SettingsScreen(
    vm: OpenPulseViewModel,
    settings: UserSettings,
    onProfile: () -> Unit,
    onReminders: () -> Unit,
    onCategories: () -> Unit,
) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    val stamp = LocalDate.now().toString()

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importFile) }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restoreBackup) }
    val importTypes = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/json", "application/octet-stream", "*/*")

    Scaffold(topBar = { TopAppBar(title = { Text("Profile & Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Group("Health profile")
                SettingRow(Icons.Outlined.Person, "Health profile", profileSummary(profile), onClick = onProfile)

                Group("Measurements")
                SettingRow(Icons.Outlined.Tune, "Categories, dashboard & checklist", "${settings.enabledTypes.size} categories tracked", onClick = onCategories)
                SettingRow(Icons.Outlined.Alarm, "Reminders", if (reminders.isEmpty()) "No reminders" else "${reminders.count { it.enabled }} active", onClick = onReminders)

                Group("Units")
                MeasurementUnit.configurableKinds.forEach { kind ->
                    UnitRow(kind, settings.units.unitFor(kind)) { u -> vm.updateSettings { it.copy(units = it.units.with(u)) } }
                }

                Group("Charts")
                Toggle("Connect consecutive readings", "Thin dashed lines join real readings that are close together. Dots are always the recorded values.", settings.connectLines) { v ->
                    vm.updateSettings { it.copy(connectLines = v) }
                }
                if (settings.connectLines) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Only connect readings up to ${settings.connectMaxGapDays} day(s) apart", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = settings.connectMaxGapDays.toFloat(), valueRange = 1f..14f, steps = 12,
                            onValueChange = { v -> vm.updateSettings { it.copy(connectMaxGapDays = v.roundToInt()) } },
                        )
                    }
                }
                Toggle("Show general reference bands", "Shade general adult reference ranges on charts where one exists.", settings.showReferenceBands) { v ->
                    vm.updateSettings { it.copy(showReferenceBands = v) }
                }

                Group("Appearance")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.themeMode == m,
                                onClick = { vm.updateSettings { it.copy(themeMode = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Toggle("Use wallpaper colours", "Material You dynamic colour instead of the Open Pulse palette.", settings.dynamicColor) { v ->
                        vm.updateSettings { it.copy(dynamicColor = v) }
                    }
                }

                Group("Privacy & security")
                Toggle(
                    "App lock",
                    if (AppLock.isAvailable(context)) "Require fingerprint, face or device PIN to open Open Pulse." else "Set up a screen lock on this device to use app lock.",
                    settings.appLock,
                    enabled = settings.appLock || AppLock.isAvailable(context),
                ) { v ->
                    val activity = context.findActivity()
                    if (activity != null) {
                        AppLock.authenticate(
                            activity, title = if (v) "Enable app lock" else "Disable app lock",
                            onSuccess = { vm.updateSettings { it.copy(appLock = v) } },
                            onError = { vm.message(it) },
                        )
                    }
                }
                Toggle("Hide content in app switcher", "Blocks screenshots and hides Open Pulse in the recent apps preview.", settings.secureScreen) { v ->
                    vm.updateSettings { it.copy(secureScreen = v) }
                }
                Toggle("Automatic update checks", "Check GitHub for new versions when the app opens – on Wi-Fi only.", settings.autoUpdateCheck) { v ->
                    vm.updateSettings { it.copy(autoUpdateCheck = v) }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    InfoNote(
                        "Your data stays on this device. No account, cloud sync, ads or analytics. Android cloud backup is disabled " +
                            "for this app; use the export and backup options below to keep your own copies. " +
                            "The only network access is the optional update check (Wi-Fi only).",
                    )
                }

                Group("Your data")
                SettingRow(Icons.Outlined.Download, "Export as CSV", "Spreadsheet-friendly list of all measurements") { exportCsv.launch("open-pulse-$stamp.csv") }
                SettingRow(Icons.Outlined.Backup, "Create backup (JSON)", "Measurements, profile, reminders and settings") { exportJson.launch("open-pulse-backup-$stamp.json") }
                SettingRow(Icons.Outlined.Upload, "Import CSV or JSON", "Adds entries; exact duplicates are skipped") { importFile.launch(importTypes) }
                SettingRow(Icons.Outlined.Restore, "Restore from backup", "Replaces all current data with a backup") { confirm = ConfirmAction.Restore }
                Toggle("Demo mode", "Explore Open Pulse with generated sample data. Your real data is hidden, not changed, while demo mode is on.", settings.demoMode) { v ->
                    if (v) confirm = ConfirmAction.DemoOn else vm.setDemoMode(false)
                }
                SettingRow(Icons.Outlined.DeleteForever, "Delete all measurements", "Profile and settings are kept", destructive = true) { confirm = ConfirmAction.DeleteMeasurements }
                SettingRow(Icons.Outlined.DeleteForever, "Erase everything", "Measurements, profile, reminders and settings", destructive = true) { confirm = ConfirmAction.Reset }

                Group("Updates")
                SettingRow(
                    Icons.Outlined.SystemUpdate, "Check for updates",
                    updateStatusText(update) ?: "Installed version ${BuildConfig.VERSION_NAME}",
                ) {
                    val s = update
                    if (s is UpdateState.Available || s is UpdateState.Failed && s.update != null) vm.showUpdateDialog() else vm.checkForUpdates()
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    InfoNote(
                        "Updates come from ${vm.updateConfig.repoUrl}. Each download is verified against its published SHA-256 checksum " +
                            "and must be signed with the same key as this installed app; Android asks you to confirm every install.",
                    )
                }

                Group("About")
                SettingRow(Icons.Outlined.Info, "Open Pulse ${BuildConfig.VERSION_NAME}", "Private, offline health journal")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Open Pulse is a personal tracking and information tool. It does not diagnose conditions or recommend treatment. " +
                            "If you are worried about a reading, contact a healthcare professional. In an emergency, call your local emergency number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ReferenceRanges.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    when (val c = confirm) {
        null -> Unit
        ConfirmAction.Restore -> ConfirmDialog(
            "Restore from backup?",
            "All current measurements, your profile and reminders will be replaced by the contents of the backup file. Consider creating a backup first.",
            "Choose file", onDismiss = { confirm = null },
        ) { restoreFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        ConfirmAction.DemoOn -> ConfirmDialog(
            "Turn on demo mode?",
            "Sample data will be generated and shown instead of your measurements. It is clearly labelled and kept separate, and is deleted when you turn demo mode off. Your real data is not affected.",
            "Turn on", onDismiss = { confirm = null },
        ) { vm.setDemoMode(true) }
        ConfirmAction.DeleteMeasurements -> ConfirmDialog(
            "Delete all measurements?",
            "Every recorded measurement will be permanently deleted from this device. This cannot be undone.",
            "Delete", destructive = true, onDismiss = { confirm = null },
        ) { vm.deleteAllMeasurements() }
        ConfirmAction.Reset -> TypedConfirmDialog(onDismiss = { confirm = null }) { vm.resetEverything() }
    }
}

private enum class ConfirmAction { Restore, DemoOn, DeleteMeasurements, Reset }

private fun profileSummary(p: app.openpulse.core.model.HealthProfile): String {
    val parts = mutableListOf<String>()
    p.dateOfBirth?.let { dob -> app.openpulse.core.health.BodyMetrics.ageYears(dob, LocalDate.now())?.let { parts += "$it years" } }
    if (p.heightCm != null) parts += "height set"
    if (p.timeZoneId != null) parts += p.timeZoneId!!
    return if (parts.isEmpty()) "Date of birth, height, goals, notes" else parts.joinToString(" · ")
}

fun updateStatusText(s: UpdateState): String? = when (s) {
    UpdateState.Idle -> null
    UpdateState.Checking -> "Checking…"
    UpdateState.UpToDate -> "You have the latest version (${BuildConfig.VERSION_NAME})"
    UpdateState.NotOnWifi -> "Connect to Wi-Fi to check for updates"
    is UpdateState.Info -> s.message
    is UpdateState.Available -> "Version ${s.update.version} is available – tap to review"
    is UpdateState.Downloading -> "Downloading ${s.update.version}…"
    is UpdateState.Verifying -> "Verifying download…"
    is UpdateState.NeedsInstallPermission -> "Allow installs from Open Pulse to continue"
    is UpdateState.Installing, is UpdateState.AwaitingConfirmation -> "Installing…"
    is UpdateState.Failed -> s.message
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        leadingContent = { Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = if (onClick != null && !destructive) ({ Icon(Icons.Outlined.ChevronRight, null) }) else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun Toggle(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun UnitRow(kind: UnitKind, current: MeasurementUnit, onSelect: (MeasurementUnit) -> Unit) {
    val options = MeasurementUnit.optionsFor(kind)
    val label = when (kind) {
        UnitKind.MASS -> "Weight"
        UnitKind.TEMPERATURE -> "Temperature"
        UnitKind.HEIGHT -> "Height"
        UnitKind.CIRCUMFERENCE -> "Waist"
        UnitKind.GLUCOSE -> "Blood glucose"
        UnitKind.VOLUME -> "Water"
        else -> kind.name.lowercase()
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 220.dp)) {
            options.forEachIndexed { i, u ->
                SegmentedButton(selected = u == current, onClick = { onSelect(u) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                    Text(if (u == MeasurementUnit.FEET_INCHES) "ft/in" else u.symbol)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TypedConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erase everything?") },
        text = {
            Column {
                Text("All measurements, your profile, reminders and settings will be permanently erased. Type DELETE to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }, enabled = text.trim() == "DELETE") {
                Text("Erase", color = if (text.trim() == "DELETE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
