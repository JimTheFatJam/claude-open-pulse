package app.openpulse

import app.openpulse.core.units.MeasurementUnit
import app.openpulse.ui.record.ChannelInput
import app.openpulse.ui.record.RecordViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordInputTest {
    @Test fun feetInchesRoundTrip() {
        val cm = RecordViewModel.parse(ChannelInput("5", "9"), MeasurementUnit.FEET_INCHES)!!
        assertEquals(175.26, cm, 1e-9)
        assertEquals(ChannelInput("5", "9"), RecordViewModel.toInput(cm, MeasurementUnit.FEET_INCHES))
        assertNull(RecordViewModel.parse(ChannelInput("5", "12"), MeasurementUnit.FEET_INCHES))
    }

    @Test fun hoursMinutes() {
        assertEquals(7.5, RecordViewModel.parse(ChannelInput("7", "30"), MeasurementUnit.HOURS)!!, 1e-9)
        assertEquals(ChannelInput("7", "30"), RecordViewModel.toInput(7.5, MeasurementUnit.HOURS))
        assertNull(RecordViewModel.parse(ChannelInput("7", "75"), MeasurementUnit.HOURS))
        assertNull(RecordViewModel.parse(ChannelInput("", ""), MeasurementUnit.HOURS))
    }

    @Test fun decimalWithUnitConversion() {
        assertEquals(80.0, RecordViewModel.parse(ChannelInput("176,37"), MeasurementUnit.POUND)!!, 0.01)
        assertEquals(ChannelInput("176.4"), RecordViewModel.toInput(80.0, MeasurementUnit.POUND))
        assertEquals(ChannelInput("120"), RecordViewModel.toInput(120.0, MeasurementUnit.MMHG))
        assertNull(RecordViewModel.parse(ChannelInput("abc"), MeasurementUnit.KILOGRAM))
    }
}
