package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenLengthTest {

    private val metric = UnitSystem.METRIC
    private val imperial = UnitSystem.IMPERIAL

    // --- Metric ---

    @Test
    fun metric_belowOneMetre_spokenInCentimetres() {
        assertEquals("84 centimetres", SpokenLength.speak(0.84, metric))
        assertEquals("50 centimetres", SpokenLength.speak(0.495, metric))
        assertEquals("0 centimetres", SpokenLength.speak(0.0, metric))
    }

    @Test
    fun metric_singularCentimetre() {
        assertEquals("1 centimetre", SpokenLength.speak(0.01, metric))
    }

    @Test
    fun metric_fromOneMetre_spokenInMetresWithoutTrailingZeros() {
        assertEquals("1.2 metres", SpokenLength.speak(1.2, metric))
        assertEquals("1.23 metres", SpokenLength.speak(1.234, metric))
        assertEquals("12.5 metres", SpokenLength.speak(12.5, metric))
    }

    @Test
    fun metric_singularMetre() {
        assertEquals("1 metre", SpokenLength.speak(1.0, metric))
        assertEquals("1 metre", SpokenLength.speak(0.995, metric))
    }

    // --- Imperial ---

    @Test
    fun imperial_feetAndInches() {
        assertEquals("2 feet 9 inches", SpokenLength.speak(0.84, imperial))
        assertEquals("3 feet 4 inches", SpokenLength.speak(1.016, imperial))
        assertEquals("3 feet 4.5 inches", SpokenLength.speak(1.0287, imperial))
    }

    @Test
    fun imperial_singularFootAndInch() {
        assertEquals("1 foot", SpokenLength.speak(0.3048, imperial))
        assertEquals("1 foot 1 inch", SpokenLength.speak(0.3302, imperial))
        assertEquals("1 inch", SpokenLength.speak(0.0254, imperial))
    }

    @Test
    fun imperial_wholeFeetHaveNoInchPart() {
        assertEquals("3 feet", SpokenLength.speak(0.9144, imperial))
    }

    @Test
    fun imperial_belowOneFoot_spokenInInches() {
        assertEquals("7.5 inches", SpokenLength.speak(0.1905, imperial))
        assertEquals("0.5 inches", SpokenLength.speak(0.0127, imperial))
        assertEquals("0 inches", SpokenLength.speak(0.0, imperial))
    }

    // --- Invalid input ---

    @Test
    fun invalidLength_returnsNull() {
        assertNull(SpokenLength.speak(Double.NaN, metric))
        assertNull(SpokenLength.speak(Double.POSITIVE_INFINITY, imperial))
        assertNull(SpokenLength.speak(-1.0, metric))
    }

    // --- Degrees ---

    @Test
    fun degrees_pluralWithOneDecimal() {
        assertEquals("2.5 degrees", SpokenLength.speakDegrees(2.5))
        assertEquals("2 degrees", SpokenLength.speakDegrees(2.0))
        assertEquals("0 degrees", SpokenLength.speakDegrees(0.0))
    }

    @Test
    fun degrees_singularDegree() {
        assertEquals("1 degree", SpokenLength.speakDegrees(1.0))
        assertEquals("1 degree", SpokenLength.speakDegrees(0.96))
    }

    @Test
    fun degrees_signIgnoredAndInvalidIsNull() {
        assertEquals("2.5 degrees", SpokenLength.speakDegrees(-2.5))
        assertNull(SpokenLength.speakDegrees(Double.NaN))
    }
}
