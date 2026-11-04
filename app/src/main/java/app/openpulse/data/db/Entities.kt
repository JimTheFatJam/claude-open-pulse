package app.openpulse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Reminder

@Entity(
    tableName = "measurements",
    indices = [Index(value = ["type", "timestamp"]), Index(value = ["timestamp"]), Index(value = ["isDemo"])],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Double,
    val secondaryValue: Double?,
    val pulse: Double?,
    val timestamp: Long,
    val zoneId: String,
    val note: String,
    /** Tags separated by '\n' (tags themselves never contain line breaks). */
    val tags: String,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Comma-separated measurement type keys. */
    val types: String,
    val hour: Int,
    val minute: Int,
    /** Comma-separated ISO day numbers (1 = Monday). Empty = every day. */
    val daysOfWeek: String,
    val enabled: Boolean,
)

fun MeasurementEntity.toModel(): Measurement? {
    val t = MeasurementType.fromKey(type) ?: return null
    return Measurement(
        id = id, type = t, value = value, secondaryValue = secondaryValue, pulse = pulse,
        timestamp = timestamp, zoneId = zoneId, note = note,
        tags = if (tags.isEmpty()) emptyList() else tags.split('\n'),
        isDemo = isDemo, createdAt = createdAt, updatedAt = updatedAt,
    )
}

fun Measurement.toEntity() = MeasurementEntity(
    id = id, type = type.key, value = value, secondaryValue = secondaryValue, pulse = pulse,
    timestamp = timestamp, zoneId = zoneId, note = note,
    tags = tags.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"),
    isDemo = isDemo, createdAt = createdAt, updatedAt = updatedAt,
)

fun ReminderEntity.toModel() = Reminder(
    id = id, title = title,
    types = types.split(',').mapNotNull { MeasurementType.fromKey(it.trim()) }.toSet(),
    hour = hour, minute = minute,
    daysOfWeek = daysOfWeek.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet(),
    enabled = enabled,
)

fun Reminder.toEntity() = ReminderEntity(
    id = id, title = title, types = types.joinToString(",") { it.key },
    hour = hour, minute = minute, daysOfWeek = daysOfWeek.sorted().joinToString(","), enabled = enabled,
)
