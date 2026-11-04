package app.openpulse.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

data class AvailableUpdate(
    val version: SemanticVersion,
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val apk: GitHubAsset,
    val checksum: GitHubAsset,
    val htmlUrl: String?,
)

sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Unavailable(val reason: String) : UpdateCheckResult
}

object ReleaseEvaluator {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parseRelease(body: String): GitHubRelease = json.decodeFromString(GitHubRelease.serializer(), body)

    /**
     * Decides whether [release] is a usable update for [installedVersion]. A release must be
     * newer, not a draft/pre-release, served over HTTPS and publish both an APK and its
     * SHA-256 checksum; otherwise it is ignored.
     */
    fun evaluate(release: GitHubRelease, installedVersion: String): UpdateCheckResult {
        if (release.draft || release.prerelease) return UpdateCheckResult.Unavailable("Latest release is not a stable release")
        val latest = SemanticVersion.parse(release.tagName)
            ?: return UpdateCheckResult.Unavailable("Release tag '${release.tagName}' is not a version")
        val installed = SemanticVersion.parse(installedVersion)
            ?: return UpdateCheckResult.Unavailable("Installed version '$installedVersion' is not comparable")
        if (latest <= installed) return UpdateCheckResult.UpToDate
        val apk = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.startsWith("https://") }
            .let { list -> list.firstOrNull { !it.name.contains("debug", ignoreCase = true) } ?: list.firstOrNull() }
            ?: return UpdateCheckResult.Unavailable("Release ${release.tagName} has no APK")
        val checksum = release.assets.firstOrNull { it.name.equals(apk.name + ".sha256", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.equals("SHA256SUMS", true) || it.name.equals("SHA256SUMS.txt", true) || it.name.equals("checksums.txt", true) }
            ?: return UpdateCheckResult.Unavailable("Release ${release.tagName} has no SHA-256 checksum")
        if (!checksum.downloadUrl.startsWith("https://")) return UpdateCheckResult.Unavailable("Checksum is not served over HTTPS")
        return UpdateCheckResult.Available(
            AvailableUpdate(
                version = latest,
                tagName = release.tagName,
                title = release.name?.takeIf { it.isNotBlank() } ?: "Open Pulse ${release.tagName}",
                releaseNotes = release.body.orEmpty().trim(),
                apk = apk,
                checksum = checksum,
                htmlUrl = release.htmlUrl,
            ),
        )
    }
}

object ChecksumParser {
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Extracts the SHA-256 for [fileName] from a checksum file. Supports a bare hash, or
     * `sha256sum` format lines ("<hash>  <name>" / "<hash> *<name>").
     */
    fun find(content: String, fileName: String): String? {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        for (line in lines) {
            val parts = line.split(Regex("\\s+"), limit = 2)
            val hash = parts[0]
            if (!HEX64.matches(hash)) continue
            if (parts.size == 1) { if (lines.size == 1) return hash.lowercase() else continue }
            val name = parts[1].trim().removePrefix("*").substringAfterLast('/')
            if (name == fileName) return hash.lowercase()
        }
        return null
    }

    fun isValidSha256(hex: String): Boolean = HEX64.matches(hex)
}
