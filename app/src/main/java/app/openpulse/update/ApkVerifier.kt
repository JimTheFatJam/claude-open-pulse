package app.openpulse.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

sealed interface ApkVerification {
    data object Valid : ApkVerification
    data class Invalid(val reason: String) : ApkVerification
}

/**
 * Ensures a downloaded APK is a legitimate update: same package, higher version code and
 * signed with exactly the same certificate(s) as the installed app. Android's installer
 * performs the same signature check again during installation.
 */
class ApkVerifier(private val context: Context) {

    fun verify(apk: File): ApkVerification {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return ApkVerification.Invalid("The downloaded file is not a valid APK")
        if (archive.packageName != context.packageName) {
            return ApkVerification.Invalid("The APK belongs to a different app (${archive.packageName})")
        }
        val installed = installedInfo(pm)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return ApkVerification.Invalid("The APK is not newer than the installed version")
        }
        val expected = signerDigests(installed)
        val actual = signerDigests(archive)
        if (expected.isEmpty() || actual.isEmpty()) return ApkVerification.Invalid("Could not read signing certificates")
        if (expected != actual) {
            return ApkVerification.Invalid("The APK is not signed with this app's signing key")
        }
        return ApkVerification.Valid
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? = try {
        pm.getPackageArchiveInfo(apk.absolutePath, flags())
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo = pm.getPackageInfo(context.packageName, flags())

    /** SHA-256 digests of the certificates that sign the APK contents (excluding past keys). */
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            info.signingInfo!!.apkContentsSigners
        } else {
            info.signatures
        } ?: return emptySet()
        return certs.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
