import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------- Versioning ----------
val appVersionName: String = (findProperty("openpulse.versionName") as String?)?.removePrefix("v") ?: "1.0.0"
/** major*1_000_000 + minor*10_000 + patch*100 (+99 for stable). Mirrors core SemanticVersion.versionCode(). */
fun versionCodeFor(name: String): Int {
    val m = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?(-.+)?$""").matchEntire(name)
        ?: throw GradleException("openpulse.versionName '$name' is not a semantic version")
    val (maj, min, pat, pre) = m.destructured
    require(min.toInt() < 100 && pat.ifEmpty { "0" }.toInt() < 100) { "minor/patch must be < 100" }
    return maj.toInt() * 1_000_000 + min.toInt() * 10_000 + pat.ifEmpty { "0" }.toInt() * 100 + if (pre.isEmpty()) 99 else 0
}

// ---------- Release signing (never committed) ----------
// Resolution order: environment variables (CI) -> keystore.properties (local, git-ignored).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("OPENPULSE_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("OPENPULSE_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("OPENPULSE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("OPENPULSE_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null } &&
    rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "app.openpulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.openpulse"
        minSdk = 26
        targetSdk = 35
        versionName = appVersionName
        versionCode = versionCodeFor(appVersionName)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val owner = (findProperty("openpulse.updateRepoOwner") as String?).orEmpty()
        val repo = (findProperty("openpulse.updateRepoName") as String?).orEmpty()
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"$owner\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"$repo\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Screenshot tests only run when requested: ./gradlew :app:testDebugUnitTest -Popenpulse.screenshots=true
            it.systemProperty("openpulse.screenshots", (findProperty("openpulse.screenshots") as String?) ?: "false")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

if (!hasReleaseSigning) {
    logger.lifecycle("Open Pulse: release signing not configured - release APKs will be unsigned.")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
