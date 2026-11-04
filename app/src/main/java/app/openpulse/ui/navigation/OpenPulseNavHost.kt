package app.openpulse.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.openpulse.core.model.MeasurementType
import app.openpulse.data.prefs.UserSettings
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.dashboard.DashboardScreen
import app.openpulse.ui.history.HistoryScreen
import app.openpulse.ui.measurements.MeasurementDetailScreen
import app.openpulse.ui.measurements.MeasurementsScreen
import app.openpulse.ui.record.RecordScreen
import app.openpulse.ui.settings.CategoriesScreen
import app.openpulse.ui.settings.ProfileScreen
import app.openpulse.ui.settings.RemindersScreen
import app.openpulse.ui.settings.SettingsScreen
import app.openpulse.ui.statistics.StatisticsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val MEASUREMENTS = "measurements"
    const val STATISTICS = "statistics"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val REMINDERS = "reminders"
    const val CATEGORIES = "categories"
    const val TYPE = "type/{key}"
    const val RECORD = "record?type={type}&id={id}"

    fun type(t: MeasurementType) = "type/${t.key}"
    fun record(t: MeasurementType? = null) = "record?type=${t?.key.orEmpty()}&id=-1"
    fun edit(id: Long) = "record?type=&id=$id"
}

@Composable
fun OpenPulseNavHost(nav: NavHostController, vm: OpenPulseViewModel, settings: UserSettings) {
    NavHost(
        navController = nav,
        startDestination = Routes.DASHBOARD,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        val record: (MeasurementType?) -> Unit = { nav.navigate(Routes.record(it)) }
        val edit: (Long) -> Unit = { nav.navigate(Routes.edit(it)) }
        val back: () -> Unit = { nav.popBackStack() }

        composable(Routes.DASHBOARD) {
            DashboardScreen(vm, settings, onRecord = record, onEdit = edit, onOpenType = { nav.navigate(Routes.type(it)) })
        }
        composable(Routes.MEASUREMENTS) {
            MeasurementsScreen(vm, settings, onOpenType = { nav.navigate(Routes.type(it)) }, onRecord = record,
                onManageCategories = { nav.navigate(Routes.CATEGORIES) })
        }
        composable(Routes.STATISTICS) { StatisticsScreen(vm, settings, onOpenProfile = { nav.navigate(Routes.PROFILE) }) }
        composable(Routes.HISTORY) { HistoryScreen(vm, settings, onEdit = edit, onRecord = record) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm, settings,
                onProfile = { nav.navigate(Routes.PROFILE) },
                onReminders = { nav.navigate(Routes.REMINDERS) },
                onCategories = { nav.navigate(Routes.CATEGORIES) },
            )
        }
        composable(Routes.PROFILE) { ProfileScreen(vm, settings, onBack = back) }
        composable(Routes.REMINDERS) { RemindersScreen(vm, settings, onBack = back) }
        composable(Routes.CATEGORIES) { CategoriesScreen(vm, settings, onBack = back) }
        composable(Routes.TYPE, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            val type = MeasurementType.fromKey(entry.arguments?.getString("key").orEmpty()) ?: MeasurementType.BLOOD_PRESSURE
            MeasurementDetailScreen(vm, settings, type, onBack = back, onRecord = { record(type) }, onEdit = edit)
        }
        composable(
            Routes.RECORD,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val type = MeasurementType.fromKey(entry.arguments?.getString("type").orEmpty())
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            RecordScreen(type, id, settings, onDone = back)
        }
    }
}
