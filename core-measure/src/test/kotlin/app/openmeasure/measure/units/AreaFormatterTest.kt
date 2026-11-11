package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaFormatterTest {
    private val m = UnitSystem.METRIC
    private val i = UnitSystem.IMPERIAL

    @Test
    fun metric() {
        assertEquals("600 cm²", AreaFormatter.format(0.06, m))
        assertEquals("1 cm²", AreaFormatter.format(0.0001, m))
        assertEquals("0 cm²", AreaFormatter.format(0.0, m))
        assertEquals("9999 cm²", AreaFormatter.format(0.9999, m))
        assertEquals("1.00 m²", AreaFormatter.format(0.99996, m)) // rounds up into m²
        assertEquals("1.25 m²", AreaFormatter.format(1.245, m)) // HALF_UP on the decimal form
        assertEquals("12.34 m²", AreaFormatter.format(12.3449, m))
    }

    @Test
    fun imperial() {
        // 96 in² = 0.06193536 m².
        assertEquals("96 in²", AreaFormatter.format(0.06193536, i))
        assertEquals("143 in²", AreaFormatter.format(143.4 * 0.00064516, i))
        assertEquals("1.0 ft²", AreaFormatter.format(143.6 * 0.00064516, i))
        assertEquals("6.0 ft²", AreaFormatter.format(0.55741824, i))
        assertEquals("12.5 ft²", AreaFormatter.format(12.5 * 144 * 0.00064516, i))
    }

    @Test
    fun invalidInputs() {
        assertEquals("—", AreaFormatter.format(Double.NaN, m))
        assertEquals("—", AreaFormatter.format(-0.1, i))
        assertEquals("—", AreaFormatter.format(Double.POSITIVE_INFINITY, m))
        assertNull(AreaFormatter.speak(Double.NaN, m))
    }

    @Test
    fun spoken() {
        assertEquals("600 square centimetres", AreaFormatter.speak(0.06, m))
        assertEquals("1 square centimetre", AreaFormatter.speak(0.0001, m))
        assertEquals("1 square metre", AreaFormatter.speak(1.0, m))
        assertEquals("1.2 square metres", AreaFormatter.speak(1.2, m))
        assertEquals("96 square inches", AreaFormatter.speak(0.06193536, i))
        assertEquals("1 square inch", AreaFormatter.speak(0.00064516, i))
        assertEquals("1 square foot", AreaFormatter.speak(144 * 0.00064516, i))
        assertEquals("6 square feet", AreaFormatter.speak(0.55741824, i))
        assertEquals("12.5 square feet", AreaFormatter.speak(12.5 * 144 * 0.00064516, i))
    }
}
