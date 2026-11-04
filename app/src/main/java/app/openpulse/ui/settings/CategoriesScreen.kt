@file:OptIn(ExperimentalMaterial3Api::class)

package app.openpulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openpulse.core.model.MeasurementType
import app.openpulse.data.prefs.UserSettings
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.components.InfoNote
import app.openpulse.ui.components.TypeBadge

@Composable
fun CategoriesScreen(vm: OpenPulseViewModel, settings: UserSettings, onBack: () -> Unit) {
    // Dashboard card order: configured order first, then any enabled types not yet placed.
    val cardOrder = (settings.dashboardCards + MeasurementType.entries).distinct().filter { it in settings.enabledTypes }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories & dashboard") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header("Tracked categories", "Disabling a category hides it everywhere. Recorded data is kept.") }
            items(MeasurementType.entries, key = { "t" + it.key }) { t ->
                ListItem(
                    leadingContent = { TypeBadge(t, 32.dp) },
                    headlineContent = { Text(t.title) },
                    supportingContent = { Text(t.description, maxLines = 2) },
                    trailingContent = {
                        Switch(
                            checked = t in settings.enabledTypes,
                            onCheckedChange = { on ->
                                vm.updateSettings {
                                    val enabled = if (on) it.enabledTypes + t else it.enabledTypes - t
                                    it.copy(
                                        enabledTypes = enabled,
                                        dashboardCards = if (on && t !in it.dashboardCards) it.dashboardCards + t else it.dashboardCards,
                                    )
                                }
                            },
                        )
                    },
                )
            }
            item { Header("Dashboard cards", "Choose which vitals appear on the dashboard and in what order.") }
            itemsIndexed(cardOrder, key = { _, t -> "c" + t.key }) { i, t ->
                val shown = t in settings.dashboardCards
                ListItem(
                    leadingContent = { Checkbox(checked = shown, onCheckedChange = { on ->
                        vm.updateSettings { s ->
                            val base = (s.dashboardCards + MeasurementType.entries).distinct().filter { it in s.enabledTypes }
                            s.copy(dashboardCards = if (on) base.filter { it in s.dashboardCards || it == t } else s.dashboardCards - t)
                        }
                    }) },
                    headlineContent = { Text(t.title) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = i > 0, onClick = { vm.updateSettings { it.copy(dashboardCards = move(cardOrder, i, i - 1).filter { c -> c in it.dashboardCards }) } }) {
                                Icon(Icons.Outlined.KeyboardArrowUp, "Move up")
                            }
                            IconButton(enabled = i < cardOrder.lastIndex, onClick = { vm.updateSettings { it.copy(dashboardCards = move(cardOrder, i, i + 1).filter { c -> c in it.dashboardCards }) } }) {
                                Icon(Icons.Outlined.KeyboardArrowDown, "Move down")
                            }
                        }
                    },
                )
            }
            item {
                Header("Daily checklist", "Shows on the dashboard which of these you've recorded today.")
                Toggle("Show daily checklist", null, settings.checklistEnabled) { v -> vm.updateSettings { it.copy(checklistEnabled = v) } }
            }
            items(settings.orderedEnabledTypes, key = { "k" + it.key }) { t ->
                ListItem(
                    headlineContent = { Text(t.title) },
                    trailingContent = {
                        Checkbox(checked = t in settings.checklistTypes, enabled = settings.checklistEnabled, onCheckedChange = { on ->
                            vm.updateSettings { it.copy(checklistTypes = if (on) it.checklistTypes + t else it.checklistTypes - t) }
                        })
                    },
                )
            }
        }
    }
}

private fun move(list: List<MeasurementType>, from: Int, to: Int): List<MeasurementType> {
    val m = list.toMutableList()
    val item = m.removeAt(from)
    m.add(to.coerceIn(0, m.size), item)
    return m
}

@Composable
private fun Header(title: String, text: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        InfoNote(text)
    }
}
