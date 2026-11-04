package app.openpulse.update

import app.openpulse.core.update.GitHubRelease
import app.openpulse.core.update.ReleaseEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Minimal HTTPS client for the GitHub Releases API and asset downloads. */
class GitHubReleaseClient(private val config: UpdateConfig) {
    private val userAgent = "OpenPulse-Android/${config.installedVersion}"

    private fun open(url: String, accept: String): HttpsURLConnection {
        val u = URL(url)
        if (u.protocol != "https") throw UpdateException("Refusing non-HTTPS URL")
        val c = u.openConnection() as? HttpsURLConnection ?: throw UpdateException("Refusing non-HTTPS connection")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true // HttpURLConnection never follows HTTPS -> HTTP redirects
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", userAgent)
        return c
    }

    suspend fun latestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        val c = open(config.latestReleaseUrl, "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            when (val code = c.responseCode) {
                200 -> ReleaseEvaluator.parseRelease(c.inputStream.bufferedReader().use { it.readText() })
                404 -> null // no published releases yet
                403, 429 -> throw UpdateException("GitHub rate limit reached, try again later")
                else -> throw UpdateException("GitHub responded with HTTP $code")
            }
        } catch (e: IOException) {
            throw UpdateException("Network error while checking for updates", e)
        } finally {
            c.disconnect()
        }
    }

    suspend fun downloadText(url: String, maxBytes: Int = 64 * 1024): String = withContext(Dispatchers.IO) {
        val c = open(url, "application/octet-stream")
        try {
            if (c.responseCode != 200) throw UpdateException("Checksum download failed (HTTP ${c.responseCode})")
            if (c.url.protocol != "https") throw UpdateException("Checksum was redirected to an insecure URL")
            val bytes = c.inputStream.use { input ->
                val buf = ByteArray(maxBytes + 1)
                var total = 0
                while (total < buf.size) { val n = input.read(buf, total, buf.size - total); if (n < 0) break; total += n }
                if (total > maxBytes) throw UpdateException("Checksum file is unexpectedly large")
                buf.copyOf(total)
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: IOException) {
            throw UpdateException("Network error while downloading checksum", e)
        } finally { c.disconnect() }
    }

    /** Streams [url] to [dest], returning the lowercase hex SHA-256 of the received bytes. */
    suspend fun downloadFile(url: String, dest: File, maxBytes: Long, onProgress: (Float?) -> Unit): String = withContext(Dispatchers.IO) {
        val c = open(url, "application/octet-stream")
        try {
            if (c.responseCode != 200) throw UpdateException("Download failed (HTTP ${c.responseCode})")
            if (c.url.protocol != "https") throw UpdateException("Download was redirected to an insecure URL")
            val length = c.contentLengthLong
            if (length > maxBytes) throw UpdateException("Update file is unexpectedly large")
            val digest = MessageDigest.getInstance("SHA-256")
            dest.parentFile?.mkdirs()
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw UpdateException("Update file is unexpectedly large")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        onProgress(if (length > 0) (total.toFloat() / length).coerceIn(0f, 1f) else null)
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: IOException) {
            dest.delete()
            throw UpdateException("Network error while downloading the update", e)
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally { c.disconnect() }
    }
}
