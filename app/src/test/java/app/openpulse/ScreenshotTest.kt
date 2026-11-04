package app.openpulse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openpulse.core.demo.DemoDataGenerator
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.time.TimeRange
import app.openpulse.data.prefs.ThemeMode
import app.openpulse.ui.OpenPulseRoot
import app.openpulse.ui.OpenPulseViewModel
import app.openpulse.ui.theme.OpenPulseTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the real UI (with demo data) to PNG files in app/build/screenshots.
 * Opt-in: ./gradlew :app:testDebugUnitTest -Popenpulse.screenshots=true --tests '*ScreenshotTest*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = OpenPulseApp::class, qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun onlyWhenRequested() { assumeTrue(System.getProperty("openpulse.screenshots") == "true") }

    private fun prepare(type: MeasurementType = MeasurementType.BLOOD_PRESSURE): OpenPulseViewModel {
        val app = ApplicationProvider.getApplicationContext<OpenPulseApp>()
        val c = app.container
        runBlocking {
            c.settingsRepository.update {
                it.copy(
                    onboardingDone = true, demoMode = true, dashboardType = type, dashboardRange = TimeRange.MONTH,
                    enabledTypes = MeasurementType.defaultEnabled + MeasurementType.SLEEP + MeasurementType.WATER,
                    dashboardCards = MeasurementType.entries.filter { it.primaryCategory } + MeasurementType.SLEEP + MeasurementType.WATER,
                )
            }
            c.measurementRepository.insertDemo(DemoDataGenerator.generate(ZoneId.systemDefault(), LocalDate.now()))
        }
        return OpenPulseViewModel(c)
    }

    private fun render(vm: OpenPulseViewModel, theme: ThemeMode) {
        compose.setContent {
            OpenPulseTheme(themeMode = theme) { OpenPulseRoot(vm, false, null, {}, null, {}) }
        }
        pump { vm.measurements.value?.isNotEmpty() == true && vm.settings.value != null }
    }

    /** Runs the main looper and lets background (Room/DataStore) threads deliver results. */
    private fun pump(maxMs: Long = 20_000, ready: () -> Boolean) {
        val end = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (ready()) break
            Thread.sleep(100)
        }
        repeat(5) {
            Thread.sleep(100)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
    }

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tab(label: String) {
        compose.onAllNodesWithText(label)[0].performClick()
        pump(1_000) { false }
    }

    @Test fun lightScreens() {
        val vm = prepare()
        render(vm, ThemeMode.LIGHT)
        save("01-dashboard-light")
        tab("Measurements"); save("02-measurements-light")
        tab("Statistics"); save("03-statistics-light")
        tab("History"); save("04-history-light")
        tab("Settings"); save("05-settings-light")
    }

    @Test fun darkDashboardWeight() {
        val vm = prepare(MeasurementType.WEIGHT)
        render(vm, ThemeMode.DARK)
        save("06-dashboard-dark-weight")
    }
}
