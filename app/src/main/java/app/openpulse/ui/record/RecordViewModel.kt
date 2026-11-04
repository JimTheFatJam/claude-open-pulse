package app.openpulse.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openpulse.AppContainer
import app.openpulse.core.model.Channel
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.units.MeasurementUnit
import app.openpulse.core.units.UnitPreferences
import app.openpulse.core.validation.MeasurementValidator
import app.openpulse.core.validation.ValidationError
import app.openpulse.data.prefs.zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.roundToInt

/** Text the user typed per channel. Feet/inches and hours/minutes use two fields. */
data class ChannelInput(val main: String = "", val sub: String = "")

data class RecordState(
    val loading: Boolean = true,
    val editingId: Long? = null,
    val type: MeasurementType = MeasurementType.BLOOD_PRESSURE,
    val inputs: Map<Channel, ChannelInput> = emptyMap(),
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val zone: ZoneId = ZoneId.systemDefault(),
    val note: String = "",
    val tags: List<String> = emptyList(),
    val units: UnitPreferences = UnitPreferences.METRIC,
    val errors: List<ValidationError> = emptyList(),
    val saved: Boolean = false,
    val isDemo: Boolean = false,
)

class RecordViewModel(
    private val c: AppContainer,
    initialType: MeasurementType?,
    private val editId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = c.settingsRepository.current()
            val zone = c.settingsRepository.currentProfile().zone()
            val existing = editId?.let { c.measurementRepository.get(it) }
            if (existing != null) {
                val z = runCatching { ZoneId.of(existing.zoneId) }.getOrDefault(zone)
                val zdt = java.time.Instant.ofEpochMilli(existing.timestamp).atZone(z)
                _state.value = RecordState(
                    loading = false, editingId = existing.id, type = existing.type,
                    inputs = existing.type.channels.associate { spec ->
                        spec.channel to (existing.valueOf(spec.channel)?.let { toInput(it, unitOf(existing.type, spec.channel, settings.units)) } ?: ChannelInput())
                    },
                    date = zdt.toLocalDate(), time = zdt.toLocalTime().withSecond(0).withNano(0), zone = z,
                    note = existing.note, tags = existing.tags, units = settings.units, isDemo = existing.isDemo,
                )
            } else {
                val now = ZonedDateTime.now(zone)
                val type = initialType ?: settings.dashboardType?.takeIf { it in settings.enabledTypes }
                    ?: settings.orderedEnabledTypes.firstOrNull() ?: MeasurementType.BLOOD_PRESSURE
                _state.value = RecordState(
                    loading = false, type = type, date = now.toLocalDate(),
                    time = now.toLocalTime().withSecond(0).withNano(0), zone = zone, units = settings.units,
                )
            }
        }
    }

    fun unitOf(type: MeasurementType, channel: Channel, prefs: UnitPreferences = _state.value.units): MeasurementUnit =
        prefs.unitFor((type.spec(channel) ?: type.primarySpec).unitKind)

    fun setType(t: MeasurementType) = _state.update { if (it.editingId != null) it else it.copy(type = t, inputs = emptyMap(), errors = emptyList()) }
    fun setInput(ch: Channel, input: ChannelInput) = _state.update { it.copy(inputs = it.inputs + (ch to input), errors = it.errors.filter { e -> e.channel != ch }) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d, errors = it.errors.filter { e -> e.channel != null }) }
    fun setTime(t: LocalTime) = _state.update { it.copy(time = t, errors = it.errors.filter { e -> e.channel != null }) }
    fun setNow() = _state.update { s -> val n = ZonedDateTime.now(s.zone); s.copy(date = n.toLocalDate(), time = n.toLocalTime().withSecond(0).withNano(0)) }
    fun setNote(n: String) = _state.update { it.copy(note = n.take(2000)) }
    fun toggleTag(tag: String) = _state.update { s ->
        val t = tag.trim().take(40)
        if (t.isEmpty()) s else s.copy(tags = if (t in s.tags) s.tags - t else s.tags + t)
    }

    /** Changes the preferred unit for this kind app-wide and converts what was typed. */
    fun changeUnit(channel: Channel, newUnit: MeasurementUnit) {
        val s = _state.value
        val oldUnit = unitOf(s.type, channel)
        if (oldUnit == newUnit) return
        val newPrefs = s.units.with(newUnit)
        val converted = s.type.channels.filter { it.unitKind == newUnit.kind }.associate { spec ->
            val canonical = parse(s.inputs[spec.channel] ?: ChannelInput(), oldUnit)
            spec.channel to (canonical?.let { toInput(it, newUnit) } ?: (s.inputs[spec.channel] ?: ChannelInput()))
        }
        _state.value = s.copy(units = newPrefs, inputs = s.inputs + converted)
        viewModelScope.launch { c.settingsRepository.update { it.copy(units = it.units.with(newUnit)) } }
    }

    fun save() {
        val s = _state.value
        val values = s.type.channels.associate { spec ->
            spec.channel to parse(s.inputs[spec.channel] ?: ChannelInput(), unitOf(s.type, spec.channel))
        }
        val parseErrors = s.type.channels.mapNotNull { spec ->
            val input = s.inputs[spec.channel] ?: ChannelInput()
            if ((input.main.isNotBlank() || input.sub.isNotBlank()) && values[spec.channel] == null) ValidationError(spec.channel, "${spec.label} is not a valid number") else null
        }
        val ts = ZonedDateTime.of(s.date, s.time, s.zone).toInstant().toEpochMilli()
        val errors = parseErrors + MeasurementValidator.validate(s.type, values, ts).filter { e -> parseErrors.none { it.channel == e.channel } }
        if (errors.isNotEmpty()) { _state.value = s.copy(errors = errors); return }
        val m = Measurement(
            id = s.editingId ?: 0, type = s.type,
            value = values[Channel.PRIMARY]!!, secondaryValue = values[Channel.SECONDARY], pulse = values[Channel.PULSE],
            timestamp = ts, zoneId = s.zone.id, note = s.note.trim(), tags = s.tags,
        )
        viewModelScope.launch {
            try {
                if (s.editingId != null) c.measurementRepository.update(m) else c.measurementRepository.add(m)
                _state.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(errors = listOf(ValidationError(null, "Could not save: ${e.message}"))) }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.editingId ?: return
        viewModelScope.launch { c.measurementRepository.delete(id); onDone() }
    }

    companion object {
        /** Canonical value from the typed input, or null if empty/invalid. */
        fun parse(input: ChannelInput, unit: MeasurementUnit): Double? = when (unit) {
            MeasurementUnit.FEET_INCHES -> {
                val ft = if (input.main.isBlank()) 0.0 else MeasurementValidator.parseNumber(input.main)
                val inch = if (input.sub.isBlank()) 0.0 else MeasurementValidator.parseNumber(input.sub)
                if (input.main.isBlank() && input.sub.isBlank() || ft == null || inch == null || inch >= 12 || inch < 0) null
                else unit.toCanonical(ft * 12 + inch)
            }
            MeasurementUnit.HOURS -> {
                val h = if (input.main.isBlank()) 0.0 else MeasurementValidator.parseNumber(input.main)
                val min = if (input.sub.isBlank()) 0.0 else MeasurementValidator.parseNumber(input.sub)
                if (input.main.isBlank() && input.sub.isBlank() || h == null || min == null || min >= 60 || min < 0) null
                else h + min / 60.0
            }
            else -> if (input.main.isBlank()) null else MeasurementValidator.parseNumber(input.main)?.let { unit.toCanonical(it) }
        }

        fun toInput(canonical: Double, unit: MeasurementUnit): ChannelInput = when (unit) {
            MeasurementUnit.FEET_INCHES -> {
                val total = (unit.fromCanonical(canonical) * 2).roundToInt() / 2.0
                val ft = floor(total / 12).toInt()
                val inch = total - ft * 12
                ChannelInput(ft.toString(), if (inch % 1.0 == 0.0) inch.toInt().toString() else inch.toString())
            }
            MeasurementUnit.HOURS -> {
                val totalMin = (canonical * 60).roundToInt()
                ChannelInput((totalMin / 60).toString(), (totalMin % 60).toString())
            }
            else -> {
                val v = unit.fromCanonical(canonical)
                val factor = Math.pow(10.0, unit.decimals.toDouble())
                val r = (v * factor).roundToInt() / factor
                ChannelInput(if (r % 1.0 == 0.0) r.toLong().toString() else r.toString())
            }
        }

        fun factory(c: AppContainer, type: MeasurementType?, id: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordViewModel(c, type, id) }
        }
    }
}
