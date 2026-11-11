package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeFormatterTest {
    private val m = UnitSystem.METRIC
    private val i = UnitSystem.IMPERIAL
    private val in3 = 0.000016387064

    @Test fun metric() {
        assertEquals("450 cm³", VolumeFormatter.format(0.00045, m))
        assertEquals("0 cm³", VolumeFormatter.format(0.0, m))
        assertEquals("999 cm³", VolumeFormatter.format(0.000999, m))
        assertEquals("0.001 m³", VolumeFormatter.format(0.0009996, m)) // rounds up into m³
        assertEquals("0.024 m³", VolumeFormatter.format(0.024, m))
        assertEquals("0.025 m³", VolumeFormatter.format(0.0245, m)) // HALF_UP
        assertEquals("1.250 m³", VolumeFormatter.format(1.25, m))
    }

    @Test fun litres() {
        assertEquals("24 L", VolumeFormatter.litres(0.024))
        assertEquals("2.5 L", VolumeFormatter.litres(0.0025))
        assertEquals("0.5 L", VolumeFormatter.litres(0.00045))
        assertEquals("10 L", VolumeFormatter.litres(0.00996)) // 9.96 -> 10.0 -> whole litres
        assertEquals("1250 L", VolumeFormatter.litres(1.25))
        assertEquals("—", VolumeFormatter.litres(-1.0))
    }

    @Test fun imperial() {
        assertEquals("300 in³", VolumeFormatter.format(300 * in3, i))
        assertEquals("1727 in³", VolumeFormatter.format(1727.4 * in3, i))
        assertEquals("1.00 ft³", VolumeFormatter.format(1727.6 * in3, i))
        assertEquals("2.50 ft³", VolumeFormatter.format(2.5 * 1728 * in3, i))
        assertEquals("35.31 ft³", VolumeFormatter.format(1.0, i))
    }

    @Test fun invalid() {
        assertEquals("—", VolumeFormatter.format(Double.NaN, m))
        assertEquals("—", VolumeFormatter.format(-0.1, i))
        assertEquals("—", VolumeFormatter.format(Double.POSITIVE_INFINITY, m))
        assertNull(VolumeFormatter.speak(Double.NaN, m))
    }

    @Test fun spoken() {
        assertEquals("450 cubic centimetres", VolumeFormatter.speak(0.00045, m))
        assertEquals("1 cubic centimetre", VolumeFormatter.speak(0.000001, m))
        assertEquals("0.024 cubic metres, 24 litres", VolumeFormatter.speak(0.024, m))
        assertEquals("1 cubic metre, 1000 litres", VolumeFormatter.speak(1.0, m))
        assertEquals("1 cubic inch", VolumeFormatter.speak(in3, i))
        assertEquals("1 cubic foot", VolumeFormatter.speak(1728 * in3, i))
        assertEquals("2.5 cubic feet", VolumeFormatter.speak(2.5 * 1728 * in3, i))
    }
}
