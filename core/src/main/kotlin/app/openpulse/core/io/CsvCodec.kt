package app.openpulse.core.io

import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.units.MeasurementUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RFC 4180 CSV export/import of measurements. Values are written in canonical units, with the
 * unit symbol in a dedicated column, so files are unambiguous and round-trip losslessly.
 */
object CsvCodec {
    val HEADER = listOf(
        "id", "type", "local_date_time", "timestamp_utc", "time_zone",
        "value", "value2", "pulse", "unit", "note", "tags", "demo",
    )
    private val localFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun encode(measurements: List<Measurement>): String = buildString {
        appendLine(HEADER.joinToString(","))
        for (m in measurements.sortedBy { it.timestamp }) {
            val zone = runCatching { ZoneId.of(m.zoneId) }.getOrDefault(ZoneId.of("UTC"))
            val unit = MeasurementUnit.canonicalFor(m.type.primarySpec.unitKind)
            val row = listOf(
                m.id.toString(),
                m.type.key,
                localFmt.format(Instant.ofEpochMilli(m.timestamp).atZone(zone)),
                Instant.ofEpochMilli(m.timestamp).toString(),
                m.zoneId,
                m.value.toString(),
                m.secondaryValue?.toString() ?: "",
                m.pulse?.toString() ?: "",
                unit.symbol,
                m.note,
                m.tags.joinToString(";"),
                m.isDemo.toString(),
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || field.startsWith(" ") || field.endsWith(" ")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field

    /** Parses CSV text into rows of fields, honouring quotes, escaped quotes and embedded newlines. */
    fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    data class ImportResult(val measurements: List<Measurement>, val errors: List<String>)

    fun decode(text: String): ImportResult {
        val rows = parseRows(text)
        if (rows.isEmpty()) return ImportResult(emptyList(), listOf("File is empty"))
        val header = rows.first().map { it.trim().lowercase() }
        val idx = HEADER.associateWith { header.indexOf(it) }
        val required = listOf("type", "timestamp_utc", "value")
        val missing = required.filter { idx[it] == -1 }
        if (missing.isNotEmpty()) return ImportResult(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))
        val out = mutableListOf<Measurement>()
        val errors = mutableListOf<String>()
        rows.drop(1).forEachIndexed { n, r ->
            val line = n + 2
            fun get(col: String): String? = idx[col]?.takeIf { it >= 0 }?.let { r.getOrNull(it) }?.trim()
            try {
                val type = MeasurementType.fromKey(get("type").orEmpty()) ?: error("unknown type '${get("type")}'")
                val ts = Instant.parse(get("timestamp_utc")).toEpochMilli()
                val zone = get("time_zone").takeUnless { it.isNullOrBlank() }?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: "UTC"
                val unitSymbol = get("unit")
                val canonical = MeasurementUnit.canonicalFor(type.primarySpec.unitKind)
                if (!unitSymbol.isNullOrBlank() && unitSymbol != canonical.symbol) error("unexpected unit '$unitSymbol' (expected ${canonical.symbol})")
                out += Measurement(
                    id = 0,
                    type = type,
                    value = get("value")!!.toDouble(),
                    secondaryValue = get("value2").takeUnless { it.isNullOrBlank() }?.toDouble(),
                    pulse = get("pulse").takeUnless { it.isNullOrBlank() }?.toDouble(),
                    timestamp = ts,
                    zoneId = zone,
                    note = get("note").orEmpty(),
                    tags = get("tags").orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() },
                    isDemo = get("demo")?.toBooleanStrictOrNull() ?: false,
                    createdAt = ts,
                    updatedAt = ts,
                )
            } catch (e: Exception) {
                errors += "Line $line: ${e.message ?: "invalid row"}"
            }
        }
        return ImportResult(out, errors)
    }
}
