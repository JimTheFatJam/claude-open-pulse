package app.openpulse.core.update

/** Semantic version (major.minor.patch[-prerelease][+build]); a leading 'v' is accepted. */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1 // release > pre-release
        if (other.preRelease.isEmpty()) return -1
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]; val b = other.preRelease[i]
            val an = a.toIntOrNull(); val bn = b.toIntOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    /** Monotonic Android versionCode derived from the version (pre-releases sort below releases). */
    fun versionCode(): Int = major * 1_000_000 + minor * 10_000 + patch * 100 + if (isPreRelease) 0 else 99

    companion object {
        private val REGEX = Regex("""^[vV]?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(text: String): SemanticVersion? {
            val m = REGEX.matchEntire(text.trim()) ?: return null
            val (maj, min, pat, pre) = m.destructured
            return SemanticVersion(
                maj.toIntOrNull() ?: return null,
                min.toIntOrNull() ?: return null,
                pat.ifEmpty { "0" }.toIntOrNull() ?: return null,
                if (pre.isEmpty()) emptyList() else pre.split('.'),
            )
        }
    }
}
