package app.openpulse.data.repo

import android.content.Context
import android.net.Uri
import app.openpulse.BuildConfig
import app.openpulse.core.io.BackupCodec
import app.openpulse.core.io.CsvCodec
import app.openpulse.core.model.Measurement
import app.openpulse.core.validation.MeasurementValidator
import app.openpulse.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportSummary(val imported: Int, val duplicates: Int, val invalid: Int, val messages: List<String>)

/**
 * Export / import / backup / restore using files the user picks through the Storage Access
 * Framework. Nothing is uploaded anywhere. Demo data is never exported.
 */
class BackupManager(
    private val context: Context,
    private val measurements: MeasurementRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
) {
    private val maxImportBytes = 50L * 1024 * 1024

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = measurements.allReal()
        write(uri, CsvCodec.encode(data))
        data.size
    }

    /** Full backup: measurements, profile, reminders and portable settings. */
    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = measurements.allReal()
        val text = BackupCodec.encode(
            measurements = data,
            profile = settings.currentProfile(),
            reminders = reminders.all(),
            settings = settings.exportableSettings(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        write(uri, text)
        data.size
    }

    /** Adds records from a CSV or JSON file, skipping exact duplicates and invalid entries. */
    suspend fun importMerge(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = read(uri)
        val (parsed, messages) = parse(text)
        val (valid, invalidMsgs) = validate(parsed)
        val fresh = BackupCodec.withoutDuplicates(valid, measurements.allReal())
        measurements.insertImported(fresh)
        ImportSummary(fresh.size, valid.size - fresh.size, parsed.size - valid.size, messages + invalidMsgs)
    }

    /** Replaces ALL real data, the profile and reminders with the contents of a JSON backup. */
    suspend fun restore(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = read(uri)
        val decoded = BackupCodec.decode(text)
        val (valid, invalidMsgs) = validate(decoded.measurements)
        val unique = BackupCodec.withoutDuplicates(valid, emptyList())
        measurements.replaceAllReal(unique)
        decoded.profile?.let { settings.updateProfile(it) }
        reminders.replaceAll(decoded.reminders)
        settings.applyImportedSettings(decoded.settings)
        ImportSummary(unique.size, valid.size - unique.size, decoded.measurements.size - valid.size, decoded.skipped + invalidMsgs)
    }

    private fun parse(text: String): Pair<List<Measurement>, List<String>> {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return if (trimmed.startsWith("{")) {
            val d = BackupCodec.decode(trimmed)
            d.measurements to d.skipped
        } else {
            val r = CsvCodec.decode(trimmed)
            if (r.measurements.isEmpty() && r.errors.isNotEmpty() && r.errors.first().startsWith("Missing columns")) {
                throw IllegalArgumentException(r.errors.first())
            }
            r.measurements to r.errors
        }
    }

    private fun validate(list: List<Measurement>): Pair<List<Measurement>, List<String>> {
        val msgs = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val ok = list.filter { m ->
            val errs = MeasurementValidator.validate(
                m.type, m.type.channels.associate { it.channel to m.valueOf(it.channel) }, m.timestamp, now,
            )
            if (errs.isNotEmpty()) msgs += "${m.type.title} at ${java.time.Instant.ofEpochMilli(m.timestamp)}: ${errs.first().message}"
            errs.isEmpty()
        }
        return ok to msgs.take(50)
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot write to the selected file")
    }

    private fun read(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected file")
        return input.use { stream ->
            val bytes = stream.readNBytesCompat(maxImportBytes + 1)
            require(bytes.size <= maxImportBytes) { "File is too large" }
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            if (total >= limit) break
        }
        return out.toByteArray()
    }
}
