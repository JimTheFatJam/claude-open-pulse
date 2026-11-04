@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.openpulse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.openpulse.core.model.MeasurementType
import app.openpulse.ui.navigation.OpenPulseNavHost
import app.openpulse.ui.navigation.Routes
import app.openpulse.ui.settings.OnboardingDialog
import app.openpulse.ui.settings.UpdateDialog

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard),
    TopLevel(Routes.MEASUREMENTS, "Measurements", Icons.AutoMirrored.Outlined.ShowChart),
    TopLevel(Routes.STATISTICS, "Statistics", Icons.Outlined.Insights),
    TopLevel(Routes.HISTORY, "History", Icons.Outlined.History),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun OpenPulseRoot(
    viewModel: OpenPulseViewModel,
    isLocked: Boolean,
    lockError: String?,
    onUnlock: () -> Unit,
    pendingRecordType: String?,
    onPendingRecordConsumed: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    if (isLocked) { LockScreen(lockError, onUnlock); return }
    val s = settings ?: return // first frame while preferences load

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(pendingRecordType) {
        val t = pendingRecordType?.let { MeasurementType.fromKey(it) }
        if (pendingRecordType != null) { nav.navigate(Routes.record(t)); onPendingRecordConsumed() }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val isTopLevel = topLevel.any { t -> current?.hierarchy?.any { it.route == t.route } == true }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide && isTopLevel) {
                NavigationBar {
                    topLevel.forEach { t ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == t.route } == true,
                            onClick = { go(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (wide && isTopLevel) {
                NavigationRail(header = { Spacer(Modifier.height(8.dp)) }) {
                    Spacer(Modifier.weight(1f))
                    topLevel.forEach { t ->
                        NavigationRailItem(
                            selected = current?.hierarchy?.any { it.route == t.route } == true,
                            onClick = { go(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(Modifier.weight(1f)) {
                OpenPulseNavHost(nav, viewModel, s)
            }
        }
    }

    if (!s.onboardingDone) OnboardingDialog(viewModel)
    UpdateDialog(viewModel)
}

@Composable
private fun LockScreen(error: String?, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Open Pulse is locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Authenticate with your fingerprint, face or device screen lock to view your health data.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
