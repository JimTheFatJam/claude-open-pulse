package app.openpulse.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Sex
import app.openpulse.core.model.UnitKind
import app.openpulse.core.time.TimeRange
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.core.units.UnitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "open_pulse_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val units: UnitPreferences = UnitPreferences.METRIC,
    val enabledTypes: Set<MeasurementType> = MeasurementType.defaultEnabled,
    /** Ordered list of dashboard cards. Only enabled types are shown. */
    val dashboardCards: List<MeasurementType> = MeasurementType.entries.filter { it.primaryCategory },
    val connectLines: Boolean = true,
    /** Readings further apart than this are never joined by a line. */
    val connectMaxGapDays: Int = 3,
    val showReferenceBands: Boolean = false,
    val appLock: Boolean = false,
    val secureScreen: Boolean = false,
    val demoMode: Boolean = false,
    val checklistEnabled: Boolean = true,
    val checklistTypes: Set<MeasurementType> = setOf(MeasurementType.BLOOD_PRESSURE, MeasurementType.WEIGHT),
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String? = null,
    val onboardingDone: Boolean = false,
    val dashboardType: MeasurementType? = null,
    val dashboardRange: TimeRange = TimeRange.MONTH,
) {
    val orderedEnabledTypes: List<MeasurementType>
        get() = MeasurementType.entries.filter { it in enabledTypes }
    val visibleDashboardCards: List<MeasurementType>
        get() = dashboardCards.filter { it in enabledTypes }
}

/** Preferences + profile storage backed by Preferences DataStore. */
class SettingsRepository(private val context: Context) {
    private val store get() = context.dataStore

    private object K {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val units = stringPreferencesKey("units") // "KIND=UNIT;KIND=UNIT"
        val enabled = stringPreferencesKey("enabled_types")
        val cards = stringPreferencesKey("dashboard_cards")
        val connect = booleanPreferencesKey("connect_lines")
        val connectGap = intPreferencesKey("connect_gap_days")
        val bands = booleanPreferencesKey("reference_bands")
        val lock = booleanPreferencesKey("app_lock")
        val secure = booleanPreferencesKey("secure_screen")
        val demo = booleanPreferencesKey("demo_mode")
        val checklist = booleanPreferencesKey("checklist_enabled")
        val checklistTypes = stringPreferencesKey("checklist_types")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
        val lastCheck = longPreferencesKey("last_update_check")
        val skipped = stringPreferencesKey("skipped_version")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val dashType = stringPreferencesKey("dashboard_type")
        val dashRange = stringPreferencesKey("dashboard_range")

        val dob = stringPreferencesKey("profile_dob")
        val sex = stringPreferencesKey("profile_sex")
        val height = doublePreferencesKey("profile_height_cm")
        val initialWeight = doublePreferencesKey("profile_initial_weight_kg")
        val zone = stringPreferencesKey("profile_time_zone")
        val goals = stringPreferencesKey("profile_goals")
        val notes = stringPreferencesKey("profile_medical_notes")
    }

    private fun typesFrom(s: String?): List<MeasurementType>? =
        s?.split(',')?.mapNotNull { MeasurementType.fromKey(it.trim()) }

    val settings: Flow<UserSettings> = store.data.map { parse(it) }

    private fun parse(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            themeMode = p[K.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.themeMode,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            units = p[K.units]?.let(::decodeUnits) ?: d.units,
            enabledTypes = typesFrom(p[K.enabled])?.toSet() ?: d.enabledTypes,
            dashboardCards = typesFrom(p[K.cards]) ?: d.dashboardCards,
            connectLines = p[K.connect] ?: d.connectLines,
            connectMaxGapDays = p[K.connectGap] ?: d.connectMaxGapDays,
            showReferenceBands = p[K.bands] ?: d.showReferenceBands,
            appLock = p[K.lock] ?: d.appLock,
            secureScreen = p[K.secure] ?: d.secureScreen,
            demoMode = p[K.demo] ?: d.demoMode,
            checklistEnabled = p[K.checklist] ?: d.checklistEnabled,
            checklistTypes = typesFrom(p[K.checklistTypes])?.toSet() ?: d.checklistTypes,
            autoUpdateCheck = p[K.autoUpdate] ?: d.autoUpdateCheck,
            lastUpdateCheck = p[K.lastCheck] ?: 0,
            skippedVersion = p[K.skipped],
            onboardingDone = p[K.onboarding] ?: false,
            dashboardType = p[K.dashType]?.let { MeasurementType.fromKey(it) },
            dashboardRange = p[K.dashRange]?.let { v -> TimeRange.entries.firstOrNull { it.name == v } } ?: d.dashboardRange,
        )
    }

