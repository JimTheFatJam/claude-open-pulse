package app.openpulse.core

import app.openpulse.core.demo.DemoDataGenerator
import app.openpulse.core.io.BackupCodec
import app.openpulse.core.io.CsvCodec
import app.openpulse.core.model.HealthProfile
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Reminder
import app.openpulse.core.model.Sex
import app.openpulse.core.update.ChecksumParser
import app.openpulse.core.update.GitHubAsset
import app.openpulse.core.update.GitHubRelease
import app.openpulse.core.update.ReleaseEvaluator
import app.openpulse.core.update.SemanticVersion
import app.openpulse.core.update.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IoAndUpdateTest {
    private val samples = listOf(
        Measurement(id = 1, type = MeasurementType.BLOOD_PRESSURE, value = 121.0, secondaryValue = 79.0, pulse = 61.0, timestamp = 1_760_000_000_000, zoneId = "Europe/Berlin", note = "after coffee, \"quick\"\nsecond line", tags = listOf("Morning", "Seated")),
        Measurement(id = 2, type = MeasurementType.WEIGHT, value = 80.25, timestamp = 1_760_000_000_000, zoneId = "UTC"),
        Measurement(id = 3, type = MeasurementType.WEIGHT, value = 80.5, timestamp = 1_760_000_100_000, zoneId = "UTC", isDemo = true),
    )

    private fun same(a: Measurement, b: Measurement) =
        a.type == b.type && a.value == b.value && a.secondaryValue == b.secondaryValue && a.pulse == b.pulse &&
            a.timestamp == b.timestamp && a.zoneId == b.zoneId && a.note == b.note && a.tags == b.tags && a.isDemo == b.isDemo

    @Test fun csvRoundTrip() {
        val csv = CsvCodec.encode(samples)
        val result = CsvCodec.decode(csv)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(3, result.measurements.size)
        samples.zip(result.measurements).forEach { (a, b) -> assertTrue("$a != $b", same(a, b)) }
    }

    @Test fun csvReportsBadRows() {
        val csv = CsvCodec.HEADER.joinToString(",") + "\nx,unknown,,2026-01-01T00:00:00Z,UTC,1,,,,,,false\n" +
            "x,weight,,not-a-date,UTC,1,,,kg,,,false\n"
        val r = CsvCodec.decode(csv)
        assertEquals(0, r.measurements.size)
        assertEquals(2, r.errors.size)
    }

    @Test fun jsonBackupRoundTripAndDedupe() {
        val profile = HealthProfile(LocalDate.of(1990, 5, 1), Sex.FEMALE, 170.0, 65.0, "Europe/Berlin", "Walk daily", "None")
        val reminder = Reminder(title = "Morning BP", types = setOf(MeasurementType.BLOOD_PRESSURE), hour = 7, minute = 30, daysOfWeek = setOf(1, 3, 5))
        val text = BackupCodec.encode(samples, profile, listOf(reminder), mapOf("theme" to "dark"), "1.0.0")
        val decoded = BackupCodec.decode(text)
        assertEquals(profile, decoded.profile)
        assertEquals(1, decoded.reminders.size)
        assertEquals(setOf(1, 3, 5), decoded.reminders[0].daysOfWeek)
        assertEquals("dark", decoded.settings["theme"])
        samples.zip(decoded.measurements).forEach { (a, b) -> assertTrue(same(a, b)) }
        assertTrue(BackupCodec.withoutDuplicates(decoded.measurements, samples).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignJson() { BackupCodec.decode("""{"hello":1}""") }

    @Test fun semverOrdering() {
        val v = { s: String -> SemanticVersion.parse(s)!! }
        assertTrue(v("v1.2.10") > v("1.2.9"))
        assertTrue(v("1.3.0") > v("1.2.99"))
        assertTrue(v("2.0.0") > v("2.0.0-rc.1"))
        assertTrue(v("2.0.0-rc.2") > v("2.0.0-rc.1"))
        assertTrue(v("2.0.0-beta") < v("2.0.0-rc"))
        assertEquals(0, v("1.0").compareTo(v("1.0.0")))
        assertNull(SemanticVersion.parse("latest"))
        assertTrue(v("1.2.3").versionCode() > v("1.2.3-rc.1").versionCode())
        assertTrue(v("1.3.0").versionCode() > v("1.2.9").versionCode())
    }

    private fun release(tag: String, assets: List<GitHubAsset>, pre: Boolean = false) = GitHubRelease(tagName = tag, body = "Notes", prerelease = pre, assets = assets)
    private val apk = GitHubAsset("OpenPulse-v1.1.0.apk", "https://github.com/o/r/releases/download/v1.1.0/OpenPulse-v1.1.0.apk")
    private val sum = GitHubAsset("OpenPulse-v1.1.0.apk.sha256", "https://github.com/o/r/releases/download/v1.1.0/OpenPulse-v1.1.0.apk.sha256")

    @Test fun releaseEvaluation() {
        assertTrue(ReleaseEvaluator.evaluate(release("v1.1.0", listOf(apk, sum)), "1.0.0") is UpdateCheckResult.Available)
        assertEquals(UpdateCheckResult.UpToDate, ReleaseEvaluator.evaluate(release("v1.0.0", listOf(apk, sum)), "1.0.0"))
        assertEquals(UpdateCheckResult.UpToDate, ReleaseEvaluator.evaluate(release("v0.9.0", listOf(apk, sum)), "1.0.0"))
        assertTrue(ReleaseEvaluator.evaluate(release("v1.1.0", listOf(apk)), "1.0.0") is UpdateCheckResult.Unavailable)
        assertTrue(ReleaseEvaluator.evaluate(release("v1.1.0", listOf(apk, sum), pre = true), "1.0.0") is UpdateCheckResult.Unavailable)
        val http = apk.copy(downloadUrl = "http://example.com/a.apk")
        assertTrue(ReleaseEvaluator.evaluate(release("v1.1.0", listOf(http, sum)), "1.0.0") is UpdateCheckResult.Unavailable)
    }

    @Test fun parsesGitHubJson() {
        val json = """{"tag_name":"v1.1.0","name":"Open Pulse 1.1.0","body":"Fixes","draft":false,"prerelease":false,
            "assets":[{"name":"OpenPulse-v1.1.0.apk","browser_download_url":"https://x/a.apk","size":10,"extra":1}],"unknown":true}"""
        val r = ReleaseEvaluator.parseRelease(json)
        assertEquals("v1.1.0", r.tagName)
        assertEquals(1, r.assets.size)
    }

    @Test fun checksumParsing() {
        val h = "a".repeat(64)
        assertEquals(h, ChecksumParser.find(h, "x.apk"))
        assertEquals(h, ChecksumParser.find("$h  x.apk\n", "x.apk"))
        assertEquals(h, ChecksumParser.find("${"b".repeat(64)}  y.apk\n$h *dir/x.apk", "x.apk"))
        assertNull(ChecksumParser.find("$h  y.apk", "x.apk"))
        assertNull(ChecksumParser.find("nothex  x.apk", "x.apk"))
        assertFalse(ChecksumParser.isValidSha256("abc"))
    }

    @Test fun demoDataIsFlaggedAndHasGaps() {
        val zone = ZoneId.of("UTC")
        val data = DemoDataGenerator.generate(zone, LocalDate.of(2026, 6, 1))
        assertTrue(data.isNotEmpty())
        assertTrue(data.all { it.isDemo })
        val gapDays = data.map { java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .filter { !it.isBefore(LocalDate.of(2026, 4, 22)) && !it.isAfter(LocalDate.of(2026, 4, 29)) }
        assertTrue(gapDays.isEmpty())
    }
}
