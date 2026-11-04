package app.openpulse.update

import app.openpulse.BuildConfig

/**
 * Where the updater looks for releases. Configured at build time through the Gradle properties
 * `openpulse.updateRepoOwner` / `openpulse.updateRepoName` (see gradle.properties), so forks can
 * point the updater at their own repository without code changes.
 */
data class UpdateConfig(
    val owner: String = BuildConfig.UPDATE_REPO_OWNER,
    val repo: String = BuildConfig.UPDATE_REPO_NAME,
    val installedVersion: String = BuildConfig.VERSION_NAME.removeSuffix("-debug"),
    /** Upper bound for an APK download, protects storage against bogus assets. */
    val maxApkBytes: Long = 150L * 1024 * 1024,
) {
    val isConfigured: Boolean get() = owner.isNotBlank() && repo.isNotBlank()
    val latestReleaseUrl: String get() = "https://api.github.com/repos/$owner/$repo/releases/latest"
    val repoUrl: String get() = "https://github.com/$owner/$repo"
}