    val profile: Flow<HealthProfile> = store.data.map { p ->
        HealthProfile(
            dateOfBirth = p[K.dob]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            sex = p[K.sex]?.let { v -> Sex.entries.firstOrNull { it.name == v } },
            heightCm = p[K.height],
            initialWeightKg = p[K.initialWeight],
            timeZoneId = p[K.zone]?.takeIf { z -> runCatching { ZoneId.of(z) }.isSuccess },
            goals = p[K.goals].orEmpty(),
            medicalNotes = p[K.notes].orEmpty(),
        )
    }

    suspend fun current(): UserSettings = settings.first()
    suspend fun currentProfile(): HealthProfile = profile.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { p ->
            val cur = parse(p)
            val n = transform(cur)
            p[K.theme] = n.themeMode.name
            p[K.dynamic] = n.dynamicColor
            p[K.units] = encodeUnits(n.units)
            p[K.enabled] = n.enabledTypes.joinToString(",") { it.key }
            p[K.cards] = n.dashboardCards.distinct().joinToString(",") { it.key }
            p[K.connect] = n.connectLines
            p[K.connectGap] = n.connectMaxGapDays.coerceIn(1, 60)
            p[K.bands] = n.showReferenceBands
            p[K.lock] = n.appLock
            p[K.secure] = n.secureScreen
            p[K.demo] = n.demoMode
            p[K.checklist] = n.checklistEnabled
            p[K.checklistTypes] = n.checklistTypes.joinToString(",") { it.key }
            p[K.autoUpdate] = n.autoUpdateCheck
            p[K.lastCheck] = n.lastUpdateCheck
            if (n.skippedVersion != null) p[K.skipped] = n.skippedVersion else p.remove(K.skipped)
            p[K.onboarding] = n.onboardingDone
            if (n.dashboardType != null) p[K.dashType] = n.dashboardType.key else p.remove(K.dashType)
            p[K.dashRange] = n.dashboardRange.name
        }
    }

    suspend fun updateProfile(profile: HealthProfile) {
        store.edit { p ->
            fun <T> set(key: Preferences.Key<T>, v: T?) { if (v == null) p.remove(key) else p[key] = v }
            set(K.dob, profile.dateOfBirth?.toString())
            set(K.sex, profile.sex?.name)
            set(K.height, profile.heightCm)
            set(K.initialWeight, profile.initialWeightKg)
            set(K.zone, profile.timeZoneId)
            set(K.goals, profile.goals.takeIf { it.isNotBlank() })
            set(K.notes, profile.medicalNotes.takeIf { it.isNotBlank() })
        }
    }

    /** Portable subset of settings included in backups. */
    suspend fun exportableSettings(): Map<String, String> {
        val s = current()
        return mapOf(
            "themeMode" to s.themeMode.name,
            "units" to encodeUnits(s.units),
            "enabledTypes" to s.enabledTypes.joinToString(",") { it.key },
            "dashboardCards" to s.dashboardCards.joinToString(",") { it.key },
            "checklistTypes" to s.checklistTypes.joinToString(",") { it.key },
            "connectLines" to s.connectLines.toString(),
            "connectMaxGapDays" to s.connectMaxGapDays.toString(),
        )
    }

    suspend fun applyImportedSettings(map: Map<String, String>) {
        update { s ->
            s.copy(
                themeMode = map["themeMode"]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: s.themeMode,
                units = map["units"]?.let(::decodeUnits) ?: s.units,
                enabledTypes = typesFrom(map["enabledTypes"])?.toSet()?.takeIf { it.isNotEmpty() } ?: s.enabledTypes,
                dashboardCards = typesFrom(map["dashboardCards"]) ?: s.dashboardCards,
                checklistTypes = typesFrom(map["checklistTypes"])?.toSet() ?: s.checklistTypes,
                connectLines = map["connectLines"]?.toBooleanStrictOrNull() ?: s.connectLines,
                connectMaxGapDays = map["connectMaxGapDays"]?.toIntOrNull() ?: s.connectMaxGapDays,
            )
        }
    }

    suspend fun clearAll() { store.edit { it.clear() } }

    companion object {
        fun encodeUnits(u: UnitPreferences): String = u.selected.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }
        fun decodeUnits(s: String): UnitPreferences = UnitPreferences(
            s.split(';').mapNotNull { pair ->
                val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val kind = UnitKind.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                val unit = MeasurementUnit.fromName(v)?.takeIf { it.kind == kind } ?: return@mapNotNull null
                kind to unit
            }.toMap(),
        )
    }
}

fun HealthProfile.zone(): ZoneId = timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
