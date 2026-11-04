package app.openpulse.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.openpulse.core.model.Channel
import app.openpulse.core.model.MeasurementType

fun MeasurementType.icon(): ImageVector = when (this) {
    MeasurementType.BLOOD_PRESSURE -> Icons.Outlined.MonitorHeart
    MeasurementType.RESTING_HEART_RATE -> Icons.Outlined.Favorite
    MeasurementType.HEART_RATE -> Icons.Outlined.Speed
    MeasurementType.WEIGHT -> Icons.Outlined.Scale
    MeasurementType.BODY_TEMPERATURE -> Icons.Outlined.Thermostat
    MeasurementType.BLOOD_OXYGEN -> Icons.Outlined.Air
    MeasurementType.HEIGHT -> Icons.Outlined.Height
    MeasurementType.WAIST -> Icons.Outlined.Straighten
    MeasurementType.BODY_FAT -> Icons.Outlined.PieChart
    MeasurementType.BLOOD_GLUCOSE -> Icons.Outlined.Bloodtype
    MeasurementType.RESPIRATORY_RATE -> Icons.Outlined.Bolt
    MeasurementType.SLEEP -> Icons.Outlined.Bedtime
    MeasurementType.WATER -> Icons.Outlined.WaterDrop
    MeasurementType.STEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
}

private fun lightColor(type: MeasurementType): Color = when (type) {
    MeasurementType.BLOOD_PRESSURE -> Color(0xFF1B6B7A)
    MeasurementType.RESTING_HEART_RATE -> Color(0xFFA4505B)
    MeasurementType.HEART_RATE -> Color(0xFFB0645A)
    MeasurementType.WEIGHT -> Color(0xFF55619A)
    MeasurementType.BODY_TEMPERATURE -> Color(0xFFB0712E)
    MeasurementType.BLOOD_OXYGEN -> Color(0xFF2F7F6B)
    MeasurementType.HEIGHT -> Color(0xFF6A6F78)
    MeasurementType.WAIST -> Color(0xFF7A5C8E)
    MeasurementType.BODY_FAT -> Color(0xFF8A6A3D)
    MeasurementType.BLOOD_GLUCOSE -> Color(0xFF9A4A72)
    MeasurementType.RESPIRATORY_RATE -> Color(0xFF3F7896)
    MeasurementType.SLEEP -> Color(0xFF4A5A8C)
    MeasurementType.WATER -> Color(0xFF2E7DA6)
    MeasurementType.STEPS -> Color(0xFF5E7F3A)
}

private fun darkColor(type: MeasurementType): Color = when (type) {
    MeasurementType.BLOOD_PRESSURE -> Color(0xFF86D1E0)
    MeasurementType.RESTING_HEART_RATE -> Color(0xFFF0A3AC)
    MeasurementType.HEART_RATE -> Color(0xFFF2B2A6)
    MeasurementType.WEIGHT -> Color(0xFFB4BEF5)
    MeasurementType.BODY_TEMPERATURE -> Color(0xFFF2C08A)
    MeasurementType.BLOOD_OXYGEN -> Color(0xFF8FD8C2)
    MeasurementType.HEIGHT -> Color(0xFFC3C7D0)
    MeasurementType.WAIST -> Color(0xFFD9BDEA)
    MeasurementType.BODY_FAT -> Color(0xFFE3C89A)
    MeasurementType.BLOOD_GLUCOSE -> Color(0xFFF0A9CC)
    MeasurementType.RESPIRATORY_RATE -> Color(0xFF9CCDE9)
    MeasurementType.SLEEP -> Color(0xFFB7C3F2)
    MeasurementType.WATER -> Color(0xFF94CDF0)
    MeasurementType.STEPS -> Color(0xFFBEDB98)
}

@Composable
fun MeasurementType.color(): Color = if (LocalExtendedColors.current.isDark) darkColor(this) else lightColor(this)

/** Series colour for a channel; diastolic gets a distinct but related hue. */
@Composable
fun MeasurementType.channelColor(channel: Channel): Color {
    val dark = LocalExtendedColors.current.isDark
    return when {
        this == MeasurementType.BLOOD_PRESSURE && channel == Channel.SECONDARY -> if (dark) Color(0xFFC3C3F9) else Color(0xFF6A63B0)
        else -> color()
    }
}
