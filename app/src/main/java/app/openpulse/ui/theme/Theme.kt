package app.openpulse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openpulse.data.prefs.ThemeMode

// Restrained, clinical palette: deep teal primary, slate neutrals.
private val Light = lightColorScheme(
    primary = Color(0xFF1B6B7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8EE),
    onPrimaryContainer = Color(0xFF00202A),
    secondary = Color(0xFF4C626A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E5EA),
    onSecondaryContainer = Color(0xFF071E25),
    tertiary = Color(0xFF5B5F8F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E0FF),
    onTertiaryContainer = Color(0xFF171A48),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F8F9),
    onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF6F8F9),
    onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDCE4E7),
    onSurfaceVariant = Color(0xFF40484B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF2F4),
    surfaceContainerHigh = Color(0xFFE8EDEF),
    surfaceContainerHighest = Color(0xFFE2E8EA),
    outline = Color(0xFF70797C),
    outlineVariant = Color(0xFFC6CED1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D1E0),
    onPrimary = Color(0xFF003641),
    primaryContainer = Color(0xFF004E5D),
    onPrimaryContainer = Color(0xFFCDE8EE),
    secondary = Color(0xFFB4CAD2),
    onSecondary = Color(0xFF1E333B),
    secondaryContainer = Color(0xFF354A52),
    onSecondaryContainer = Color(0xFFD8E5EA),
    tertiary = Color(0xFFC3C3F9),
    onTertiary = Color(0xFF2C2F5E),
    tertiaryContainer = Color(0xFF434676),
    onTertiaryContainer = Color(0xFFE1E0FF),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0E1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0E1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF40484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    surfaceContainerLowest = Color(0xFF090F11),
    surfaceContainerLow = Color(0xFF161D1F),
    surfaceContainer = Color(0xFF1A2123),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF2F3638),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF40484B),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Tabular figures for numbers that update or line up in columns. */
val NumericStyle = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")

data class ExtendedColors(val isDark: Boolean)
val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(false) }

@Composable
fun OpenPulseTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors(dark)) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
