package app.openpulse.core.io

import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Reminder
import app.openpulse.core.model.Sex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

@Serializable
data class MeasurementDto(
    val type: String,
    val value: Double,
    val value2: Double? = null,
    val pulse: Double? = null,
    val timestampUtc: String,
    val timeZone: String,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val demo: Boolean = false,
    val createdAtUtc: String? = null,
    val updatedAtUtc: String? = null,
)

@Serializable
data class ProfileDto(
    val dateOfBirth: String? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val initialWeightKg: Double? = null,
    val timeZone: String? = null,
    val goals: String = "",
    val medicalNotes: String = "",
)

@Serializable
data class ReminderDto(
    val title: String,
    val types: List<String>,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: List<Int>,
    val enabled: Boolean = true,
)

@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtUtc: String,
    val appVersion: String,
    /** Canonical unit per channel kind, for human readers of the file. */
    val units: Map<String, String> = emptyMap(),
    val profile: ProfileDto? = null,
    val settings: Map<String, String> = emptyMap(),
    val reminders: List<ReminderDto> = emptyList(),
    val measurements: List<MeasurementDto>,
) {
    companion object {
        const val FORMAT = "open-pulse-backup"
        const val SCHEMA_VERSION = 1
    }
}

data class DecodedBackup(
    val measurements: List<Measurement>,
    val profile: HealthProfile?,
    val reminders: List<Reminder>,
    val settings: Map<String, String>,
    val skipped: List<String>,
)

object BackupCodec {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(
        measurements: List<Measurement>,
        profile: HealthProfile?,
        reminders: List<Reminder>,
        settings: Map<String, String>,
        appVersion: String,
        now: Instant = Instant.now(),
    ): String {
        val doc = BackupDocument(
            exportedAtUtc = now.toString(),
            appVersion = appVersion,
            units = mapOf(
                "blood_pressure" to "mmHg", "heart_rate" to "bpm", "weight" to "kg", "temperature" to "°C",
                "percent" to "%", "length" to "cm", "glucose" to "mg/dL", "respiratory_rate" to "breaths/min",
                "sleep" to "h", "water" to "mL", "steps" to "steps",
            ),
            profile = profile?.toDto(),
            settings = settings,
            reminders = reminders.map { ReminderDto(it.title, it.types.map { t -> t.key }, it.hour, it.minute, it.daysOfWeek.sorted(), it.enabled) },
            measurements = measurements.sortedBy { it.timestamp }.map { it.toDto() },
        )
        return json.encodeToString(BackupDocument.serializer(), doc)
    }

    /** @throws IllegalArgumentException if the text is not an Open Pulse backup. */
    fun decode(text: String): DecodedBackup {
        val doc = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid Open Pulse JSON file", e)
        }
        require(doc.format == BackupDocument.FORMAT) { "Unknown file format '${doc.format}'" }
        require(doc.schemaVersion <= BackupDocument.SCHEMA_VERSION) { "Backup was created by a newer app version" }
        val skipped = mutableListOf<String>()
        val ms = doc.measurements.mapIndexedNotNull { i, d ->
            runCatching { d.toModel() }.onFailure { skipped += "Entry ${i + 1}: ${it.message}" }.getOrNull()
        }
        val reminders = doc.reminders.mapNotNull { r ->
            val types = r.types.mapNotNull { MeasurementType.fromKey(it) }.toSet()
            if (r.hour !in 0..23 || r.minute !in 0..59) null
            else Reminder(0, r.title, types, r.hour, r.minute, r.daysOfWeek.filter { it in 1..7 }.toSet(), r.enabled)
        }
        return DecodedBackup(ms, doc.profile?.toModel(), reminders, doc.settings, skipped)
    }

    fun Measurement.toDto() = MeasurementDto(
        type = type.key, value = value, value2 = secondaryValue, pulse = pulse,
        timestampUtc = Instant.ofEpochMilli(timestamp).toString(), timeZone = zoneId,
        note = note, tags = tags, demo = isDemo,
        createdAtUtc = Instant.ofEpochMilli(createdAt).toString(),
        updatedAtUtc = Instant.ofEpochMilli(updatedAt).toString(),
    )

    fun MeasurementDto.toModel(): Measurement {
        val t = MeasurementType.fromKey(type) ?: error("unknown type '$type'")
        val ts = Instant.parse(timestampUtc).toEpochMilli()
        require(!value.isNaN() && !value.isInfinite()) { "invalid value" }
        return Measurement(
            id = 0, type = t, value = value, secondaryValue = value2, pulse = pulse,
            timestamp = ts, zoneId = timeZone, note = note, tags = tags, isDemo = demo,
            createdAt = createdAtUtc?.let { Instant.parse(it).toEpochMilli() } ?: ts,
            updatedAt = updatedAtUtc?.let { Instant.parse(it).toEpochMilli() } ?: ts,
        )
    }

    fun HealthProfile.toDto() = ProfileDto(
        dateOfBirth = dateOfBirth?.toString(), sex = sex?.name, heightCm = heightCm,
        initialWeightKg = initialWeightKg, timeZone = timeZoneId, goals = goals, medicalNotes = medicalNotes,
    )

    fun ProfileDto.toModel() = HealthProfile(
        dateOfBirth = dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sex = sex?.let { s -> Sex.entries.firstOrNull { it.name == s } },
        heightCm = heightCm, initialWeightKg = initialWeightKg, timeZoneId = timeZone,
        goals = goals, medicalNotes = medicalNotes,
    )

    /** Removes entries already present (same type, instant and values) so re-imports don't duplicate. */
    fun withoutDuplicates(incoming: List<Measurement>, existing: List<Measurement>): List<Measurement> {
        val seen = existing.map { it.dedupeKey() }.toMutableSet()
        return incoming.filter { seen.add(it.dedupeKey()) }
    }
}
